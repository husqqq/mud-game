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
            // 警告：无法创建存档目录
        }
    }
    
    /**
     * 保存玩家数据
     */
    public boolean save(Player player) {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(getSaveFilePath(player.getSaveName())))) {
            
            // 使用序列化保存完整Player对象
            oos.writeObject(player);

            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 加载玩家数据
     */
    public Player load(String saveName) {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(getSaveFilePath(saveName)))) {
            
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
            
            return player;
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }
    
    /**
     * 检查存档是否存在
     */
    public boolean saveExists(String saveName) {
        return Files.exists(Paths.get(getSaveFilePath(saveName)));
    }
    
    /**
     * 获取存档文件路径
     */
    private String getSaveFilePath(String saveName) {
        return SAVE_DIR + saveName + ".sav";
    }
    
    /**
     * 保存多玩家存档
     */
    public boolean saveMultiPlayer(MultiPlayerSaveData saveData) {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(getSaveFilePath(saveData.getSaveName())))) {
            
            oos.writeObject(saveData);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 加载多玩家存档
     */
    public MultiPlayerSaveData loadMultiPlayer(String saveName) {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(getSaveFilePath(saveName)))) {
            
            MultiPlayerSaveData saveData = (MultiPlayerSaveData) ois.readObject();
            
            // 确保所有玩家的数据正确
            for (Player player : saveData.getPlayers()) {
                player.getStats().recalcHpMax();
                player.restoreFullHp();
                player.recalcPower();
                if (player.getRoundCount() < 0) {
                    player.setRoundCount(0);
                }
            }
            
            return saveData;
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }
    
    /**
     * 检查多玩家存档是否存在
     */
    public boolean multiPlayerSaveExists(String saveName) {
        return saveExists(saveName);
    }
}