package main.network;

/**
 * 消息类型枚举
 */
public enum MessageType {
    // 基本IO消息
    DISPLAY_TEXT(1),           // 服务器发送显示文本
    REQUEST_INPUT(2),          // 服务器请求输入
    USER_INPUT(3),             // 客户端发送用户输入
    
    // 认证消息
    LOGIN_REQUEST(10),         // 客户端登录请求
    LOGIN_RESPONSE(11),        // 服务器登录响应
    REGISTER_REQUEST(12),      // 客户端注册请求
    REGISTER_RESPONSE(13),     // 服务器注册响应
    RECONNECT_REQUEST(14),     // 客户端重连请求
    RECONNECT_RESPONSE(15),   // 服务器重连响应
    
    // 游戏状态消息
    PLAYER_LIST(20),           // 玩家列表
    SELECT_PLAYER(21),         // 选择玩家
    CREATE_PLAYER(22),         // 创建玩家
    
    // 超时和连接消息
    TIMEOUT_WARNING(30),       // 超时警告
    TIMEOUT_EXCEEDED(31),      // 超时已超过
    HEARTBEAT(32),             // 心跳包
    DISCONNECT(33),            // 断开连接
    
    // 错误消息
    ERROR(40);                 // 错误消息
    
    private final byte value;
    
    MessageType(int value) {
        this.value = (byte) value;
    }
    
    public byte getValue() {
        return value;
    }
    
    public static MessageType fromValue(byte value) {
        for (MessageType type : MessageType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + value);
    }
}

