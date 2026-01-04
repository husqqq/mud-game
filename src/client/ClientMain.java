package main.client;

/**
 * 客户端主程序
 */
public class ClientMain {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    
    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        
        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("无效的端口号，使用默认端口: " + DEFAULT_PORT);
            }
        }
        
        GameClient client = new GameClient(host, port);
        boolean connected = client.start();
        
        // 如果连接失败，直接返回（不添加关闭钩子）
        if (!connected) {
            return;
        }
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(client::disconnect));
    }
}

