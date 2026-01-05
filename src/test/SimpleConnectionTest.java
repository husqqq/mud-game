package test;

import main.network.GameMessage;
import main.network.MessageSerializer;
import main.network.MessageType;

import java.io.*;
import java.net.*;

/**
 * 简单的连接测试
 * 用于验证客户端和服务器的协议通信是否正常
 */
public class SimpleConnectionTest {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("简单连接测试");
        System.out.println("========================================");
        System.out.println("\n重要：请先启动服务器！");
        System.out.println("运行 run.bat，选择多人模式(2)，输入玩家数量(2)");
        System.out.println("\n按回车开始测试...");
        
        try {
            System.in.read();
        } catch (IOException e) {
            // ignore
        }
        
        try {
            System.out.println("\n开始连接测试...");
            testConnection();
            System.out.println("\n✅ 测试成功！");
        } catch (Exception e) {
            System.err.println("\n❌ 测试失败！");
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testConnection() throws Exception {
        Socket socket = null;
        DataInputStream in = null;
        DataOutputStream out = null;
        
        try {
            // 1. 连接服务器
            System.out.println("1. 正在连接服务器 " + SERVER_HOST + ":" + SERVER_PORT + "...");
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            System.out.println("   ✓ 连接成功");
            
            // 2. 等待服务器欢迎消息
            System.out.println("\n2. 等待服务器欢迎消息...");
            Thread.sleep(500);
            
            // 读取所有待处理的消息
            int msgCount = 0;
            while (MessageSerializer.hasData(in)) {
                GameMessage msg = MessageSerializer.deserialize(in);
                msgCount++;
                System.out.println("   收到消息 " + msgCount + ": " + msg.getType() + " - " + 
                    (msg.getData() != null && msg.getData().length() > 50 ? 
                     msg.getData().substring(0, 50) + "..." : msg.getData()));
                Thread.sleep(100);
            }
            System.out.println("   ✓ 收到 " + msgCount + " 条消息");
            
            // 3. 发送注册选择
            System.out.println("\n3. 选择注册 (发送 '2')...");
            GameMessage registerChoice = new GameMessage(MessageType.USER_INPUT, "2");
            MessageSerializer.serialize(registerChoice, out);
            out.flush();
            System.out.println("   ✓ 已发送");
            Thread.sleep(500);
            
            // 4. 读取服务器响应
            System.out.println("\n4. 等待服务器响应...");
            msgCount = 0;
            while (MessageSerializer.hasData(in)) {
                GameMessage msg = MessageSerializer.deserialize(in);
                msgCount++;
                System.out.println("   收到消息 " + msgCount + ": " + msg.getType() + " - " + msg.getData());
                Thread.sleep(100);
            }
            System.out.println("   ✓ 收到 " + msgCount + " 条消息");
            
            // 5. 发送用户名
            String username = "test_user_" + System.currentTimeMillis();
            System.out.println("\n5. 发送用户名: " + username);
            GameMessage usernameMsg = new GameMessage(MessageType.USER_INPUT, username);
            MessageSerializer.serialize(usernameMsg, out);
            out.flush();
            System.out.println("   ✓ 已发送");
            Thread.sleep(500);
            
            // 6. 读取服务器响应
            System.out.println("\n6. 等待服务器响应...");
            msgCount = 0;
            while (MessageSerializer.hasData(in)) {
                GameMessage msg = MessageSerializer.deserialize(in);
                msgCount++;
                System.out.println("   收到消息 " + msgCount + ": " + msg.getType() + " - " + msg.getData());
                Thread.sleep(100);
            }
            System.out.println("   ✓ 收到 " + msgCount + " 条消息");
            
            // 7. 发送密码
            String password = "test123";
            System.out.println("\n7. 发送密码: " + password);
            GameMessage passwordMsg = new GameMessage(MessageType.USER_INPUT, password);
            MessageSerializer.serialize(passwordMsg, out);
            out.flush();
            System.out.println("   ✓ 已发送");
            Thread.sleep(1000);
            
            // 8. 读取注册结果
            System.out.println("\n8. 等待注册结果...");
            long startTime = System.currentTimeMillis();
            boolean gotResponse = false;
            while (System.currentTimeMillis() - startTime < 5000) { // 等待最多5秒
                if (MessageSerializer.hasData(in)) {
                    GameMessage msg = MessageSerializer.deserialize(in);
                    System.out.println("   收到: " + msg.getType() + " - " + msg.getData());
                    if (msg.getType() == MessageType.REGISTER_RESPONSE) {
                        gotResponse = true;
                        if (msg.getData().startsWith("SUCCESS")) {
                            System.out.println("   ✓ 注册成功！");
                        } else {
                            System.out.println("   ✗ 注册失败: " + msg.getData());
                        }
                    }
                }
                Thread.sleep(100);
            }
            
            if (!gotResponse) {
                System.out.println("   ⚠ 未收到注册响应");
            }
            
            // 9. 检查连接状态
            System.out.println("\n9. 检查连接状态...");
            if (socket.isClosed()) {
                System.out.println("   ✗ 连接已关闭");
            } else {
                System.out.println("   ✓ 连接仍然活跃");
                
                // 尝试再读取一些消息
                Thread.sleep(1000);
                System.out.println("\n10. 检查是否有更多消息...");
                msgCount = 0;
                startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < 3000) {
                    if (MessageSerializer.hasData(in)) {
                        GameMessage msg = MessageSerializer.deserialize(in);
                        msgCount++;
                        System.out.println("   收到消息 " + msgCount + ": " + msg.getType() + " - " + 
                            (msg.getData() != null && msg.getData().length() > 100 ? 
                             msg.getData().substring(0, 100) + "..." : msg.getData()));
                    }
                    Thread.sleep(100);
                }
                System.out.println("   共收到 " + msgCount + " 条额外消息");
            }
            
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("\n连接已关闭");
        }
    }
}
