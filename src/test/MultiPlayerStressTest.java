package test;

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
            
            // 连接服务器
            connect();
            
            // 注册
            register();
            
            // 登录
            login();
            
            // 创建角色
            createCharacter();
            
            // 模拟游戏操作
            performGameActions();
            
            // 断开连接
            disconnect();
            
            System.out.println("[客户端 " + clientId + "] 测试完成");
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
                    // 简化接收逻辑，直接读取字节
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                // 忽略接收错误
            }
        }
        
        private void handleMessage(String message) {
            // 简单日志输出
            if (message.contains("ERROR") || message.contains("错误")) {
                System.err.println("[客户端 " + clientId + "] 收到错误: " + message);
            }
        }
        
        private void register() throws Exception {
            System.out.println("[客户端 " + clientId + "] 注册账号: " + username);
            sendMessage("1"); // 选择注册
            Thread.sleep(300);
            sendMessage(username);
            Thread.sleep(300);
            sendMessage(password);
            Thread.sleep(500);
        }
        
        private void login() throws Exception {
            System.out.println("[客户端 " + clientId + "] 登录账号: " + username);
            sendMessage("2"); // 选择登录
            Thread.sleep(300);
            sendMessage(username);
            Thread.sleep(300);
            sendMessage(password);
            Thread.sleep(500);
        }
        
        private void createCharacter() throws Exception {
            System.out.println("[客户端 " + clientId + "] 创建角色");
            
            // 选择创建新角色
            String playerName = "Player_" + clientId;
            sendMessage(playerName);
            Thread.sleep(300);
            
            // 属性分配 - 直接回车平均分配
            sendMessage("");
            Thread.sleep(300);
            
            // 选择主技能 - 随机选择
            int skillChoice = 1 + random.nextInt(3);
            sendMessage(String.valueOf(skillChoice));
            Thread.sleep(500);
        }
        
        private void performGameActions() throws Exception {
            int numActions = 3 + random.nextInt(3); // 3-5个动作
            
            for (int i = 0; i < numActions; i++) {
                int action = 1 + random.nextInt(5); // 1-5 随机选择操作
                
                System.out.println("[客户端 " + clientId + "] 执行动作 " + (i + 1) + "/" + numActions + ": " + getActionName(action));
                
                switch (action) {
                    case 1: // 查看状态
                        performViewStatus();
                        break;
                    case 2: // 修炼
                        performTraining();
                        break;
                    case 3: // 与NPC战斗
                        performNpcBattle();
                        break;
                    case 4: // 进入决斗池
                        performArenaBattle();
                        break;
                    case 5: // 退出
                        return;
                    default:
                        break;
                }
                
                Thread.sleep(500 + random.nextInt(1000));
            }
        }
        
        private String getActionName(int action) {
            switch (action) {
                case 1: return "查看状态";
                case 2: return "修炼";
                case 3: return "与NPC战斗";
                case 4: return "进入决斗池";
                case 5: return "退出";
                default: return "未知";
            }
        }
        
        private void performViewStatus() throws Exception {
            sendMessage("1");
            Thread.sleep(500);
            sendMessage(""); // 按回车继续
            Thread.sleep(300);
        }
        
        private void performTraining() throws Exception {
            sendMessage("2");
            Thread.sleep(500);
            
            // 选择训练技能（随机）
            int skillChoice = 1 + random.nextInt(3);
            sendMessage(String.valueOf(skillChoice));
            Thread.sleep(500);
            
            // 确认训练
            sendMessage("y");
            Thread.sleep(1000);
            
            sendMessage(""); // 按回车继续
            Thread.sleep(300);
        }
        
        private void performNpcBattle() throws Exception {
            sendMessage("3");
            Thread.sleep(500);
            
            // 选择难度（简单）
            sendMessage("1");
            Thread.sleep(500);
            
            // 模拟战斗中的技能选择（3-5次）
            int numRounds = 3 + random.nextInt(3);
            for (int i = 0; i < numRounds; i++) {
                Thread.sleep(800);
                // 随机选择技能
                int skillChoice = 1 + random.nextInt(3);
                sendMessage(String.valueOf(skillChoice));
            }
            
            Thread.sleep(1000);
            sendMessage(""); // 按回车继续
            Thread.sleep(300);
        }
        
        private void performArenaBattle() throws Exception {
            sendMessage("4");
            Thread.sleep(1000);
            
            // 可能需要选择目标和技能
            // 选择第一个目标
            sendMessage("1");
            Thread.sleep(800);
            
            // 选择技能
            int skillChoice = 1 + random.nextInt(3);
            sendMessage(String.valueOf(skillChoice));
            Thread.sleep(1000);
            
            // 按回车继续
            sendMessage("");
            Thread.sleep(500);
        }
        
        private void disconnect() throws Exception {
            System.out.println("[客户端 " + clientId + "] 断开连接");
            
            // 发送退出命令
            try {
                sendMessage("5"); // 退出
                Thread.sleep(300);
                sendMessage("n"); // 不保存
                Thread.sleep(500);
            } catch (Exception e) {
                // 忽略退出时的错误
            }
            
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
        
        private void sendMessage(String message) throws IOException {
            // 简单地发送字符串（需要根据实际协议调整）
            out.writeUTF(message);
            out.flush();
        }
    }
}
