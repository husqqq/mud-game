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
    private boolean timedOut;
    private boolean connected;
    
    public NetworkConsoleIO(ClientHandler clientHandler) {
        this.clientHandler = clientHandler;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.timedOut = false;
        this.connected = true;
    }
    
    @Override
    public void print(String message) {
        if (connected && !timedOut) {
            clientHandler.sendMessage(new GameMessage(MessageType.DISPLAY_TEXT, message));
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
        
        // 等待输入（带超时）
        try {
            String input = clientHandler.receiveInput(DEFAULT_TIMEOUT_MS);
            if (input == null) {
                // 超时，发送警告
                handleTimeout();
                // 再等待一段时间
                input = clientHandler.receiveInput(WARNING_TIMEOUT_MS);
                if (input == null) {
                    // 仍然超时，标记为超时
                    markTimedOut();
                    return "";
                }
            }
            clearTimeout();
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
                if (input.isEmpty() && timedOut) {
                    return min;  // 超时返回最小值
                }
                int inputValue = Integer.parseInt(input);
                if (inputValue >= min && inputValue <= max) {
                    return inputValue;
                }
                printErrorMessage("请输入" + min + "-" + max + "之间的数字");
            } catch (NumberFormatException e) {
                printErrorMessage("请输入有效的数字");
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
        printMessage("5. 保存并退出");
        printMessage("6. 退出游戏");
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

