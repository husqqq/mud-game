package test;

import main.network.GameMessage;
import main.network.MessageSerializer;
import main.network.MessageType;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 多人游戏压力测试
 * 模拟多个客户端同时连接并进行各种操作
 */
public class MultiPlayerStressTest {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final int MIN_CLIENTS = 2;
    private static final int MAX_CLIENTS = 8;
    private static final int FIXED_CLIENTS = 8; // 固定创建8个客户端，与服务器设置一致
    
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger errorCount = new AtomicInteger(0);
    private static final List<String> errorLogs = new CopyOnWriteArrayList<>();
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("多人游戏压力测试");
        System.out.println("========================================");
        
        // 检查服务器是否运行
        System.out.println("\n检查服务器连接...");
        if (!checkServerRunning()) {
            System.err.println("错误：服务器未运行！");
            System.err.println("请先启动服务器：");
            System.err.println("  Windows: 运行 run.bat");
            System.err.println("  或手动: cd bin && java main.Main 2");
            System.err.println("\n然后再运行此测试脚本。");
            System.exit(1);
        }
        System.out.println("服务器连接正常！");
        
        // 运行多轮测试
        int totalRounds = 5;
        for (int round = 1; round <= totalRounds; round++) {
            // 固定创建8个客户端，与服务器设置一致
            int numClients = FIXED_CLIENTS;
            System.out.println("\n========================================");
            System.out.println("第 " + round + " 轮测试：" + numClients + " 个客户端");
            System.out.println("========================================");
            
            runTest(numClients);
            
            // 每轮之间等待一下
            if (round < totalRounds) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        
        // 打印测试结果
        System.out.println("\n========================================");
        System.out.println("测试完成！");
        System.out.println("========================================");
        System.out.println("成功操作数: " + successCount.get());
        System.out.println("错误操作数: " + errorCount.get());
        
        if (!errorLogs.isEmpty()) {
            System.out.println("\n错误日志:");
            for (String log : errorLogs) {
                System.out.println("  - " + log);
            }
        }
        
        System.out.println("\n测试完成！服务器仍在运行，可以手动关闭。");
        System.exit(0);
    }
    
    /**
     * 检查服务器是否运行
     */
    private static boolean checkServerRunning() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), 3000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 运行一轮测试
     */
    private static void runTest(int numClients) {
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        CountDownLatch latch = new CountDownLatch(numClients);
        
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i + 1;
            final String username = "test_user_" + System.currentTimeMillis() + "_" + clientId;
            final String password = "test_pass_" + clientId;
            
            Future<?> future = executor.submit(() -> {
                try {
                    TestClient client = new TestClient(clientId, username, password);
                    client.run();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    String errorMsg = "客户端 " + clientId + " 错误: " + e.getMessage();
                    errorLogs.add(errorMsg);
                    System.err.println(errorMsg);
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
            
            futures.add(future);
        }
        
        // 等待所有客户端完成或超时
        try {
            boolean finished = latch.await(120, TimeUnit.SECONDS);
            if (!finished) {
                System.err.println("警告：部分客户端测试超时！");
                for (Future<?> future : futures) {
                    future.cancel(true);
                }
            }
        } catch (InterruptedException e) {
            System.err.println("测试被中断: " + e.getMessage());
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
    
    /**
     * 测试客户端
     */
    static class TestClient {
        private final int clientId;
        private final String username;
        private final String password;
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private final Random random = new Random();
        
        public TestClient(int clientId, String username, String password) {
            this.clientId = clientId;
            this.username = username;
            this.password = password;
        }
        
        public void run() throws Exception {
            System.out.println("[客户端 " + clientId + "] 开始连接...");
            
            try {
                // 连接服务器
                connect();
                
                // 注册（注册成功后服务器会自动登录并要求创建角色）
                registerAndCreateCharacter();
                
                // 等待游戏开始（等待所有玩家连接）
                System.out.println("[客户端 " + clientId + "] 等待游戏开始...");
                Thread.sleep(5000);
                
                // 简单等待一段时间（模拟游戏过程）
                System.out.println("[客户端 " + clientId + "] 游戏进行中...");
                Thread.sleep(3000);
                
                System.out.println("[客户端 " + clientId + "] 测试完成");
            } catch (Exception e) {
                System.err.println("[客户端 " + clientId + "] 错误: " + e.getMessage());
                throw e;
            } finally {
                // 断开连接
                disconnect();
            }
        }
        
        private void connect() throws IOException {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            
            // 启动接收线程
            new Thread(this::receiveMessages).start();
            
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        private void receiveMessages() {
            try {
                while (!socket.isClosed()) {
                    // 使用正确的协议接收GameMessage
                    if (MessageSerializer.hasData(in)) {
                        GameMessage message = MessageSerializer.deserialize(in);
                        handleMessage(message);
                    } else {
                        Thread.sleep(50);
                    }
                }
            } catch (Exception e) {
                // 连接关闭或其他错误，忽略
            }
        }
        
        private void handleMessage(GameMessage message) {
            // 简单日志输出
            String data = message.getData();
            if (message.getType() == MessageType.ERROR || 
                (data != null && (data.contains("ERROR") || data.contains("错误")))) {
                System.err.println("[客户端 " + clientId + "] 收到错误: " + data);
            }
            // 可以根据需要添加更多消息处理
        }
        
        private void registerAndCreateCharacter() throws Exception {
            System.out.println("[客户端 " + clientId + "] 开始注册和创建角色");
            
            // 等待服务器发送欢迎消息
            Thread.sleep(500);
            
            // 选择注册
            System.out.println("[客户端 " + clientId + "] 选择注册");
            sendMessage("2");
            Thread.sleep(500);
            
            // 发送用户名
            System.out.println("[客户端 " + clientId + "] 发送用户名: " + username);
            sendMessage(username);
            Thread.sleep(500);
            
            // 发送密码
            System.out.println("[客户端 " + clientId + "] 发送密码");
            sendMessage(password);
            Thread.sleep(1000);
            
            // 注册成功后，服务器会自动进入角色选择流程
            // 服务器会提示："您还没有角色，需要创建新角色"
            // 然后会请求输入角色名
            String playerName = "Player_" + clientId;
            System.out.println("[客户端 " + clientId + "] 输入角色名: " + playerName);
            sendMessage(playerName);
            Thread.sleep(500);
            
            // 服务器会要求分配属性点
            // "请输入要加点的属性名 (str/agi/con/int/luk/def)，回车平均分配剩余点数: "
            System.out.println("[客户端 " + clientId + "] 选择平均分配属性（发送空字符串）");
            sendMessage(""); // 回车平均分配
            Thread.sleep(1000);
            
            // 服务器会要求选择主流派
            // "请选择主用流派：1. 刀法 2. 剑法 3. 拳法"
            int skillChoice = 1 + random.nextInt(3);
            System.out.println("[客户端 " + clientId + "] 选择技能: " + skillChoice);
            sendMessage(String.valueOf(skillChoice));
            Thread.sleep(1000);
            
            System.out.println("[客户端 " + clientId + "] 角色创建完成");
        }
        
        
        private void disconnect() {
            try {
                System.out.println("[客户端 " + clientId + "] 断开连接");
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception e) {
                // 忽略断开连接时的错误
            }
        }
        
        private void sendMessage(String message) throws IOException {
            // 使用正确的协议：发送GameMessage
            GameMessage msg = new GameMessage(MessageType.USER_INPUT, message);
            MessageSerializer.serialize(msg, out);
            out.flush();
        }
    }
}
