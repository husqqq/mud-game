package main.server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 用户数据类
 * 存储用户的基本信息和绑定的玩家
 */
public class UserData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String username;  // final 保证不可变
    private final String passwordHash;  // 密码哈希值，final 保证不可变
    private final String salt;          // 密码盐值，final 保证不可变
    private final List<String> playerNames;  // 该用户绑定的玩家名列表，使用线程安全的列表
    private volatile long lastLoginTime;   // 最后登录时间，使用 volatile 保证可见性
    
    public UserData(String username, String passwordHash, String salt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.playerNames = new CopyOnWriteArrayList<>();  // 线程安全的列表
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
        return new ArrayList<>(playerNames);  // 返回副本，保证线程安全
    }
    
    public void addPlayerName(String playerName) {
        if (!playerNames.contains(playerName)) {
            playerNames.add(playerName);  // CopyOnWriteArrayList 是线程安全的
        }
    }
    
    public void removePlayerName(String playerName) {
        playerNames.remove(playerName);  // CopyOnWriteArrayList 是线程安全的
    }
    
    public long getLastLoginTime() {
        return lastLoginTime;
    }
    
    public void updateLastLoginTime() {
        this.lastLoginTime = System.currentTimeMillis();
    }
}

