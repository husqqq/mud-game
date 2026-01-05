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
    private final AtomicBoolean reconnecting;  // 是否正在重连
    private Thread receiveThread;
    private Thread heartbeatThread;
    private final Scanner scanner;
    
    public GameClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.connected = new AtomicBoolean(false);
        this.running = new AtomicBoolean(false);
        this.reconnecting = new AtomicBoolean(false);
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
                if (dataIn != null && MessageSerializer.hasData(dataIn)) {
                    try {
                        GameMessage message = MessageSerializer.deserialize(dataIn);
                        handleMessage(message);
                    } catch (IllegalArgumentException e) {
                        // 消息类型错误，可能是流不同步
                        System.err.println("消息反序列化错误: " + e.getMessage());
                        connected.set(false);
                        break;
                    } catch (EOFException e) {
                        // 流结束，连接可能已断开
                        System.err.println("流结束，连接可能已断开");
                        connected.set(false);
                        break;
                    } catch (IOException e) {
                        // 其他IO错误，可能是流不同步或数据不完整
                        String errorMsg = e.getMessage();
                        if (errorMsg != null && (errorMsg.contains("无效的消息类型") || errorMsg.contains("数据不足") || errorMsg.contains("无法读取"))) {
                            System.err.println("接收消息失败: " + errorMsg);
                            // 流不同步，标记断开并退出循环
                            connected.set(false);
                            break;
                        } else {
                            // 其他IO错误，检查连接状态
                            if (!connected.get()) {
                                break; // 连接已断开，退出循环
                            }
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
                    connected.set(false);
                }
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // 线程退出后，如果客户端仍在运行且尚未进入重连流程，触发重连
        if (running.get() && !connected.get()) {
            handleDisconnect();
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
                // 检查连接状态，如果已断开则不处理输入
                if (!connected.get() || !running.get()) {
                    break;
                }
                
                System.out.print(message.getData());
                // 检查是否允许空输入：包含“按回车”/“回车默认”/“回车平均”等提示时允许空输入
                String prompt = message.getData();
                boolean allowEmpty = prompt.contains("按回车") || prompt.contains("回车默认") || prompt.contains("回车平均");
                
                // 循环读取输入
                while (connected.get() && running.get()) {
                    try {
                        String input = scanner.nextLine().trim();
                        // 再次检查连接状态
                        if (!connected.get() || !running.get()) {
                            break;
                        }
                        if (!input.isEmpty() || allowEmpty) {
                            // 非空输入，或者允许空输入（按回车键继续）
                            sendMessage(new GameMessage(MessageType.USER_INPUT, input));
                            break; // 成功发送，退出循环
                        } else {
                            // 如果输入为空且不允许空输入，提示并重新读取
                            System.out.print("[输入为空，请重新输入]\n" + message.getData());
                        }
                    } catch (java.util.NoSuchElementException e) {
                        // Scanner已关闭或流结束
                        System.err.println("输入流已关闭");
                        break;
                    } catch (Exception e) {
                        // 读取输入出错，检查连接状态
                        if (!connected.get() || !running.get()) {
                            break;
                        }
                        System.err.println("读取输入失败: " + e.getMessage());
                        // 如果是连接问题，退出循环
                        if (e.getMessage() != null && e.getMessage().contains("end")) {
                            break;
                        }
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
            // 检查连接状态和socket状态
            if (dataOut != null && connected.get() && socket != null && !socket.isClosed()) {
                // 检查socket是否真的连接
                if (socket.isConnected() && !socket.isOutputShutdown()) {
                    MessageSerializer.serialize(message, dataOut);
                } else {
                    // Socket已关闭或输出流已关闭
                    connected.set(false);
                    System.err.println("连接已关闭，无法发送消息");
                    handleDisconnect();
                }
            } else if (!connected.get()) {
                // 连接已断开，不发送消息
                return;
            }
        } catch (java.net.SocketException e) {
            // Socket异常，通常是连接被关闭
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("连接") || errorMsg.contains("Connection") || 
                                    errorMsg.contains("reset") || errorMsg.contains("abort") ||
                                    errorMsg.contains("中止"))) {
                // 连接被关闭，这是正常的（服务器断开连接）
                if (connected.get()) {
                    System.out.println("与服务器连接已关闭");
                }
            } else {
                System.err.println("发送消息失败 (Socket异常): " + errorMsg);
            }
            handleDisconnect();
        } catch (IOException e) {
            // 其他IO异常
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("连接") || errorMsg.contains("Connection") || 
                                    errorMsg.contains("reset") || errorMsg.contains("abort") ||
                                    errorMsg.contains("中止"))) {
                // 连接被关闭，这是正常的
                if (connected.get()) {
                    System.out.println("与服务器连接已关闭");
                }
            } else {
                System.err.println("发送消息失败: " + errorMsg);
            }
            handleDisconnect();
        } catch (Exception e) {
            // 其他异常
            System.err.println("发送消息时发生未知错误: " + e.getMessage());
            handleDisconnect();
        }
    }
    
    /**
     * 处理断开连接
     */
    private void handleDisconnect() {
        // 如果已经在重连中，避免重复处理
        if (reconnecting.get()) {
            return;
        }
        
        // 如果已经断开，避免重复处理
        if (!connected.get() && receiveThread == null && heartbeatThread == null) {
            return;
        }
        
        // 设置重连标志，防止重复调用
        if (!reconnecting.compareAndSet(false, true)) {
            return; // 已经在重连中
        }
        
        try {
            System.out.println("\n与服务器断开连接");
            
            // 先停止运行标志，让线程自然退出
            connected.set(false);
            
            // 等待线程结束（最多等待1秒）
            try {
                if (receiveThread != null && receiveThread.isAlive()) {
                    receiveThread.join(1000);
                }
                if (heartbeatThread != null && heartbeatThread.isAlive()) {
                    heartbeatThread.join(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 清理旧的连接
            cleanupConnection();
            
            // 尝试重连（最多重试3次，避免无限重连）
            if (running.get()) {
                int reconnectAttempts = 0;
                final int MAX_RECONNECT_ATTEMPTS = 3;
                
                while (reconnectAttempts < MAX_RECONNECT_ATTEMPTS && running.get()) {
                    System.out.println("尝试重新连接... (" + (reconnectAttempts + 1) + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                    if (connect()) {
                        // 重连成功，但服务器会把它当作新连接处理
                        // 发送重连请求，让服务器知道这是重连
                        try {
                            sendMessage(new GameMessage(MessageType.RECONNECT_REQUEST, "RECONNECT"));
                        } catch (Exception e) {
                            // 发送重连请求失败，继续处理
                            System.err.println("发送重连请求失败: " + e.getMessage());
                        }
                        
                        // 重新启动接收线程
                        receiveThread = new Thread(this::receiveMessages);
                        receiveThread.start();
                        
                        // 重新启动心跳线程
                        heartbeatThread = new Thread(this::sendHeartbeat);
                        heartbeatThread.start();
                        reconnecting.set(false); // 重连成功，清除标志
                        
                        // 重连后，提示用户需要重新登录，继续运行等待服务器消息
                        System.out.println("\n已重新连接到服务器");
                        System.out.println("注意：重连后需要重新登录");
                        System.out.println("请按照服务器提示进行操作...\n");
                        
                        return; // 重连成功，退出
                    }
                    reconnectAttempts++;
                    
                    // 等待一段时间再重试
                    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        try {
                            Thread.sleep(2000); // 等待2秒
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                // 重连失败，停止运行
                System.err.println("重连失败，停止客户端");
                running.set(false);
            }
        } finally {
            // 清除重连标志
            reconnecting.set(false);
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

