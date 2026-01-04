package main.network;

/**
 * 游戏消息类
 * 封装消息类型和数据
 */
public class GameMessage {
    private final MessageType type;
    private final String data;
    
    public GameMessage(MessageType type, String data) {
        this.type = type;
        this.data = data != null ? data : "";
    }
    
    public MessageType getType() {
        return type;
    }
    
    public String getData() {
        return data;
    }
    
    @Override
    public String toString() {
        return "GameMessage{type=" + type + ", data='" + data + "'}";
    }
}

