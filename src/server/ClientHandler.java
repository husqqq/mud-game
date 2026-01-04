package main.server;

import main.io.GameIO;
import main.network.GameMessage;
import main.network.MessageSerializer;
import main.network.MessageType;
import main.Player;
import main.SkillType;
import main.Stats;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 客户端处理器
 * 每个客户端连接对应一个ClientHandler实例
 */
public class ClientHandler extends Thread {
    private final Socket socket;
    private final GameServer server;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    private String username;
    private String playerName;
    private boolean authenticated;
    private boolean connected;
    private final BlockingQueue<String> inputQueue;  // 输入队列
    private long lastHeartbeatTime;  // 最后心跳时间
    private boolean waitingForOthers;  // 是否正在等待其他玩家
    private long waitingStartTime;  // 开始等待的时间
    private static final long HEARTBEAT_INTERVAL_MS = 20000;  // 翻倍：20秒
    private static final long HEARTBEAT_TIMEOUT_MS = 60000;  // 翻倍：60秒超时
    
    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.authenticated = false;
        this.connected = true;
        this.inputQueue = new LinkedBlockingQueue<>();
        this.lastHeartbeatTime = System.currentTimeMillis();
        this.waitingForOthers = false;
        this.waitingStartTime = 0;
    }
    
    @Override
    public void run() {
        try {
            dataIn = new DataInputStream(socket.getInputStream());
            dataOut = new DataOutputStream(socket.getOutputStream());
            
            // 处理认证流程
            handleAuthentication();
            
            if (authenticated && playerName != null) {
                // 创建NetworkConsoleIO并绑定到玩家
                GameIO networkIO = new NetworkConsoleIO(this);
                server.bindPlayerToUser(playerName, username, networkIO);

                // 将NetworkConsoleIO绑定到游戏
                var game = server.getGame();
                if (game != null) {
                    game.setPlayerIO(playerName, networkIO);

                    // 确保玩家存在于游戏中
                    var existingPlayer = game.getPlayerManager().getAllPlayers().stream()
                        .filter(p -> p.getName().equals(playerName))
                        .findFirst()
                        .orElse(null);

                    if (existingPlayer == null) {
                        // 创建新玩家（使用默认属性）
                        Stats defaultStats = new Stats(10, 10, 10, 10, 10); // 默认属性
                        SkillType mainSkill = SkillType.SWORD; // 默认主技能

                        Player newPlayer = new Player(playerName, username, defaultStats, mainSkill);
                        game.getPlayerManager().addPlayer(newPlayer);

                        sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
                            "角色 " + playerName + " 创建成功！"));
                    } else {
                        sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
                            "欢迎回来，" + playerName + "！"));
                    }

                    // 通知服务器检查是否需要启动游戏
                    server.startGameIfNeeded();
                }
                
                // 启动心跳检测线程
                Thread heartbeatThread = new Thread(this::heartbeatCheck);
                heartbeatThread.setDaemon(true);
                heartbeatThread.start();
                
                // 进入游戏循环（接收消息）
                while (connected && !socket.isClosed()) {
                    try {
                        if (MessageSerializer.hasData(dataIn)) {
                            GameMessage message = MessageSerializer.deserialize(dataIn);
                            handleMessage(message);
                            // 更新心跳时间
                            if (message.getType() == MessageType.HEARTBEAT || 
                                message.getType() == MessageType.USER_INPUT) {
                                lastHeartbeatTime = System.currentTimeMillis();
                            }
                        } else {
                            // 如果正在等待其他玩家，暂停超时检测
                            if (waitingForOthers) {
                                // 等待其他玩家时不计入超时检测，只更新等待开始时间（如果还没设置）
                                if (waitingStartTime == 0) {
                                    waitingStartTime = System.currentTimeMillis();
                                }
                                Thread.sleep(100);  // 短暂休眠
                                continue;  // 跳过超时检测
                            }
                            
                            // 检查心跳超时 - 根据游戏状态调整超时时间
                            long timeoutThreshold;
                            if (playerName != null) {
                                // 玩家已在游戏中，使用更长的超时时间
                                timeoutThreshold = HEARTBEAT_TIMEOUT_MS * 3; // 翻倍：180秒
                            } else if (authenticated) {
                                // 已认证但未进入游戏
                                timeoutThreshold = HEARTBEAT_TIMEOUT_MS * 2; // 翻倍：120秒
                            } else {
                                // 未认证
                                timeoutThreshold = HEARTBEAT_TIMEOUT_MS; // 翻倍：60秒
                            }

                            if (System.currentTimeMillis() - lastHeartbeatTime > timeoutThreshold) {
                                System.out.println("客户端 " + (username != null ? username : "未知") + " 心跳超时，断开连接");
                                System.out.println("认证状态: " + authenticated + ", 玩家名: " + playerName);
                                System.out.println("最后心跳时间: " + new java.util.Date(lastHeartbeatTime));
                                System.out.println("当前时间: " + new java.util.Date());
                                System.out.println("超时阈值: " + timeoutThreshold + "ms (" +
                                    (playerName != null ? "游戏中" : authenticated ? "已认证" : "未认证") + ")");
                                connected = false;
                                break;
                            }
                            Thread.sleep(100);  // 短暂休眠
                        }
                    } catch (IOException e) {
                        if (connected) {
                            System.err.println("读取客户端消息失败: " + e.getMessage());
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("客户端连接处理失败: " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    
    /**
     * 处理认证流程
     */
    private void handleAuthentication() {
        // 发送欢迎消息和登录选项
        sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
            "欢迎来到武侠世界！\n请选择：\n1. 登录\n2. 注册"));
        sendMessage(new GameMessage(MessageType.REQUEST_INPUT, "请选择 (1-2): "));

        while (!authenticated && connected) {
            try {
                if (MessageSerializer.hasData(dataIn)) {
                    GameMessage message = MessageSerializer.deserialize(dataIn);
                    
                    if (message.getType() == MessageType.USER_INPUT) {
                        String input = message.getData().trim();
                        if (input.equals("1")) {
                            // 登录
                            handleLogin();
                        } else if (input.equals("2")) {
                            // 注册
                            handleRegister();
                        } else {
                            sendMessage(new GameMessage(MessageType.ERROR, "无效选择，请输入1或2"));
                            sendMessage(new GameMessage(MessageType.REQUEST_INPUT, "请选择 (1-2): "));
                        }
                    } else if (message.getType() == MessageType.HEARTBEAT) {
                        // 心跳消息，更新心跳时间
                        lastHeartbeatTime = System.currentTimeMillis();
                        sendMessage(new GameMessage(MessageType.HEARTBEAT, "PONG"));
                    }
                    // 其他类型的消息忽略
                } else {
                    Thread.sleep(100);
                }
            } catch (IOException e) {
                System.err.println("认证流程读取消息失败: " + e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("连接")) {
                    break; // 连接问题，退出循环
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 处理登录
     */
    private void handleLogin() {
        sendMessage(new GameMessage(MessageType.REQUEST_INPUT, "请输入用户名: "));
        
        // 等待并读取用户名
        GameMessage usernameMsg = waitForUserInput();
        if (usernameMsg == null) {
            return; // 连接问题，直接返回
        }
        
        sendMessage(new GameMessage(MessageType.REQUEST_INPUT, "请输入密码: "));
        
        // 等待并读取密码
        GameMessage passwordMsg = waitForUserInput();
        if (passwordMsg == null) {
            return; // 连接问题，直接返回
        }
        
        String username = usernameMsg.getData().trim();
        String password = passwordMsg.getData();
        
        if (server.getAuthService().authenticate(username, password)) {
            this.username = username;
            authenticated = true;
            server.registerClientHandler(username, this);
            
            // 处理玩家选择
            handlePlayerSelection();
        } else {
            sendMessage(new GameMessage(MessageType.LOGIN_RESPONSE, "FAIL:用户名或密码错误"));
            // 重新显示主菜单并请求输入
            sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
                "\n欢迎来到武侠世界！\n请选择：\n1. 登录\n2. 注册"));
            sendMessage(new GameMessage(MessageType.REQUEST_INPUT, "请选择 (1-2): "));
        }
    }
    
    /**
     * 处理注册
     */
    private void handleRegister() {
        sendMessage(new GameMessage(MessageType.REQUEST_INPUT, "请输入用户名: "));
        
        // 等待并读取用户名
        GameMessage usernameMsg = waitForUserInput();
        if (usernameMsg == null) {
            return; // 连接问题，直接返回
        }
        
        sendMessage(new GameMessage(MessageType.REQUEST_INPUT, "请输入密码: "));
        
        // 等待并读取密码
        GameMessage passwordMsg = waitForUserInput();
        if (passwordMsg == null) {
            return; // 连接问题，直接返回
        }
        
        String username = usernameMsg.getData().trim();
        String password = passwordMsg.getData();
        
        if (server.getAuthService().register(username, password)) {
            sendMessage(new GameMessage(MessageType.REGISTER_RESPONSE, "SUCCESS:注册成功"));
            // 注册成功后自动登录
            this.username = username;
            authenticated = true;
            server.registerClientHandler(username, this);
            handlePlayerSelection();
        } else {
            sendMessage(new GameMessage(MessageType.REGISTER_RESPONSE, "FAIL:用户名已存在或无效"));
            // 重新显示主菜单并请求输入
            sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
                "\n欢迎来到武侠世界！\n请选择：\n1. 登录\n2. 注册"));
            sendMessage(new GameMessage(MessageType.REQUEST_INPUT, "请选择 (1-2): "));
        }
    }
    
    /**
     * 等待用户输入（带超时和错误处理）
     */
    private GameMessage waitForUserInput() {
        long startTime = System.currentTimeMillis();
        while (connected && !socket.isClosed()) {
            try {
                if (MessageSerializer.hasData(dataIn)) {
                    GameMessage message = MessageSerializer.deserialize(dataIn);
                    if (message.getType() == MessageType.USER_INPUT) {
                        return message;
                    } else {
                        // 不是用户输入，可能是其他消息，忽略并继续等待
                        System.err.println("收到非用户输入消息: " + message.getType());
                    }
                }
                // 检查超时（翻倍：60秒）
                if (System.currentTimeMillis() - startTime > 60000) {
                    System.err.println("等待用户输入超时");
                    return null;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (IOException e) {
                System.err.println("读取用户输入失败: " + e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("连接")) {
                    return null; // 连接问题
                }
                // 其他错误，继续等待
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }
    
    /**
     * 处理玩家选择
     */
    private void handlePlayerSelection() {
        try {
            // 获取用户的玩家列表
            var playerNames = server.getAuthService().getPlayerNames(username);
            
            if (playerNames.isEmpty()) {
                // 没有玩家，需要创建
                sendMessage(new GameMessage(MessageType.DISPLAY_TEXT, "您还没有角色，需要创建新角色"));
                createNewPlayer();
            } else {
                // 显示玩家列表
                StringBuilder sb = new StringBuilder("请选择角色:\n");
                for (int i = 0; i < playerNames.size(); i++) {
                    sb.append((i + 1)).append(". ").append(playerNames.get(i)).append("\n");
                }
                sb.append((playerNames.size() + 1)).append(". 创建新角色");
                sendMessage(new GameMessage(MessageType.DISPLAY_TEXT, sb.toString()));
                
                sendMessage(new GameMessage(MessageType.REQUEST_INPUT, "请选择: "));
                
                // 等待输入，带超时保护
                GameMessage choiceMsg = null;
                long startTime = System.currentTimeMillis();
                while (choiceMsg == null && connected && !socket.isClosed()) {
                    try {
                        if (MessageSerializer.hasData(dataIn)) {
                            choiceMsg = MessageSerializer.deserialize(dataIn);
                            break;
                        }
                        // 检查超时（翻倍：60秒）
                        if (System.currentTimeMillis() - startTime > 60000) {
                            System.err.println("选择角色超时");
                            break;
                        }
                        Thread.sleep(100);
                    } catch (IOException e) {
                        System.err.println("读取选择失败: " + e.getMessage());
                        if (e.getMessage() != null && e.getMessage().contains("连接")) {
                            // 连接问题，直接返回
                            return;
                        }
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                
                if (choiceMsg != null && choiceMsg.getType() == MessageType.USER_INPUT) {
                    try {
                        int choice = Integer.parseInt(choiceMsg.getData().trim());
                        if (choice >= 1 && choice <= playerNames.size()) {
                            // 选择已有玩家
                            this.playerName = playerNames.get(choice - 1);
                            // 检查玩家是否已存在于游戏中
                            var game = server.getGame();
                            if (game != null) {
                                var existingPlayer = game.getPlayerManager().getAllPlayers().stream()
                                    .filter(p -> p.getName().equals(playerName))
                                    .findFirst()
                                    .orElse(null);
                                if (existingPlayer == null) {
                                    // 玩家不存在，需要从存档加载或创建
                                    loadOrCreatePlayer();
                                }
                            }
                        } else if (choice == playerNames.size() + 1) {
                            // 创建新角色
                            createNewPlayer();
                        }
                    } catch (NumberFormatException e) {
                        createNewPlayer();
                    }
                }
            }
            
            if (playerName != null) {
                sendMessage(new GameMessage(MessageType.DISPLAY_TEXT, "进入游戏！角色: " + playerName));
            } else {
                // 如果playerName还是null，使用默认名称
                this.playerName = username + "_player";
                try {
                    server.getAuthService().bindPlayer(username, playerName);
                    var game = server.getGame();
                    if (game != null) {
                        Stats defaultStats = new Stats(10, 10, 10, 10, 10);
                        SkillType mainSkill = SkillType.SWORD;
                        Player newPlayer = new Player(playerName, username, defaultStats, mainSkill);
                        game.getPlayerManager().addPlayer(newPlayer);
                    }
                    sendMessage(new GameMessage(MessageType.DISPLAY_TEXT, "进入游戏！角色: " + playerName));
                } catch (Exception ex) {
                    System.err.println("创建默认玩家失败: " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("玩家选择处理失败: " + e.getMessage());
            e.printStackTrace();
            // 失败时创建默认玩家，但不递归调用createNewPlayer
            if (this.playerName == null) {
                this.playerName = username + "_player";
                try {
                    server.getAuthService().bindPlayer(username, playerName);
                    var game = server.getGame();
                    if (game != null) {
                        Stats defaultStats = new Stats(10, 10, 10, 10, 10);
                        SkillType mainSkill = SkillType.SWORD;
                        Player newPlayer = new Player(playerName, username, defaultStats, mainSkill);
                        game.getPlayerManager().addPlayer(newPlayer);
                    }
                    sendMessage(new GameMessage(MessageType.DISPLAY_TEXT, "进入游戏！角色: " + playerName));
                } catch (Exception ex) {
                    System.err.println("创建默认玩家失败: " + ex.getMessage());
                }
            }
        }
    }
    
    /**
     * 创建新玩家
     */
    private void createNewPlayer() {
        try {
            sendMessage(new GameMessage(MessageType.REQUEST_INPUT, "请输入角色名: "));
            
            // 等待输入，带超时保护
            GameMessage nameMsg = null;
            long startTime = System.currentTimeMillis();
            while (nameMsg == null && connected && !socket.isClosed()) {
                try {
                    if (MessageSerializer.hasData(dataIn)) {
                        nameMsg = MessageSerializer.deserialize(dataIn);
                        break;
                    }
                    // 检查超时（翻倍：60秒）
                    if (System.currentTimeMillis() - startTime > 60000) {
                        System.err.println("创建玩家超时");
                        break;
                    }
                    Thread.sleep(100);
                } catch (IOException e) {
                    System.err.println("读取角色名失败: " + e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("连接")) {
                        // 连接问题，直接返回
                        return;
                    }
                    // 其他IO错误，重试
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            
            if (nameMsg != null && nameMsg.getType() == MessageType.USER_INPUT) {
                String name = nameMsg.getData().trim();
                if (name.isEmpty()) {
                    name = username + "_player";
                }
                this.playerName = name;
                
                // 创建玩家（简化版，使用默认属性）
                sendMessage(new GameMessage(MessageType.DISPLAY_TEXT, 
                    "正在创建角色 " + playerName + "..."));
                
                // 绑定玩家到用户
                server.getAuthService().bindPlayer(username, playerName);
                
                // 将玩家添加到游戏（如果游戏已启动）
                var game = server.getGame();
                if (game != null) {
                    // 检查玩家是否已存在
                    var existingPlayer = game.getPlayerManager().getAllPlayers().stream()
                        .filter(p -> p.getName().equals(playerName))
                        .findFirst()
                        .orElse(null);
                    
                    if (existingPlayer == null) {
                        // 创建新玩家（使用默认属性）
                        Stats defaultStats = new Stats(10, 10, 10, 10, 10); // 默认属性
                        SkillType mainSkill = SkillType.SWORD; // 默认主技能

                        Player newPlayer = new Player(playerName, username, defaultStats, mainSkill);
                        game.getPlayerManager().addPlayer(newPlayer);

                        sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
                            "角色 " + playerName + " 创建成功！"));
                    } else {
                        sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
                            "角色 " + playerName + " 已存在，直接进入游戏！"));
                    }
                }
            } else {
                // 输入超时或无效，使用默认名称
                System.err.println("创建玩家输入超时或无效，使用默认名称");
                this.playerName = username + "_player";
                // 仍然创建玩家
                server.getAuthService().bindPlayer(username, playerName);
                var game = server.getGame();
                if (game != null) {
                    Stats defaultStats = new Stats(10, 10, 10, 10, 10);
                    SkillType mainSkill = SkillType.SWORD;
                    Player newPlayer = new Player(playerName, username, defaultStats, mainSkill);
                    game.getPlayerManager().addPlayer(newPlayer);
                }
            }
        } catch (Exception e) {
            System.err.println("创建玩家失败: " + e.getMessage());
            e.printStackTrace();
            // 使用默认名称，不递归调用
            if (this.playerName == null) {
                this.playerName = username + "_player";
                try {
                    server.getAuthService().bindPlayer(username, playerName);
                    var game = server.getGame();
                    if (game != null) {
                        Stats defaultStats = new Stats(10, 10, 10, 10, 10);
                        SkillType mainSkill = SkillType.SWORD;
                        Player newPlayer = new Player(playerName, username, defaultStats, mainSkill);
                        game.getPlayerManager().addPlayer(newPlayer);
                    }
                } catch (Exception ex) {
                    System.err.println("创建默认玩家也失败: " + ex.getMessage());
                }
            }
        }
    }
    
    /**
     * 加载或创建玩家
     */
    private void loadOrCreatePlayer() {
        // 这里可以从存档加载玩家，或创建新玩家
        // 暂时创建新玩家
        createNewPlayer();
    }
    
    /**
     * 处理消息
     */
    private void handleMessage(GameMessage message) {
        if (message.getType() == MessageType.USER_INPUT) {
            // 用户输入，放入队列
            inputQueue.offer(message.getData());
        } else if (message.getType() == MessageType.HEARTBEAT) {
            // 心跳包，回复
            sendMessage(new GameMessage(MessageType.HEARTBEAT, "PONG"));
            lastHeartbeatTime = System.currentTimeMillis();
        } else if (message.getType() == MessageType.DISCONNECT) {
            // 客户端主动断开
            connected = false;
        } else if (message.getType() == MessageType.RECONNECT_REQUEST) {
            // 重连请求（在认证阶段处理）
            // 这里可以处理重连逻辑
        }
    }
    
    /**
     * 心跳检测
     */
    private void heartbeatCheck() {
        while (connected && !socket.isClosed()) {
            try {
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
                // 发送心跳包
                if (connected && authenticated) {
                    sendMessage(new GameMessage(MessageType.HEARTBEAT, "PING"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 发送消息到客户端
     */
    public synchronized void sendMessage(GameMessage message) {
        try {
            if (dataOut != null && connected) {
                MessageSerializer.serialize(message, dataOut);
            }
        } catch (IOException e) {
            System.err.println("发送消息失败: " + e.getMessage());
            connected = false;
        }
    }
    
    /**
     * 接收用户输入（阻塞）
     */
    public String receiveInput() throws InterruptedException {
        return inputQueue.take();
    }
    
    /**
     * 接收用户输入（带超时）
     */
    public String receiveInput(long timeoutMs) throws InterruptedException {
        String input = inputQueue.poll();
        if (input != null) {
            return input;
        }
        // 如果队列为空，等待指定时间
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            input = inputQueue.poll();
            if (input != null) {
                return input;
            }
            Thread.sleep(100);
        }
        return null;  // 超时
    }
    
    /**
     * 设置等待其他玩家的状态
     */
    public synchronized void setWaitingForOthers(boolean waiting) {
        this.waitingForOthers = waiting;
        if (waiting) {
            // 开始等待时，记录等待开始时间，并更新最后心跳时间（暂停超时检测）
            this.waitingStartTime = System.currentTimeMillis();
            // 更新最后心跳时间，这样等待期间不会超时
            this.lastHeartbeatTime = System.currentTimeMillis();
        } else {
            // 结束等待时，恢复最后心跳时间（从等待开始时间计算）
            if (waitingStartTime > 0) {
                // 将等待期间的时间不计入超时检测
                long waitingDuration = System.currentTimeMillis() - waitingStartTime;
                // 不更新lastHeartbeatTime，保持原来的值，这样等待期间的时间不计入超时
                this.waitingStartTime = 0;
            }
        }
    }
    
    /**
     * 检查是否正在等待其他玩家
     */
    public synchronized boolean isWaitingForOthers() {
        return waitingForOthers;
    }
    
    /**
     * 清理资源
     */
    private void cleanup() {
        connected = false;
        if (username != null) {
            server.unregisterClientHandler(username);
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("关闭客户端连接失败: " + e.getMessage());
        }
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public boolean isConnected() {
        return connected;
    }
}

