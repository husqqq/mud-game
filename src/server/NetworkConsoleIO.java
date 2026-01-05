package main.server;

import main.Player;
import main.io.GameIO;
import main.network.GameMessage;
import main.network.MessageType;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 网络控制台IO实现
 * 将IO操作代理到客户端，实现超时机制
 */
public class NetworkConsoleIO implements GameIO {
    private static final long DEFAULT_TIMEOUT_MS = 60000;  // 翻倍：60秒
    private static final long WARNING_TIMEOUT_MS = 10000;   // 翻倍：警告后10秒
    
    private final ClientHandler clientHandler;
    private final ScheduledExecutorService scheduler;
    private volatile boolean timedOut;  // 使用 volatile 保证可见性
    private volatile boolean connected;  // 使用 volatile 保证可见性
    
    public NetworkConsoleIO(ClientHandler clientHandler) {
        this.clientHandler = clientHandler;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.timedOut = false;
        this.connected = true;
    }
    
    @Override
    public void print(String message) {
        // 检查连接状态（包括ClientHandler的状态）
        if (!clientHandler.isConnected()) {
            connected = false;
            return;
        }
        
        if (connected && !timedOut) {
            try {
                clientHandler.sendMessage(new GameMessage(MessageType.DISPLAY_TEXT, message));
                // 发送后再次检查连接状态（sendMessage可能会设置connected=false）
                if (!clientHandler.isConnected()) {
                    connected = false;
                }
            } catch (Exception e) {
                // 发送消息失败，但不立即标记为断开连接（可能是临时错误）
                System.err.println("NetworkConsoleIO.print 发送消息失败: " + e.getMessage());
                // 只有在确认连接已断开时才标记为断开
                if (!clientHandler.isConnected()) {
                    connected = false;
                }
                // 如果是Socket异常，可能是连接真的断开了
                if (e instanceof java.net.SocketException || 
                    (e.getMessage() != null && (e.getMessage().contains("连接") || 
                                               e.getMessage().contains("Connection") ||
                                               e.getMessage().contains("reset") ||
                                               e.getMessage().contains("abort")))) {
                    connected = false;
                }
            }
        }
    }
    
    @Override
    public void println(String message) {
        print(message + "\n");
    }
    
    @Override
    public void printSeparator() {
        println("====================================");
    }
    
    @Override
    public void printTitle(String title) {
        printSeparator();
        println(title);
        printSeparator();
    }
    
    @Override
    public String readLine() {
        return readInput("");
    }
    
    @Override
    public String readInput(String prompt) {
        if (!connected || timedOut) {
            return "";
        }
        
        // 发送输入提示
        clientHandler.sendMessage(new GameMessage(MessageType.REQUEST_INPUT, prompt));
        
        try {
            // 超时检测已暂时注释（用于调试），使用无限等待
            String input = clientHandler.receiveInput(Long.MAX_VALUE);
            
            // 原超时检测逻辑（已注释）
            // String input = clientHandler.receiveInput(DEFAULT_TIMEOUT_MS);
            // if (input == null) {
            //     // 初次等待超时，发送提示再给一次机会
            //     handleTimeout();
            //     input = clientHandler.receiveInput(WARNING_TIMEOUT_MS);
            //     if (input == null) {
            //         markTimedOut();
            //         return "";
            //     }
            // }
            // clearTimeout();
            
            return input != null ? input.trim() : "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
    
    @Override
    public void readInput() {
        readInput("");
    }
    
    @Override
    public int readInt(String prompt) {
        while (true) {
            try {
                // 将提示作为 readInput 的参数，这样提示会通过 REQUEST_INPUT 消息一起发送
                String input = readInput(prompt);
                if (input.isEmpty() && timedOut) {
                    return 0;  // 超时返回默认值
                }
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                printErrorMessage("输入无效，请输入一个整数。");
            }
        }
    }
    
    @Override
    public int readIntInput(String prompt, int min, int max) {
        while (true) {
            try {
                // 将提示作为 readInput 的参数，这样提示会通过 REQUEST_INPUT 消息一起发送
                String input = readInput(prompt);
                
                // 检查连接状态
                if (!connected || timedOut) {
                    return min;  // 超时或断开，返回最小值
                }
                
                // 如果输入为空，提示并重新输入
                if (input == null || input.trim().isEmpty()) {
                    printErrorMessage("输入不能为空，请输入" + min + "-" + max + "之间的数字");
                    continue; // 重新输入
                }
                
                // 尝试解析为整数
                int inputValue;
                try {
                    inputValue = Integer.parseInt(input.trim());
                } catch (NumberFormatException e) {
                    printErrorMessage("请输入有效的数字（" + min + "-" + max + "）");
                    continue; // 重新输入
                }
                
                // 检查范围
                if (inputValue >= min && inputValue <= max) {
                    return inputValue;
                } else {
                    printErrorMessage("请输入" + min + "-" + max + "之间的数字");
                }
            } catch (Exception e) {
                // 其他异常，提示并重新输入
                printErrorMessage("输入错误，请重新输入（" + min + "-" + max + "）");
            }
        }
    }
    
    @Override
    public int readChoice(int maxChoice, String prompt) {
        return readIntInput(prompt, 1, maxChoice);
    }
    
    @Override
    public boolean confirm(String prompt) {
        while (true) {
            // 将提示作为 readInput 的参数，这样提示会通过 REQUEST_INPUT 消息一起发送
            String input = readInput(prompt + " (y/n): ").toLowerCase();
            if (input.isEmpty() && timedOut) {
                return false;  // 超时返回false
            }
            if (input.equals("y") || input.equals("yes")) {
                return true;
            } else if (input.equals("n") || input.equals("no")) {
                return false;
            }
            printErrorMessage("输入无效，请输入 y 或 n。");
        }
    }
    
    @Override
    public boolean confirmAction(String prompt) {
        String input = readInput(prompt);
        if (input.isEmpty() && timedOut) {
            return false;
        }
        return input.equalsIgnoreCase("y") || input.equalsIgnoreCase("yes");
    }
    
    @Override
    public void waitForEnter() {
        // 直接使用 readInput 发送提示，这样客户端会显示提示并等待输入
        readInput("\n按回车键继续...");
    }
    
    @Override
    public void pressEnterToContinue() {
        waitForEnter();
    }
    
    @Override
    public void clearConsole() {
        for (int i = 0; i < 20; i++) {
            println("");
        }
    }
    
    @Override
    public void printMessage(String message) {
        println(message);
    }
    
    @Override
    public void printErrorMessage(String message) {
        println("[错误] " + message);
    }
    
    @Override
    public void printSuccessMessage(String message) {
        println("[成功] " + message);
    }
    
    @Override
    public void printWelcomeMessage() {
        printMessage("欢迎来到武侠世界！");
        printMessage("在这个世界里，你将扮演一位武林中人，通过不断修炼和战斗，追求成为武林至尊！");
        printMessage("");
    }
    
    @Override
    public void printMainMenu() {
        printMessage("\n===== 少侠，请选择行动 =====");
        printMessage("1. 查看状态");
        printMessage("2. 去后山练功");
        printMessage("3. 下山找人切磋（NPC）");
        printMessage("4. 进入决斗池（PvP）");
        printMessage("5. 退出游戏（可选择保存）");
    }
    
    @Override
    public void printBattleDifficultyMenu() {
        printMessage("\n===== 选择战斗难度 =====");
        printMessage("1. 简单");
        printMessage("2. 普通");
        printMessage("3. 困难");
        printMessage("4. 地狱");
        printMessage("5. 返回");
    }
    
    @Override
    public void printPlayerStatus(Object playerObj) {
        main.Player player = (main.Player) playerObj;
        printMessage("\n===== 角色状态 =====");
        printMessage("角色名: " + player.getName());
        printMessage("存档名: " + player.getSaveName());
        if (player.getTitle() != null && !player.getTitle().isEmpty()) {
            printMessage("称号: " + player.getTitle());
        }
        printMessage("战力: " + player.getPower());
        printMessage("回合数: " + player.getRoundCount() + "/100");
        
        printMessage("\n===== 属性 =====");
        var stats = player.getStats();
        printMessage("生命: " + stats.getHpCurrent() + "/" + stats.getHpMax());
        printMessage("力量(STR): " + stats.getStr());
        printMessage("敏捷(AGI): " + stats.getAgi());
        printMessage("体质(CON): " + stats.getCon());
        printMessage("智力(INT): " + stats.getIntel());
        printMessage("运气(LUK): " + stats.getLuk());
        
        printMessage("\n===== 技能 =====");
        printMessage("主用流派: " + player.getMainStyle().getName());
        for (var skillType : main.SkillType.values()) {
            String skillName = skillType.getName();
            String mainIndicator = (skillType == player.getMainStyle()) ? " (主)" : "";
            String status = player.hasSkill(skillType) ? "已掌握" : "未掌握";
            printMessage(skillName + mainIndicator + ": " + status);
        }
    }
    
    @Override
    public void printBattleEffect(String attackerName, String skillName, String targetName, 
                                  int damage, boolean isCritical, boolean isCounter) {
        StringBuilder sb = new StringBuilder();
        sb.append(attackerName).append(" 使出「").append(skillName).append("」，");
        
        if (damage > 0) {
            if (isCritical) {
                sb.append("暴击！");
            }
            sb.append("命中对手，造成 ").append(damage).append(" 点伤害！");
            if (isCounter) {
                sb.append("（").append(skillName).append("克制，伤害提升）");
            }
        } else {
            sb.append(targetName).append(" 身形一晃，轻松闪过！（攻击落空）");
        }
        
        println(sb.toString());
    }
    
    /**
     * 处理超时
     */
    private void handleTimeout() {
        if (connected && clientHandler.isConnected()) {
            clientHandler.sendMessage(new GameMessage(MessageType.TIMEOUT_WARNING, 
                "输入超时！请在10秒内输入，否则将由AI接管..."));
        }
    }
    
    @Override
    public boolean isConnected() {
        return connected && clientHandler.isConnected();
    }
    
    @Override
    public boolean isTimedOut() {
        return timedOut;
    }
    
    @Override
    public void markTimedOut() {
        this.timedOut = true;
        clientHandler.sendMessage(new GameMessage(MessageType.TIMEOUT_EXCEEDED, 
            "输入超时，角色将由AI接管"));
    }
    
    @Override
    public void clearTimeout() {
        this.timedOut = false;
    }
    
    /**
     * 设置等待其他玩家的状态（等待期间不计入超时检测）
     */
    public void setWaitingForOthers(boolean waiting) {
        clientHandler.setWaitingForOthers(waiting);
    }
    
    /**
     * 关闭IO
     */
    public void close() {
        connected = false;
        scheduler.shutdown();
    }
}

