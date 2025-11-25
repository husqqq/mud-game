package main;// File: Main.java

/**
 * 程序入口类
 */
public class Main {
    public static void main(String[] args) {
        try {
            Game game = new Game();
            game.start();
        } catch (Exception e) {
            new ConsoleIO().printErrorMessage("游戏运行时发生错误：" + e.getMessage());
            e.printStackTrace();
            new ConsoleIO().printMessage("按回车键退出...");
            new ConsoleIO().readInput();
        }
    }
}

