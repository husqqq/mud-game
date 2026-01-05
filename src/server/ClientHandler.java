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
import java.util.concurrent.TimeUnit;

/**
 * 客户端处理器
 * 每个客户端连接对应一个ClientHandler实例
 */
public class ClientHandler extends Thread {
    private final Socket socket;
    private final GameServer server;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    private volatile String username;  // 使用 volatile 保证可见性
    private volatile String playerName;  // 使用 volatile 保证可见性
    private volatile boolean authenticated;  // 使用 volatile 保证可见性
    private volatile boolean connected;  // 使用 volatile 保证可见性
    private final BlockingQueue<String> inputQueue;  // 输入队列，已经是线程安全的
    private volatile long lastHeartbeatTime;  // 最后心跳时间，使用 volatile 保证可见性
    private volatile boolean waitingForOthers;  // 是否正在等待其他玩家，使用 volatile 保证可见性
    private volatile long waitingStartTime;  // 开始等待的时间，使用 volatile 保证可见性
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
            socket.setSoTimeout((int) HEARTBEAT_TIMEOUT_MS);
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
                        Player newPlayer = createPlayerWithCustomization(playerName);
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
                                    lastHeartbeatTime = waitingStartTime; // 等待时不计算超时
                                }
                                Thread.sleep(100);  // 短暂休眠
                                continue;  // 跳过超时检测
                            }
                            
                            checkHeartbeatTimeout();
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
                    } else if (message.getType() == MessageType.RECONNECT_REQUEST) {
                        // 重连请求，重置认证状态并重新发送欢迎消息和登录选项
                        authenticated = false;
                        playerName = null;
                        // 只发送一次欢迎消息，避免重复
                        sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
                            "\n已重新连接到服务器\n注意：重连后需要重新登录\n\n欢迎来到武侠世界！\n请选择：\n1. 登录\n2. 注册"));
                        sendMessage(new GameMessage(MessageType.REQUEST_INPUT, "请选择 (1-2): "));
                    }
                    // 其他类型的消息忽略
                } else {
                    Thread.sleep(100);
                }
            } catch (IOException e) {
                String errorMsg = e.getMessage();
                System.err.println("认证流程读取消息失败: " + errorMsg);
                
                // 如果是流不同步或数据错误，断开连接
                if (errorMsg != null && (errorMsg.contains("无效的消息类型") || 
                                         errorMsg.contains("数据不足") || 
                                         errorMsg.contains("无效的数据长度") ||
                                         errorMsg.contains("连接"))) {
                    System.err.println("流不同步或连接问题，断开连接");
                    connected = false;
                    break; // 连接问题，退出循环
                }
                
                // 其他错误，短暂等待后继续
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
                // 如果playerName还是null，使用用户名作为默认名称
                this.playerName = username; // 使用用户名，而不是username + "_player"
                try {
                    server.getAuthService().bindPlayer(username, playerName);
                    var game = server.getGame();
                    if (game != null) {
                        Player newPlayer = createPlayerWithCustomization(playerName);
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
                this.playerName = username; // 使用用户名，而不是username + "_player"
                try {
                    server.getAuthService().bindPlayer(username, playerName);
                    var game = server.getGame();
                    if (game != null) {
                        Player newPlayer = createPlayerWithCustomization(playerName);
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
            
            // 使用waitForUserInput确保正确读取用户输入
            GameMessage nameMsg = waitForUserInput();
            
            if (nameMsg != null) {
                String name = nameMsg.getData().trim();
                // 如果输入为空，使用用户名作为角色名，而不是username + "_player"
                if (name.isEmpty()) {
                    name = username;
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
                        Player newPlayer = createPlayerWithCustomization(playerName);
                        game.getPlayerManager().addPlayer(newPlayer);

                        sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
                            "角色 " + playerName + " 创建成功！"));
                    } else {
                        sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
                            "角色 " + playerName + " 已存在，直接进入游戏！"));
                    }
                }
            } else {
                // 输入超时或无效，使用用户名作为默认名称
                System.err.println("创建玩家输入超时或无效，使用用户名作为角色名");
                this.playerName = username; // 使用用户名，而不是username + "_player"
                // 仍然创建玩家
                server.getAuthService().bindPlayer(username, playerName);
                var game = server.getGame();
                if (game != null) {
                    Player newPlayer = createPlayerWithCustomization(playerName);
                    game.getPlayerManager().addPlayer(newPlayer);
                }
                sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
                    "角色 " + playerName + " 创建成功！"));
            }
        } catch (Exception e) {
            System.err.println("创建玩家失败: " + e.getMessage());
            e.printStackTrace();
            // 使用用户名作为默认名称
            if (this.playerName == null) {
                this.playerName = username; // 使用用户名，而不是username + "_player"
                try {
                    server.getAuthService().bindPlayer(username, playerName);
                    var game = server.getGame();
                    if (game != null) {
                    Player newPlayer = createPlayerWithCustomization(playerName);
                    game.getPlayerManager().addPlayer(newPlayer);
                    }
                    sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
                        "角色 " + playerName + " 创建成功！"));
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
    * 创建玩家（带初始加点与主流派选择，支持回车默认）
    */
    private Player createPlayerWithCustomization(String playerName) {
        Stats stats = allocateAttributesForNewPlayer();
        SkillType mainStyle = selectInitialSkillWithDefault();
        Player newPlayer = new Player(playerName, username != null ? username : playerName, stats, mainStyle);

        sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
            "角色名：" + newPlayer.getName() + "\n主用流派：" + mainStyle.getName() +
            "\n初始战力：" + newPlayer.getPower()));
        return newPlayer;
    }

    /**
    * 分配属性点，回车默认平均分配剩余点数
    */
    private Stats allocateAttributesForNewPlayer() {
        int baseValue = 10;
        int remaining = 5;
        int str = baseValue, agi = baseValue, con = baseValue, intel = baseValue, luk = baseValue;

        sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
            "\n你有5点属性可以自由分配（基础均为10点）。\n属性包括：力量(STR)、敏捷(AGI)、体质(CON)、智力(INT)、运气(LUK)\n输入属性名分配点数，回车直接平均分配剩余点数。"));
        showAttributeStatus(str, agi, con, intel, luk, remaining);

        while (remaining > 0 && connected) {
            sendMessage(new GameMessage(MessageType.REQUEST_INPUT,
                "请输入要加点的属性名 (str/agi/con/int/luk)，回车平均分配剩余点数: "));
            GameMessage attrMsg = waitForUserInput();
            if (attrMsg == null) {
                break; // 连接问题，使用当前值
            }
            String attr = attrMsg.getData().trim().toLowerCase();
            if (attr.isEmpty()) {
                // 平均分配剩余点数
                int share = remaining / 5;
                int extra = remaining % 5;
                str += share;
                agi += share;
                con += share;
                intel += share;
                luk += share;
                int[] extras = {0, 1, 2, 3, 4};
                for (int i = 0; i < extra; i++) {
                    switch (extras[i]) {
                        case 0 -> str++;
                        case 1 -> agi++;
                        case 2 -> con++;
                        case 3 -> intel++;
                        case 4 -> luk++;
                        default -> {
                        }
                    }
                }
                remaining = 0;
                break;
            }

            sendMessage(new GameMessage(MessageType.REQUEST_INPUT,
                "要增加多少点？(1-" + remaining + "，回车默认1): "));
            GameMessage pointsMsg = waitForUserInput();
            int pointsToAdd = 1;
            if (pointsMsg != null) {
                String ptsStr = pointsMsg.getData().trim();
                if (!ptsStr.isEmpty()) {
                    try {
                        pointsToAdd = Integer.parseInt(ptsStr);
                    } catch (NumberFormatException ignored) {
                        pointsToAdd = 1;
                    }
                }
            }
            if (pointsToAdd < 1) pointsToAdd = 1;
            if (pointsToAdd > remaining) pointsToAdd = remaining;

            boolean validAttr = true;
            switch (attr) {
                case "str" -> str += pointsToAdd;
                case "agi" -> agi += pointsToAdd;
                case "con" -> con += pointsToAdd;
                case "int", "intel" -> intel += pointsToAdd;
                case "luk" -> luk += pointsToAdd;
                default -> validAttr = false;
            }

            if (!validAttr) {
                sendMessage(new GameMessage(MessageType.ERROR, "无效的属性名，请输入 str/agi/con/int/luk 之一。"));
                continue;
            }

            remaining -= pointsToAdd;
            showAttributeStatus(str, agi, con, intel, luk, remaining);
        }

        return new Stats(str, agi, con, intel, luk);
    }

    /**
    * 选择初始主流派，回车默认剑法
    */
    private SkillType selectInitialSkillWithDefault() {
        sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
            "\n请选择主用流派：\n1. 刀法 (SABER)\n2. 剑法 (SWORD)\n3. 拳法 (FIST)\n回车默认选择剑法"));
        sendMessage(new GameMessage(MessageType.REQUEST_INPUT, "请选择 (1-3，回车默认2): "));
        GameMessage choiceMsg = waitForUserInput();
        if (choiceMsg == null) {
            return SkillType.SWORD;
        }
        String data = choiceMsg.getData().trim();
        if (data.isEmpty()) {
            return SkillType.SWORD;
        }
        try {
            int choice = Integer.parseInt(data);
            if (choice >= 1 && choice <= 3) {
                return SkillType.values()[choice - 1];
            }
        } catch (NumberFormatException ignored) {
        }
        return SkillType.SWORD;
    }

    private void showAttributeStatus(int str, int agi, int con, int intel, int luk, int remaining) {
        sendMessage(new GameMessage(MessageType.DISPLAY_TEXT,
            "STR = " + str + " (力量)\n" +
            "AGI = " + agi + " (敏捷)\n" +
            "CON = " + con + " (体质)\n" +
            "INT = " + intel + " (智力)\n" +
            "LUK = " + luk + " (运气)\n" +
            "剩余分配点数：" + remaining));
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
            // 检查连接状态和socket状态
            if (dataOut != null && connected && socket != null && !socket.isClosed()) {
                // 检查socket是否真的连接
                if (socket.isConnected() && !socket.isOutputShutdown()) {
                    MessageSerializer.serialize(message, dataOut);
                } else {
                    // Socket已关闭或输出流已关闭
                    connected = false;
                    System.err.println("连接已关闭，无法发送消息");
                }
            } else if (!connected) {
                // 连接已断开，不发送消息
                return;
            }
        } catch (java.net.SocketException e) {
            // Socket异常，通常是连接被关闭
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("连接") || errorMsg.contains("Connection") || 
                                    errorMsg.contains("reset") || errorMsg.contains("abort"))) {
                // 连接被关闭，这是正常的（客户端断开连接）
                if (connected) {
                    System.out.println("客户端 " + (username != null ? username : "未知") + " 连接已关闭");
                }
            } else {
                System.err.println("发送消息失败 (Socket异常): " + errorMsg);
            }
            connected = false;
        } catch (IOException e) {
            // 其他IO异常
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("连接") || errorMsg.contains("Connection") || 
                                    errorMsg.contains("reset") || errorMsg.contains("abort") ||
                                    errorMsg.contains("中止"))) {
                // 连接被关闭，这是正常的
                if (connected) {
                    System.out.println("客户端 " + (username != null ? username : "未知") + " 连接已关闭");
                }
            } else {
                System.err.println("发送消息失败: " + errorMsg);
            }
            connected = false;
        } catch (Exception e) {
            // 其他异常
            System.err.println("发送消息时发生未知错误: " + e.getMessage());
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
        if (timeoutMs == Long.MAX_VALUE) {
            return inputQueue.take();
        }
        if (timeoutMs <= 0) {
            return inputQueue.poll();
        }
        return inputQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
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
                // 重置心跳时间，避免刚结束等待就被误判为超时
                this.lastHeartbeatTime = System.currentTimeMillis();
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

    /**
     * 根据心跳检测连接是否超时
     */
    private void checkHeartbeatTimeout() {
        long now = System.currentTimeMillis();
        long timeoutThreshold;
        if (playerName != null) {
            timeoutThreshold = HEARTBEAT_TIMEOUT_MS * 3; // 游戏中给更长时间
        } else if (authenticated) {
            timeoutThreshold = HEARTBEAT_TIMEOUT_MS * 2; // 已认证但未进入游戏
        } else {
            timeoutThreshold = HEARTBEAT_TIMEOUT_MS;
        }

        if (now - lastHeartbeatTime > timeoutThreshold) {
            System.out.println("客户端 " + (username != null ? username : "未知") + " 心跳超时，断开连接");
            connected = false;
        }
    }
}

