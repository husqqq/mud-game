package main;// File: SaveLoadService.java

import java.io.*;
import java.nio.file.*;

/**
 * 存档加载服务类
 */
public class SaveLoadService {
    private static final String SAVE_DIR = "./saves/";
    
    public SaveLoadService() {
        // 确保存档目录存在
        try {
            Files.createDirectories(Paths.get(SAVE_DIR));
        } catch (IOException e) {
            ConsoleIO.println("警告：无法创建存档目录！");
        }
    }
    
    /**
     * 保存玩家数据
     */
    public boolean save(Player player) {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(getSaveFilePath(player.getUsername())))) {
            
            // 使用序列化保存完整Player对象
            oos.writeObject(player);
            
            ConsoleIO.println("保存成功！");
            return true;
        } catch (IOException e) {
            ConsoleIO.println("保存失败：" + e.getMessage());
            return false;
        }
    }
    
    /**
     * 加载玩家数据
     */
    public Player load(String username) {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(getSaveFilePath(username)))) {
            
            // 使用反序列化加载完整Player对象
            Player player = (Player) ois.readObject();
            
            // 确保生命值正确
            player.getStats().recalcHpMax();
            player.restoreFullHp();
            
            // 重新计算战力（确保数据一致性）
            player.recalcPower();
            
            // 确保回合数已初始化（兼容旧存档）
            if (player.getRoundCount() < 0) {
                player.setRoundCount(0);
            }
            
            ConsoleIO.println("加载成功！欢迎回来，" + player.getName() + "！");
            return player;
        } catch (IOException | ClassNotFoundException e) {
            ConsoleIO.println("加载失败：" + e.getMessage());
            return null;
        }
    }
    
    /**
     * 检查存档是否存在
     */
    public boolean saveExists(String username) {
        return Files.exists(Paths.get(getSaveFilePath(username)));
    }
    
    /**
     * 获取存档文件路径
     */
    private String getSaveFilePath(String username) {
        return SAVE_DIR + username + ".sav";
    }
    
    /**
     * 删除存档
     */
    public boolean deleteSave(String username) {
        try {
            Files.deleteIfExists(Paths.get(getSaveFilePath(username)));
            return true;
        } catch (IOException e) {
            ConsoleIO.println("删除存档失败：" + e.getMessage());
            return false;
        }
    }
}

