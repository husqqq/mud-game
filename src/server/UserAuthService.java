package main.server;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户认证服务
 * 处理用户注册、登录、密码验证等
 */
public class UserAuthService {
    private static final String USER_DATA_DIR = "userdata";
    private static final String USER_DATA_FILE = "users.dat";
    private final Map<String, UserData> users;  // 内存中的用户数据
    private final SecureRandom random;
    
    public UserAuthService() {
        this.users = new ConcurrentHashMap<>();
        this.random = new SecureRandom();
        loadUsers();
    }
    
    /**
     * 注册新用户
     */
    public boolean register(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        if (password == null || password.isEmpty()) {
            return false;
        }
        if (users.containsKey(username)) {
            return false;  // 用户已存在
        }
        
        // 生成盐值
        String salt = generateSalt();
        // 计算密码哈希
        String passwordHash = hashPassword(password, salt);
        
        // 创建用户数据
        UserData userData = new UserData(username, passwordHash, salt);
        users.put(username, userData);
        
        // 保存到文件
        saveUsers();
        
        return true;
    }
    
    /**
     * 验证用户登录
     */
    public boolean authenticate(String username, String password) {
        UserData userData = users.get(username);
        if (userData == null) {
            return false;
        }
        
        // 验证密码
        String passwordHash = hashPassword(password, userData.getSalt());
        if (passwordHash.equals(userData.getPasswordHash())) {
            userData.updateLastLoginTime();
            saveUsers();
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取用户的玩家列表
     */
    public List<String> getPlayerNames(String username) {
        UserData userData = users.get(username);
        if (userData == null) {
            return new ArrayList<>();
        }
        return userData.getPlayerNames();
    }
    
    /**
     * 绑定玩家到用户
     */
    public void bindPlayer(String username, String playerName) {
        UserData userData = users.get(username);
        if (userData != null) {
            userData.addPlayerName(playerName);
            saveUsers();
        }
    }
    
    /**
     * 解绑玩家
     */
    public void unbindPlayer(String username, String playerName) {
        UserData userData = users.get(username);
        if (userData != null) {
            userData.removePlayerName(playerName);
            saveUsers();
        }
    }
    
    /**
     * 检查用户是否存在
     */
    public boolean userExists(String username) {
        return users.containsKey(username);
    }
    
    /**
     * 生成随机盐值
     */
    private String generateSalt() {
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }
    
    /**
     * 计算密码哈希（SHA-256）
     */
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes());
            byte[] hash = digest.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("密码哈希计算失败", e);
        }
    }
    
    /**
     * 加载用户数据
     */
    @SuppressWarnings("unchecked")
    private void loadUsers() {
        try {
            Path dir = Paths.get(USER_DATA_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            
            Path file = Paths.get(USER_DATA_DIR, USER_DATA_FILE);
            if (!Files.exists(file)) {
                return;  // 文件不存在，使用空数据
            }
            
            try (ObjectInputStream ois = new ObjectInputStream(
                    new FileInputStream(file.toFile()))) {
                Map<String, UserData> loadedUsers = (Map<String, UserData>) ois.readObject();
                users.putAll(loadedUsers);
            }
        } catch (Exception e) {
            System.err.println("加载用户数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 保存用户数据
     */
    private void saveUsers() {
        try {
            Path dir = Paths.get(USER_DATA_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            
            Path file = Paths.get(USER_DATA_DIR, USER_DATA_FILE);
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(file.toFile()))) {
                oos.writeObject(users);
            }
        } catch (Exception e) {
            System.err.println("保存用户数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

