package main.io;

/**
 * 游戏IO接口
 * 定义所有游戏需要的输入输出方法
 */
public interface GameIO {
    // 基本输出方法
    void print(String message);
    void println(String message);
    void printSeparator();
    void printTitle(String title);
    
    // 基本输入方法
    String readLine();
    String readInput(String prompt);
    void readInput();
    int readInt(String prompt);
    int readIntInput(String prompt, int min, int max);
    int readChoice(int maxChoice, String prompt);
    boolean confirm(String prompt);
    boolean confirmAction(String prompt);
    
    // 等待和清屏
    void waitForEnter();
    void pressEnterToContinue();
    void clearConsole();
    
    // 游戏特定方法
    void printMessage(String message);
    void printErrorMessage(String message);
    void printSuccessMessage(String message);
    void printWelcomeMessage();
    void printMainMenu();
    void printBattleDifficultyMenu();
    void printPlayerStatus(Object player); // 使用Object避免循环依赖
    void printBattleEffect(String attackerName, String skillName, String targetName, 
                          int damage, boolean isCritical, boolean isCounter);
    
    // 网络相关方法（用于检测连接状态和超时）
    boolean isConnected();
    boolean isTimedOut();
    void markTimedOut();
    void clearTimeout();
}

