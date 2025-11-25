package main;// File: ConsoleIO.java

import java.util.Scanner;
import java.util.Map;

/**
 * 控制台输入输出工具类
 */
public class ConsoleIO {
    private static final Scanner scanner = new Scanner(System.in);
    
    /**
     * 打印消息
     */
    public static void print(String message) {
        System.out.print(message);
    }
    
    /**
     * 打印消息并换行
     */
    public static void println(String message) {
        System.out.println(message);
    }
    
    /**
     * 打印分隔线
     */
    public static void printSeparator() {
        println("====================================");
    }
    
    /**
     * 打印标题
     */
    public static void printTitle(String title) {
        printSeparator();
        println(title);
        printSeparator();
    }
    
    /**
     * 读取用户输入
     */
    public static String readLine() {
        return scanner.nextLine().trim();
    }
    
    /**
     * 读取用户输入的整数
     */
    public static int readInt(String prompt) {
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
    
    /**
     * 读取用户输入的选择（1-n之间的整数）
     */
    public static int readChoice(int maxChoice, String prompt) {
        while (true) {
            int choice = readInt(prompt);
            if (choice >= 1 && choice <= maxChoice) {
                return choice;
            }
            println("选择无效，请输入1-" + maxChoice + "之间的数字。");
        }
    }
    
    /**
     * 读取用户的确认（y/n）
     */
    public static boolean confirm(String prompt) {
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
    
    /**
     * 等待用户按回车键继续
     */
    public static void waitForEnter() {
        println("\n按回车键继续...");
        readLine();
    }
    
    /**
     * 清空控制台（模拟）
     */
    public static void clearConsole() {
        for (int i = 0; i < 20; i++) {
            println("");
        }
    }
    
    /**
     * 打印战斗效果文本
     */
    public static void printBattleEffect(String attackerName, String skillName, String targetName, int damage, boolean isCritical, boolean isCounter) {
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
    
    // 以下是Game类引用的额外交互方法
    public void printMessage(String message) {
        System.out.println(message);
    }

    public void printErrorMessage(String message) {
        System.err.println("[错误] " + message);
    }

    public void printSuccessMessage(String message) {
        System.out.println("[成功] " + message);
    }

    public String readInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }

    public void readInput() {
        scanner.nextLine();
    }

    public void printWelcomeMessage() {
        printMessage("欢迎来到武侠世界！");
        printMessage("在这个世界里，你将扮演一位武林中人，通过不断修炼和战斗，追求成为武林至尊！");
        printMessage("");
    }

    public void printMainMenu() {
        printMessage("\n===== 少侠，请选择行动 =====");
        printMessage("1. 查看状态");
        printMessage("2. 去后山练功");
        printMessage("3. 下山找人切磋");
        printMessage("4. 一键扫荡练功");
        printMessage("5. 保存并退出");
        printMessage("6. 直接退出");
    }

    public void printBattleDifficultyMenu() {
        printMessage("\n===== 选择战斗难度 =====");
        printMessage("1. 简单");
        printMessage("2. 普通");
        printMessage("3. 困难");
        printMessage("4. 地狱");
        printMessage("5. 返回");
    }

    public void printPlayerStatus(Player player) {
        printMessage("\n===== 角色状态 =====");
        printMessage("角色名: " + player.getName());
        printMessage("用户名: " + player.getUsername());
        if (player.getTitle() != null && !player.getTitle().isEmpty()) {
            printMessage("称号: " + player.getTitle());
        }
        printMessage("战力: " + player.getPower());
        printMessage("回合数: " + player.getRoundCount() + "/100");
        
        printMessage("\n===== 属性 =====");
        Stats stats = player.getStats();
        printMessage("生命: " + stats.getHpCurrent() + "/" + stats.getHpMax());
        printMessage("力量(STR): " + stats.getStr());
        printMessage("敏捷(AGI): " + stats.getAgi());
        printMessage("体质(CON): " + stats.getCon());
        printMessage("智力(INT): " + stats.getIntel());
        printMessage("运气(LUK): " + stats.getLuk());
        
        printMessage("\n===== 技能 =====");
        printMessage("主用流派: " + player.getMainStyle().getName());
        // 简化技能显示，只显示流派名称，不再显示等级和经验
        for (Map.Entry<String, Integer> skillEntry : player.getSkills().entrySet()) {
            String skillName = skillEntry.getKey();
            printMessage(skillName + ": 已掌握");
        }
    }
    
    // 添加Game类需要的方法
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

    public boolean confirmAction(String prompt) {
        String input = readInput(prompt);
        return input.equalsIgnoreCase("y") || input.equalsIgnoreCase("yes");
    }

    public void pressEnterToContinue() {
        printMessage("\n按回车键继续...");
        readInput();
    }
}