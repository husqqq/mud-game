package main;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 多玩家管理器
 * 负责管理多个玩家、回合同步、PvP战斗等
 */
public class MultiPlayerManager {
    private final List<Player> players;  // 使用 CopyOnWriteArrayList 或同步访问
    private final Map<String, Player> playerMap; // 玩家名 -> 玩家对象，使用 ConcurrentHashMap
    private final Set<String> aiControlledPlayers; // 被AI接管的玩家名集合，使用 ConcurrentHashMap.newKeySet()
    private final Set<String> arenaParticipants; // 选择进入决斗池的玩家名集合，使用 ConcurrentHashMap.newKeySet()
    private final ReentrantReadWriteLock rwLock;
    private volatile int currentRound;  // 使用 volatile 保证可见性
    private volatile boolean gameEnded;  // 使用 volatile 保证可见性
    private final int maxRounds;
    
    public MultiPlayerManager(int maxRounds) {
        this.players = new CopyOnWriteArrayList<>();  // 线程安全的列表
        this.playerMap = new ConcurrentHashMap<>();  // 线程安全的映射
        this.aiControlledPlayers = ConcurrentHashMap.newKeySet();  // 线程安全的集合
        this.arenaParticipants = ConcurrentHashMap.newKeySet();  // 线程安全的集合
        this.rwLock = new ReentrantReadWriteLock();
        this.currentRound = 0;
        this.gameEnded = false;
        this.maxRounds = maxRounds;
    }
    
    /**
     * 添加玩家
     */
    public void addPlayer(Player player) {
        rwLock.writeLock().lock();
        try {
            players.add(player);
            playerMap.put(player.getName(), player);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * 移除玩家
     */
    public void removePlayer(String playerName) {
        rwLock.writeLock().lock();
        try {
            Player player = playerMap.remove(playerName);
            if (player != null) {
                players.remove(player);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取所有玩家
     */
    public List<Player> getAllPlayers() {
        rwLock.readLock().lock();
        try {
            return new ArrayList<>(players);
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 获取玩家（不包括自己，但包括AI接管的玩家，用于决斗池）
     */
    public List<Player> getOtherPlayers(String currentPlayerName) {
        rwLock.readLock().lock();
        try {
            List<Player> others = new ArrayList<>();
            for (Player player : players) {
                if (!player.getName().equals(currentPlayerName) && player.getStats().isAlive()) {
                    others.add(player);
                }
            }
            return others;
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 获取人类玩家（不包括自己和AI接管的玩家，用于回合操作）
     */
    public List<Player> getOtherHumanPlayers(String currentPlayerName) {
        rwLock.readLock().lock();
        try {
            List<Player> others = new ArrayList<>();
            for (Player player : players) {
                if (!player.getName().equals(currentPlayerName) 
                    && player.getStats().isAlive() 
                    && !aiControlledPlayers.contains(player.getName())) {
                    others.add(player);
                }
            }
            return others;
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 标记玩家被AI接管
     */
    public void setAiControlled(String playerName, boolean aiControlled) {
        if (aiControlled) {
            aiControlledPlayers.add(playerName);
        } else {
            aiControlledPlayers.remove(playerName);
        }
    }
    
    /**
     * 检查玩家是否被AI接管
     */
    public boolean isAiControlled(String playerName) {
        return aiControlledPlayers.contains(playerName);
    }
    
    /**
     * 获取所有人类玩家（不包括AI接管的）
     */
    public List<Player> getHumanPlayers() {
        rwLock.readLock().lock();
        try {
            List<Player> humanPlayers = new ArrayList<>();
            for (Player player : players) {
                if (!aiControlledPlayers.contains(player.getName()) && player.getStats().isAlive()) {
                    humanPlayers.add(player);
                }
            }
            return humanPlayers;
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 获取玩家数量
     */
    public int getPlayerCount() {
        rwLock.readLock().lock();
        try {
            return players.size();
        } finally {
            rwLock.readLock().unlock();
        }
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
    public int getCurrentRound() {
        return currentRound;
    }
    
    /**
     * 进入下一回合
     */
    public void nextRound() {
        currentRound++;
    }
    
    /**
     * 检查游戏是否结束
     */
    public boolean isGameEnded() {
        return gameEnded;
    }
    
    /**
     * 设置游戏结束
     */
    public void setGameEnded(boolean ended) {
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
    public List<Player> getRanking() {
        rwLock.readLock().lock();
        try {
            List<Player> ranking = new ArrayList<>(players);
            ranking.sort((p1, p2) -> Integer.compare(p2.getPower(), p1.getPower()));
            return ranking;
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 检查是否所有玩家都完成了操作
     */
    public boolean allPlayersReady() {
        // 这个方法将在Game类中使用，检查所有玩家是否都完成了当前回合的操作
        return true; // 简化实现，实际应该检查每个玩家的状态
    }
    
    /**
     * 添加玩家到决斗池
     */
    public void addArenaParticipant(String playerName) {
        arenaParticipants.add(playerName);
    }
    
    /**
     * 移除玩家从决斗池
     */
    public void removeArenaParticipant(String playerName) {
        arenaParticipants.remove(playerName);
    }
    
    /**
     * 获取所有决斗池参与者
     */
    public List<Player> getArenaParticipants() {
        rwLock.readLock().lock();
        try {
            List<Player> participants = new ArrayList<>();
            for (String playerName : arenaParticipants) {
                Player player = playerMap.get(playerName);
                if (player != null && player.getStats().isAlive()) {
                    participants.add(player);
                }
            }
            return participants;
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 清空决斗池参与者
     */
    public void clearArenaParticipants() {
        arenaParticipants.clear();
    }
    
    /**
     * 检查是否有决斗池参与者
     */
    public boolean hasArenaParticipants() {
        return !arenaParticipants.isEmpty();
    }
}

