package main;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 多玩家管理器
 * 负责管理多个玩家、回合同步、PvP战斗等
 */
public class MultiPlayerManager {
    private final List<Player> players;
    private final Map<String, Player> playerMap; // 玩家名 -> 玩家对象
    private final Set<String> aiControlledPlayers; // 被AI接管的玩家名集合
    private final ReentrantLock lock;
    private int currentRound;
    private boolean gameEnded;
    private final int maxRounds;
    
    public MultiPlayerManager(int maxRounds) {
        this.players = new ArrayList<>();
        this.playerMap = new HashMap<>();
        this.aiControlledPlayers = new HashSet<>();
        this.lock = new ReentrantLock();
        this.currentRound = 0;
        this.gameEnded = false;
        this.maxRounds = maxRounds;
    }
    
    /**
     * 添加玩家
     */
    public synchronized void addPlayer(Player player) {
        players.add(player);
        playerMap.put(player.getName(), player);
    }
    
    /**
     * 移除玩家
     */
    public synchronized void removePlayer(String playerName) {
        Player player = playerMap.remove(playerName);
        if (player != null) {
            players.remove(player);
        }
    }
    
    /**
     * 获取所有玩家
     */
    public synchronized List<Player> getAllPlayers() {
        return new ArrayList<>(players);
    }
    
    /**
     * 获取玩家（不包括自己，但包括AI接管的玩家，用于决斗池）
     */
    public synchronized List<Player> getOtherPlayers(String currentPlayerName) {
        List<Player> others = new ArrayList<>();
        for (Player player : players) {
            if (!player.getName().equals(currentPlayerName) && player.getStats().isAlive()) {
                others.add(player);
            }
        }
        return others;
    }
    
    /**
     * 获取人类玩家（不包括自己和AI接管的玩家，用于回合操作）
     */
    public synchronized List<Player> getOtherHumanPlayers(String currentPlayerName) {
        List<Player> others = new ArrayList<>();
        for (Player player : players) {
            if (!player.getName().equals(currentPlayerName) 
                && player.getStats().isAlive() 
                && !aiControlledPlayers.contains(player.getName())) {
                others.add(player);
            }
        }
        return others;
    }
    
    /**
     * 标记玩家被AI接管
     */
    public synchronized void setAiControlled(String playerName, boolean aiControlled) {
        if (aiControlled) {
            aiControlledPlayers.add(playerName);
        } else {
            aiControlledPlayers.remove(playerName);
        }
    }
    
    /**
     * 检查玩家是否被AI接管
     */
    public synchronized boolean isAiControlled(String playerName) {
        return aiControlledPlayers.contains(playerName);
    }
    
    /**
     * 获取所有人类玩家（不包括AI接管的）
     */
    public synchronized List<Player> getHumanPlayers() {
        List<Player> humanPlayers = new ArrayList<>();
        for (Player player : players) {
            if (!aiControlledPlayers.contains(player.getName()) && player.getStats().isAlive()) {
                humanPlayers.add(player);
            }
        }
        return humanPlayers;
    }
    
    /**
     * 获取玩家数量
     */
    public synchronized int getPlayerCount() {
        return players.size();
    }
    
    /**
     * 等待所有玩家完成当前回合
     */
    public void waitForRoundCompletion(CountDownLatch latch) throws InterruptedException {
        latch.await();
    }
    
    /**
     * 标记玩家完成当前回合
     */
    public void completeRound(CountDownLatch latch) {
        latch.countDown();
    }
    
    /**
     * 获取当前回合数
     */
    public synchronized int getCurrentRound() {
        return currentRound;
    }
    
    /**
     * 进入下一回合
     */
    public synchronized void nextRound() {
        currentRound++;
    }
    
    /**
     * 检查游戏是否结束
     */
    public synchronized boolean isGameEnded() {
        return gameEnded;
    }
    
    /**
     * 设置游戏结束
     */
    public synchronized void setGameEnded(boolean ended) {
        this.gameEnded = ended;
    }
    
    /**
     * 获取最大回合数
     */
    public int getMaxRounds() {
        return maxRounds;
    }
    
    /**
     * 获取排行榜（按战力排序）
     */
    public synchronized List<Player> getRanking() {
        List<Player> ranking = new ArrayList<>(players);
        ranking.sort((p1, p2) -> Integer.compare(p2.getPower(), p1.getPower()));
        return ranking;
    }
    
    /**
     * 检查是否所有玩家都完成了操作
     */
    public synchronized boolean allPlayersReady() {
        // 这个方法将在Game类中使用，检查所有玩家是否都完成了当前回合的操作
        return true; // 简化实现，实际应该检查每个玩家的状态
    }
}

