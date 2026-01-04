package main;

/**
 * 程序入口类
 */
public class Main {
    private static final ConsoleIO consoleIO = new ConsoleIO();

    public static void main(String[] args) {
        try {
            showMainMenu();
        } catch (Exception e) {
            consoleIO.printErrorMessage("游戏运行时发生错误：" + e.getMessage());
            e.printStackTrace();
            consoleIO.printMessage("按回车键退出...");
            consoleIO.readInput();
        }
    }

    /**
     * 显示主菜单
     */
    private static void showMainMenu() {
        consoleIO.printTitle("武侠世界游戏启动器");
        consoleIO.println("欢迎来到武侠世界！");
        consoleIO.println("请选择游戏模式：");
        consoleIO.println("");
        consoleIO.println("1. 单机模式（单人游戏）");
        consoleIO.println("2. 联机模式（多人游戏）");
        consoleIO.println("3. 退出");
        consoleIO.println("");

        int choice = consoleIO.readChoice(3, "请选择 (1-3): ");

        switch (choice) {
            case 1:
                startSinglePlayerMode();
                break;
            case 2:
                showMultiplayerMenu();
                break;
            case 3:
                consoleIO.println("感谢游玩，再见！");
                return;
            default:
                consoleIO.printErrorMessage("无效选择");
                showMainMenu();
        }
    }

    /**
     * 启动单机模式
     */
    private static void startSinglePlayerMode() {
        consoleIO.printTitle("单机模式");
        consoleIO.println("正在启动单机模式...");
        consoleIO.println("");

        try {
            Game singlePlayerGame = new Game();
            singlePlayerGame.start();
        } catch (Exception e) {
            consoleIO.printErrorMessage("单机模式启动失败：" + e.getMessage());
            showMainMenu();
        }
    }

    /**
     * 显示多人游戏菜单
     */
    private static void showMultiplayerMenu() {
        consoleIO.printTitle("联机模式");
        consoleIO.println("请选择联机方式：");
        consoleIO.println("");
        consoleIO.println("1. 创建服务器（成为主机）");
        consoleIO.println("2. 加入服务器（连接到主机）");
        consoleIO.println("3. 返回主菜单");
        consoleIO.println("");

        int choice = consoleIO.readChoice(3, "请选择 (1-3): ");

        switch (choice) {
            case 1:
                startServerMode();
                break;
            case 2:
                startClientMode();
                break;
            case 3:
                showMainMenu();
                break;
            default:
                consoleIO.printErrorMessage("无效选择");
                showMultiplayerMenu();
        }
    }

    /**
     * 启动服务器模式
     */
    private static void startServerMode() {
        consoleIO.printTitle("创建服务器");
        consoleIO.println("请输入服务器配置：");
        consoleIO.println("");

        // 输入服务器地址
        consoleIO.print("服务器地址 (默认: 0.0.0.0，绑定所有网络接口): ");
        String host = consoleIO.readLine().trim();
        if (host.isEmpty()) {
            host = "0.0.0.0";
        }

        // 输入端口
        consoleIO.print("服务器端口 (默认: 8888): ");
        String portStr = consoleIO.readLine().trim();
        int port = 8888;
        if (!portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                consoleIO.println("无效端口，使用默认端口 8888");
            }
        }

        consoleIO.println("");
        consoleIO.println("正在启动服务器...");
        if (host.equals("0.0.0.0")) {
            consoleIO.println("服务器将绑定所有网络接口");
        } else {
            consoleIO.println("服务器将绑定到: " + host);
        }
        consoleIO.println("端口: " + port + " (如果被占用将自动选择其他端口)");
        consoleIO.println("");

        try {
            // 启动服务器
            main.server.GameServer.main(new String[]{host, String.valueOf(port)});
        } catch (Exception e) {
            consoleIO.printErrorMessage("服务器启动失败：" + e.getMessage());
            showMultiplayerMenu();
        }
    }

    /**
     * 启动客户端模式
     */
    private static void startClientMode() {
        consoleIO.printTitle("加入服务器");
        consoleIO.println("请输入服务器信息：");
        consoleIO.println("");

        // 默认服务器地址
        consoleIO.print("服务器地址 (默认: localhost): ");
        String host = consoleIO.readLine().trim();
        if (host.isEmpty()) {
            host = "localhost";
        }

        consoleIO.print("服务器端口 (默认: 8888): ");
        String portStr = consoleIO.readLine().trim();
        int port = 8888;
        if (!portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                consoleIO.println("无效端口，使用默认端口 8888");
            }
        }

        consoleIO.println("");
        consoleIO.println("正在连接到服务器 " + host + ":" + port + "...");
        consoleIO.println("");

        try {
            // 直接创建GameClient并启动，以便检测连接失败
            main.client.GameClient client = new main.client.GameClient(host, port);
            boolean connected = client.start();
            
            if (!connected) {
                // 连接失败，返回到多人游戏菜单
                consoleIO.println("");
                showMultiplayerMenu();
                return;
            }
            
            // 连接成功，客户端会一直运行直到断开
            // 当客户端断开后，返回到多人游戏菜单
            consoleIO.println("");
            consoleIO.println("已断开连接，返回菜单...");
            consoleIO.println("");
            showMultiplayerMenu();
        } catch (Exception e) {
            consoleIO.printErrorMessage("客户端启动失败：" + e.getMessage());
            consoleIO.println("");
            showMultiplayerMenu();
        }
    }
}

