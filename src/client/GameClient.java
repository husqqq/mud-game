package main.client;

import main.network.GameMessage;
import main.network.MessageSerializer;
import main.network.MessageType;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 游戏客户端
 * 处理与服务器的连接和通信
 */
public class GameClient {
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final long RECONNECT_DELAY_MS = 10000;  // 翻倍：10秒
    
    private final String serverHost;
    private final int serverPort;
    private Socket socket;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    private final AtomicBoolean connected;
    private final AtomicBoolean running;
    private Thread receiveThread;
    private Thread heartbeatThread;
    private final Scanner scanner;
    
    public GameClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.connected = new AtomicBoolean(false);
        this.running = new AtomicBoolean(false);
        this.scanner = new Scanner(System.in);
    }
    
    /**
     * 连接到服务器
     */
    public boolean connect() {
        // 先清理旧的连接
        cleanupConnection();
        
        int attempts = 0;
        while (attempts < MAX_RECONNECT_ATTEMPTS && !connected.get()) {
            try {
                socket = new Socket(serverHost, serverPort);
                // 设置socket选项，避免旧数据残留
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(60000); // 翻倍：60秒读取超时
                
                dataIn = new DataInputStream(socket.getInputStream());
                dataOut = new DataOutputStream(socket.getOutputStream());
                connected.set(true);
                System.out.println("已连接到服务器: " + serverHost + ":" + serverPort);
                return true;
            } catch (IOException e) {
                attempts++;
                if (attempts < MAX_RECONNECT_ATTEMPTS) {
                    System.out.println("连接失败，" + RECONNECT_DELAY_MS / 1000 + "秒后重试... (" + attempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    System.err.println("无法连接到服务器: " + e.getMessage());
                }
            }
        }
        return false;
    }
    
    /**
     * 启动客户端
     * @return true 如果成功连接，false 如果连接失败
     */
    public boolean start() {
        if (!connect()) {
            return false;
        }
        
        running.set(true);

        // 启动消息接收线程
        receiveThread = new Thread(this::receiveMessages);
        receiveThread.start();

        // 启动心跳发送线程
        heartbeatThread = new Thread(this::sendHeartbeat);
        heartbeatThread.start();

        // 主循环：等待客户端运行，直到断开连接
        try {
            while (running.get() && connected.get()) {
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
        
        return true;
    }
    
    /**
     * 发送心跳消息
     */
    private void sendHeartbeat() {
        while (running.get() && connected.get()) {
            try {
                // 每10秒发送一次心跳（翻倍）
                Thread.sleep(10000);
                if (connected.get()) {
                    sendMessage(new GameMessage(MessageType.HEARTBEAT, "PING"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 接收服务器消息
     */
    private void receiveMessages() {
        while (running.get() && connected.get()) {
            try {
                // 检查是否有可用数据（至少5字节：1字节类型 + 4字节长度）
                if (dataIn != null && dataIn.available() >= 5) {
                    try {
                        GameMessage message = MessageSerializer.deserialize(dataIn);
                        handleMessage(message);
                    } catch (IllegalArgumentException e) {
                        // 消息类型错误，可能是流不同步
                        System.err.println("消息反序列化错误: " + e.getMessage());
                        System.err.println("可能是连接问题，尝试重新连接...");
                        handleDisconnect();
                        break;
                    } catch (EOFException e) {
                        // 流结束，连接可能已断开
                        System.err.println("流结束，连接可能已断开");
                        handleDisconnect();
                        break;
                    } catch (IOException e) {
                        // 其他IO错误，可能是流不同步或数据不完整
                        String errorMsg = e.getMessage();
                        if (errorMsg != null && (errorMsg.contains("无效的消息类型") || errorMsg.contains("数据不足"))) {
                            System.err.println("接收消息失败: " + errorMsg);
                            System.err.println("可能是流不同步，尝试重新连接...");
                            handleDisconnect();
                            break;
                        } else {
                            // 其他IO错误，继续尝试
                            System.err.println("接收消息时出错: " + errorMsg);
                            Thread.sleep(100); // 短暂等待后继续
                        }
                    }
                } else {
                    Thread.sleep(10);
                }
            } catch (IOException e) {
                if (connected.get()) {
                    System.err.println("接收消息失败: " + e.getMessage());
                    handleDisconnect();
                }
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 处理服务器消息
     */
    private void handleMessage(GameMessage message) {
        switch (message.getType()) {
            case DISPLAY_TEXT:
                System.out.print(message.getData());
                break;
            case REQUEST_INPUT:
                System.out.print(message.getData());
                // 检查是否是"按回车键继续"的提示，如果是则允许空输入
                boolean allowEmpty = message.getData().contains("按回车键继续");
                
                // 循环读取输入
                while (true) {
                    try {
                        String input = scanner.nextLine().trim();
                        if (!input.isEmpty() || allowEmpty) {
                            // 非空输入，或者允许空输入（按回车键继续）
                            sendMessage(new GameMessage(MessageType.USER_INPUT, input));
                            break; // 成功发送，退出循环
                        } else {
                            // 如果输入为空且不允许空输入，提示并重新读取
                            System.out.print("[输入为空，请重新输入]\n" + message.getData());
                        }
                    } catch (Exception e) {
                        // 读取输入出错，退出循环
                        System.err.println("读取输入失败: " + e.getMessage());
                        break;
                    }
                }
                break;
            case TIMEOUT_WARNING:
                System.out.println("\n" + message.getData());
                break;
            case TIMEOUT_EXCEEDED:
                System.out.println("\n" + message.getData());
                break;
            case ERROR:
                System.err.println("\n[错误] " + message.getData());
                break;
            case LOGIN_RESPONSE:
                if (message.getData().startsWith("SUCCESS")) {
                    System.out.println("登录成功！");
                } else if (message.getData().startsWith("FAIL")) {
                    System.out.println("登录失败: " + message.getData().substring(5));
                    // 登录失败后重新显示主菜单，等待服务器发送REQUEST_INPUT
                    System.out.println("\n欢迎来到武侠世界！");
                    System.out.println("请选择：");
                    System.out.println("1. 登录");
                    System.out.println("2. 注册");
                    // 不在这里读取输入，等待服务器发送REQUEST_INPUT
                }
                break;
            case REGISTER_RESPONSE:
                if (message.getData().startsWith("SUCCESS")) {
                    System.out.println("注册成功！");
                } else if (message.getData().startsWith("FAIL")) {
                    System.out.println("注册失败: " + message.getData().substring(5));
                    // 注册失败后重新显示主菜单，等待服务器发送REQUEST_INPUT
                    System.out.println("\n欢迎来到武侠世界！");
                    System.out.println("请选择：");
                    System.out.println("1. 登录");
                    System.out.println("2. 注册");
                    // 不在这里读取输入，等待服务器发送REQUEST_INPUT
                }
                break;
            case HEARTBEAT:
                // 回复心跳
                sendMessage(new GameMessage(MessageType.HEARTBEAT, "PONG"));
                break;
            default:
                System.out.println("收到消息: " + message);
        }
    }
    
    /**
     * 处理用户输入（用于主动发送消息）
     */
    private void handleUserInput() {
        // 这个功能主要用于发送特殊命令，普通输入由REQUEST_INPUT触发
        // 暂时留空，因为大部分输入都是响应服务器的REQUEST_INPUT
    }
    
    /**
     * 发送消息到服务器
     */
    public void sendMessage(GameMessage message) {
        try {
            if (dataOut != null && connected.get()) {
                MessageSerializer.serialize(message, dataOut);
            }
        } catch (IOException e) {
            System.err.println("发送消息失败: " + e.getMessage());
            handleDisconnect();
        }
    }
    
    /**
     * 处理断开连接
     */
    private void handleDisconnect() {
        System.out.println("\n与服务器断开连接");
        
        // 清理旧的连接
        cleanupConnection();
        
        // 尝试重连
        if (running.get()) {
            System.out.println("尝试重新连接...");
            if (connect()) {
                // 重新启动接收线程
                receiveThread = new Thread(this::receiveMessages);
                receiveThread.start();
                
                // 重新启动心跳线程
                heartbeatThread = new Thread(this::sendHeartbeat);
                heartbeatThread.start();
            } else {
                running.set(false);
            }
        }
    }
    
    /**
     * 断开连接（清理资源）
     */
    private void cleanupConnection() {
        connected.set(false);
        try {
            // 关闭输入流
            if (dataIn != null) {
                try {
                    dataIn.close();
                } catch (IOException e) {
                    // 忽略关闭错误
                }
                dataIn = null;
            }
            
            // 关闭输出流
            if (dataOut != null) {
                try {
                    dataOut.close();
                } catch (IOException e) {
                    // 忽略关闭错误
                }
                dataOut = null;
            }
            
            // 关闭socket
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // 忽略关闭错误
                }
                socket = null;
            }
        } catch (Exception e) {
            System.err.println("清理连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 断开连接（公共方法，用于完全关闭客户端）
     */
    public void disconnect() {
        running.set(false);
        cleanupConnection();
    }
    
    /**
     * 检查是否连接
     */
    public boolean isConnected() {
        return connected.get();
    }
}

