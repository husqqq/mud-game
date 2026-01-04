package main.server;

import main.MultiPlayerGame;
import main.MultiPlayerManager;
import main.MultiPlayerSaveData;
import main.SaveLoadService;
import main.io.GameIO;
import main.network.GameMessage;
import main.network.MessageType;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏服务器
 * 处理客户端连接，管理游戏状态
 */
public class GameServer {
    private static final int DEFAULT_PORT = 8888;

    private int port;
    private int expectedPlayerCount;  // 期望的玩家数量
    private final UserAuthService authService;
    private final SaveLoadService saveLoadService;
    private final Map<String, ClientHandler> clientHandlers;  // 用户名 -> ClientHandler
    private final Map<String, String> playerToUser;  // 玩家名 -> 用户名
    private final Map<String, GameIO> playerIOs;  // 玩家名 -> NetworkConsoleIO
    private MultiPlayerGame game;
    private ServerSocket serverSocket;
    private boolean running;
    
    public GameServer(int port, int expectedPlayerCount) {
        this.port = port;
        this.expectedPlayerCount = expectedPlayerCount;
        this.authService = new UserAuthService();
        this.saveLoadService = new SaveLoadService();
        this.clientHandlers = new ConcurrentHashMap<>();
        this.playerToUser = new ConcurrentHashMap<>();
        this.playerIOs = new ConcurrentHashMap<>();
        this.running = false;
    }

    public GameServer(int port) {
        this.port = port;
        this.expectedPlayerCount = 2; // 默认2个玩家
        this.authService = new UserAuthService();
        this.saveLoadService = new SaveLoadService();
        this.clientHandlers = new ConcurrentHashMap<>();
        this.playerToUser = new ConcurrentHashMap<>();
        this.playerIOs = new ConcurrentHashMap<>();
        this.running = false;
    }

    public GameServer() {
        this(DEFAULT_PORT);
    }

    /**
     * 寻找可用端口
     * @param preferredPort 首选端口
     * @return 可用端口
     */
    private int findAvailablePort(int preferredPort) {
        // 先尝试首选端口
        if (isPortAvailable(preferredPort)) {
            return preferredPort;
        }

        System.out.println("端口 " + preferredPort + " 已被占用，尝试寻找其他可用端口...");

        // 如果首选端口不可用，尝试其他端口
        for (int port = preferredPort + 1; port <= preferredPort + 100; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }

        // 如果还是找不到，抛出异常
        throw new RuntimeException("无法找到可用端口 (尝试了 " + preferredPort + " 到 " + (preferredPort + 100) + ")");
    }

    /**
     * 检查端口是否可用
     * @param port 端口号
     * @return 是否可用
     */
    private boolean isPortAvailable(int port) {
        try {
            ServerSocket testSocket = new ServerSocket(port);
            testSocket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 启动服务器
     */
    public void start() {
        try {
            // 尝试绑定端口，如果失败则自动寻找可用端口
            int actualPort = findAvailablePort(port);
            if (actualPort != port) {
                System.out.println("端口 " + port + " 已被占用，使用端口 " + actualPort);
                this.port = actualPort;
            }

            serverSocket = new ServerSocket(actualPort);
            running = true;
            System.out.println("====================================");
            System.out.println("        武侠世界服务器已启动");
            System.out.println("====================================");
            System.out.println("服务器地址: localhost (本机)");
            System.out.println("监听端口: " + actualPort);
            System.out.println("连接方式: 其他玩家选择'加入服务器'");
            System.out.println("          输入地址: localhost, 端口: " + actualPort);
            System.out.println("====================================");
            System.out.println("等待玩家连接...");

            // 初始化游戏（但不立即启动）
            initializeGame();

            // 接受客户端连接
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("新客户端连接: " + clientSocket.getRemoteSocketAddress());

                    // 为每个客户端创建处理线程
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    handler.start();
                } catch (IOException e) {
                    if (running) {
                        System.err.println("接受客户端连接失败: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 停止服务器
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("关闭服务器失败: " + e.getMessage());
        }
    }
    
    /**
     * 询问并设置期望的玩家数量
     */
    private void setupExpectedPlayerCount() {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        while (true) {
            try {
                System.out.print("请输入期望的玩家数量 (2-8): ");
                int count = scanner.nextInt();
                if (count >= 2 && count <= 8) {
                    this.expectedPlayerCount = count;
                    System.out.println("设置期望玩家数量为: " + count);
                    break;
                } else {
                    System.out.println("玩家数量必须在2-8之间，请重新输入。");
                }
            } catch (java.util.InputMismatchException e) {
                System.out.println("输入无效，请输入一个数字。");
                scanner.nextLine(); // 清除无效输入
            }
        }
    }

    /**
     * 初始化游戏
     */
    private void initializeGame() {
        // 首先询问期望的玩家数量
        setupExpectedPlayerCount();

        // 创建MultiPlayerGame实例（网络模式，不需要加载存档）
        this.game = new MultiPlayerGame();
        System.out.println("游戏已初始化，等待 " + expectedPlayerCount + " 个玩家连接...");
    }
    
    /**
     * 注册客户端处理器
     */
    public void registerClientHandler(String username, ClientHandler handler) {
        clientHandlers.put(username, handler);
    }
    
    /**
     * 注销客户端处理器
     */
    public void unregisterClientHandler(String username) {
        ClientHandler handler = clientHandlers.remove(username);
        if (handler != null) {
            // 查找该用户绑定的玩家
            String playerName = handler.getPlayerName();
            if (playerName != null) {
                playerToUser.remove(playerName);
                playerIOs.remove(playerName);

                // 通知游戏将玩家标记为AI接管
                if (game != null) {
                    game.setPlayerAiControlled(playerName, true);
                    System.out.println("玩家 " + playerName + " 断线，已标记为AI接管");
                }
            }
        }
    }
    
    /**
     * 绑定玩家到用户
     */
    public void bindPlayerToUser(String playerName, String username, GameIO io) {
        playerToUser.put(playerName, username);
        playerIOs.put(playerName, io);
    }
    
    /**
     * 获取用户认证服务
     */
    public UserAuthService getAuthService() {
        return authService;
    }
    
    /**
     * 获取存档服务
     */
    public SaveLoadService getSaveLoadService() {
        return saveLoadService;
    }
    
    /**
     * 获取玩家IO
     */
    public GameIO getPlayerIO(String playerName) {
        return playerIOs.get(playerName);
    }
    
    /**
     * 设置游戏实例
     */
    public void setGame(MultiPlayerGame game) {
        this.game = game;
    }

    /**
     * 获取游戏实例
     */
    public MultiPlayerGame getGame() {
        return game;
    }

    /**
     * 启动游戏（当有玩家加入时调用）
     */
    public synchronized void startGameIfNeeded() {
        if (game != null && !game.isGameStarted()) {
            int currentPlayerCount = game.getPlayerManager().getPlayerCount();
            System.out.println("当前玩家数量: " + currentPlayerCount + "/" + expectedPlayerCount);

            if (currentPlayerCount >= expectedPlayerCount) {
                System.out.println("所有玩家已连接，开始游戏...");
                // 在新线程中启动游戏，避免阻塞服务器主循环
                Thread gameThread = new Thread(() -> {
                    game.startNetworkMode();
                });
                gameThread.setDaemon(true);
                gameThread.start();
            } else {
                System.out.println("等待更多玩家连接... (还需要 " + (expectedPlayerCount - currentPlayerCount) + " 个玩家)");
            }
        }
    }
    
    /**
     * 检查玩家是否在线
     */
    public boolean isPlayerOnline(String playerName) {
        String username = playerToUser.get(playerName);
        return username != null && clientHandlers.containsKey(username);
    }
    
    /**
     * 主方法（用于测试）
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("无效的端口号，使用默认端口: " + DEFAULT_PORT);
            }
        }
        
        GameServer server = new GameServer(port);
        server.start();
    }
}

