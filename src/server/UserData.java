package main.server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户数据类
 * 存储用户的基本信息和绑定的玩家
 */
public class UserData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String username;
    private String passwordHash;  // 密码哈希值
    private String salt;          // 密码盐值
    private List<String> playerNames;  // 该用户绑定的玩家名列表
    private long lastLoginTime;   // 最后登录时间
    
    public UserData(String username, String passwordHash, String salt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.playerNames = new ArrayList<>();
        this.lastLoginTime = System.currentTimeMillis();
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public String getSalt() {
        return salt;
    }
    
    public List<String> getPlayerNames() {
        return new ArrayList<>(playerNames);
    }
    
    public void addPlayerName(String playerName) {
        if (!playerNames.contains(playerName)) {
            playerNames.add(playerName);
        }
    }
    
    public void removePlayerName(String playerName) {
        playerNames.remove(playerName);
    }
    
    public long getLastLoginTime() {
        return lastLoginTime;
    }
    
    public void updateLastLoginTime() {
        this.lastLoginTime = System.currentTimeMillis();
    }
}

