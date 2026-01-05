package main.network;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 消息序列化工具类
 * 使用二进制格式序列化/反序列化消息
 * 格式: [消息类型: 1字节][字符串长度: 4字节][字符串内容: UTF-8编码]
 */
public class MessageSerializer {
    // 最长1MB，防止异常数据撑爆内存
    private static final int MAX_MESSAGE_BYTES = 1024 * 1024;
    
    /**
     * 序列化消息到输出流
     */
    public static void serialize(GameMessage message, DataOutputStream out) throws IOException {
        // 写入消息类型（1字节）
        out.writeByte(message.getType().getValue());
        
        // 获取字符串的UTF-8字节
        byte[] dataBytes = message.getData().getBytes(StandardCharsets.UTF_8);
        
        // 写入字符串长度（4字节）
        out.writeInt(dataBytes.length);
        
        // 写入字符串内容
        out.write(dataBytes);
        
        out.flush();
    }
    
    /**
     * 从输入流反序列化消息
     */
    public static GameMessage deserialize(DataInputStream in) throws IOException {
        try {
            // 读取消息类型（1字节）
            byte typeValue = in.readByte();
            MessageType type;
            try {
                type = MessageType.fromValue(typeValue);
            } catch (IllegalArgumentException e) {
                // 无效的消息类型，可能是流不同步
                // 尝试跳过一些字节以恢复同步（但这可能不是最佳方案）
                int available = in.available();
                throw new IOException("无效的消息类型: " + (typeValue & 0xFF) + " (可能是流不同步，可用字节: " + available + ")", e);
            }
            
            // 读取字符串长度（4字节）
            int dataLength = in.readInt();
            
            // 验证数据长度（防止异常大的数据）
            if (dataLength < 0 || dataLength > MAX_MESSAGE_BYTES) {
                throw new IOException("无效的数据长度: " + dataLength);
            }

            // 读取字符串内容
            byte[] dataBytes = new byte[dataLength];
            in.readFully(dataBytes);
            
            // 转换为UTF-8字符串
            String data = new String(dataBytes, StandardCharsets.UTF_8);
            
            return new GameMessage(type, data);
        } catch (EOFException e) {
            // 流结束
            throw new IOException("流结束，连接可能已断开", e);
        }
    }
    
    /**
     * 检查输入流是否有可用数据（非阻塞）
     */
    public static boolean hasData(InputStream in) throws IOException {
        if (in == null) {
            return false;
        }
        try {
            // 至少要有1字节才能尝试读取消息头
            return in.available() >= 1;
        } catch (IOException e) {
            // 流可能已关闭
            return false;
        }
    }
}

