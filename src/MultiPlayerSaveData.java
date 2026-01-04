package main;

import java.io.Serializable;
import java.util.*;

/**
 * 多玩家存档数据类
 */
public class MultiPlayerSaveData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String saveName;
    private String password;
    private List<Player> players;
    private int currentRound;
    
    public MultiPlayerSaveData(String saveName, String password) {
        this.saveName = saveName;
        this.password = password;
        this.players = new ArrayList<>();
        this.currentRound = 0;
    }
    
    public String getSaveName() {
        return saveName;
    }
    
    public String getPassword() {
        return password;
    }
    
    public List<Player> getPlayers() {
        return players;
    }
    
    public void addPlayer(Player player) {
        players.add(player);
    }
    
    public void removePlayer(String playerName) {
        players.removeIf(p -> p.getName().equals(playerName));
    }
    
    public Player getPlayer(String playerName) {
        for (Player player : players) {
            if (player.getName().equals(playerName)) {
                return player;
            }
        }
        return null;
    }
    
    public int getCurrentRound() {
        return currentRound;
    }
    
    public void setCurrentRound(int round) {
        this.currentRound = round;
    }
}

