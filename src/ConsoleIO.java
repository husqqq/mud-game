package main;

import java.util.Scanner;
import main.io.GameIO;

/**
 * 控制台输入输出工具类
 * 实现GameIO接口
 */
public class ConsoleIO implements GameIO {
    private static final Scanner scanner = new Scanner(System.in);
    
    @Override
    public void print(String message) {
        System.out.print(message);
    }
    
    @Override
    public void println(String message) {
        System.out.println(message);
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
        return scanner.nextLine().trim();
    }
    
    @Override
    public String readInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }

    @Override
    public void readInput() {
        scanner.nextLine();
    }

    @Override
    public int readInt(String prompt) {
        while (true) {
            try {
                println(prompt);
                String input = readLine();
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                println("输入无效，请输入一个整数。");
            }
        }
    }
    
    @Override
    public int readIntInput(String prompt, int min, int max) {
        while (true) {
            try {
                System.out.print(prompt);
                int input = Integer.parseInt(scanner.nextLine());
                if (input >= min && input <= max) {
                    return input;
                }
                printErrorMessage("请输入" + min + "-" + max + "之间的数字");
            } catch (NumberFormatException e) {
                printErrorMessage("请输入有效的数字");
            }
        }
    }

    @Override
    public int readChoice(int maxChoice, String prompt) {
        while (true) {
            int choice = readInt(prompt);
            if (choice >= 1 && choice <= maxChoice) {
                return choice;
            }
            println("选择无效，请输入1-" + maxChoice + "之间的数字。");
        }
    }
    
    @Override
    public boolean confirm(String prompt) {
        while (true) {
            println(prompt + " (y/n)");
            String input = readLine().toLowerCase();
            if (input.equals("y") || input.equals("yes")) {
                return true;
            } else if (input.equals("n") || input.equals("no")) {
                return false;
            }
            println("输入无效，请输入 y 或 n。");
        }
    }
    
    @Override
    public boolean confirmAction(String prompt) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim().toLowerCase();
        return input.isEmpty() || input.equals("y") || input.equals("yes");
    }

    @Override
    public void waitForEnter() {
        println("\n按回车键继续...");
        readLine();
    }
    
    @Override
    public void pressEnterToContinue() {
        printMessage("\n按回车键继续...");
        readInput();
    }

    @Override
    public void clearConsole() {
        for (int i = 0; i < 20; i++) {
            println("");
        }
    }
    
    @Override
    public void printMessage(String message) {
        System.out.println(message);
    }

    @Override
    public void printErrorMessage(String message) {
        System.err.println("[错误] " + message);
    }

    @Override
    public void printSuccessMessage(String message) {
        System.out.println("[成功] " + message);
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
        Player player = (Player) playerObj;
        printMessage("\n===== 角色状态 =====");
        printMessage("角色名: " + player.getName());
        printMessage("存档名: " + player.getSaveName());
        if (player.getTitle() != null && !player.getTitle().isEmpty()) {
            printMessage("称号: " + player.getTitle());
        }
        printMessage("战力: " + player.getPower());
        printMessage("回合数: " + player.getRoundCount() + "/100");
        
        printMessage("\n===== 属性 =====");
        Stats stats = player.getStats();
        printMessage("生命: " + stats.getHpCurrent() + "/" + stats.getHpMax());
        printMessage("防御: " + stats.getDef());
        printMessage("力量(STR): " + stats.getStr());
        printMessage("敏捷(AGI): " + stats.getAgi());
        printMessage("体质(CON): " + stats.getCon());
        printMessage("智力(INT): " + stats.getIntel());
        printMessage("运气(LUK): " + stats.getLuk());
        
        printMessage("\n===== 技能 =====");
        printMessage("主用流派: " + player.getMainStyle().getName());
        // 显示所有技能的中文名称和掌握状态
        for (SkillType skillType : SkillType.values()) {
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

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isTimedOut() {
        return false;
    }

    @Override
    public void markTimedOut() {
        // 本地IO不会超时
    }

    @Override
    public void clearTimeout() {
        // 本地IO不会超时
    }
}