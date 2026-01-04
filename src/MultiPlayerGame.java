package main;

import main.io.GameIO;
import java.util.*;
import java.util.concurrent.*;

/**
 * 多玩家游戏主控制类
 */
public class MultiPlayerGame {
    private static final int MAX_ROUNDS = 100;
    
    private final MultiPlayerManager playerManager;
    private final TrainingService trainingService;
    private final BattleService battleService;
    private final PvPBattleService pvpBattleService;
    private final Arena arena;
    private final NpcFactory npcFactory;
    private final SaveLoadService saveLoadService;
    private final Map<String, GameIO> playerIOs;  // 玩家名 -> GameIO
    private final GameIO defaultIO;  // 默认IO（用于没有绑定IO的玩家）
    private MultiPlayerSaveData saveData;
    private boolean isRunning;
    private boolean gameStarted;
    private CountDownLatch currentRoundLatch;
    private final Map<String, Boolean> playerReadyMap; // 玩家是否完成当前回合
    
    public MultiPlayerGame() {
        this(new HashMap<>());
    }
    
    public MultiPlayerGame(Map<String, GameIO> playerIOs) {
        this.playerIOs = playerIOs != null ? new ConcurrentHashMap<>(playerIOs) : new ConcurrentHashMap<>();
        this.defaultIO = new ConsoleIO();  // 默认使用本地ConsoleIO
        this.saveLoadService = new SaveLoadService();
        this.trainingService = new TrainingService(defaultIO);
        this.battleService = new BattleService();
        this.npcFactory = new NpcFactory();
        this.playerManager = new MultiPlayerManager(MAX_ROUNDS);
        this.pvpBattleService = new PvPBattleService(playerManager);
        this.arena = new Arena(playerManager, playerIOs, defaultIO);
        this.playerReadyMap = new ConcurrentHashMap<>();
        // BattleService和TrainingService会在每次使用时传入玩家的IO
        this.isRunning = true;
        this.gameStarted = false;
    }
    
    /**
     * 获取玩家的IO（如果不存在则使用默认IO）
     */
    private GameIO getPlayerIO(String playerName) {
        GameIO io = playerIOs.get(playerName);
        return io != null ? io : defaultIO;
    }
    
    /**
     * 设置玩家的IO
     */
    public void setPlayerIO(String playerName, GameIO io) {
        if (io != null) {
            playerIOs.put(playerName, io);
        } else {
            playerIOs.remove(playerName);
        }
    }
    
    /**
     * 游戏启动方法（本地测试用）
     */
    public void start() {
        defaultIO.printWelcomeMessage();

        defaultIO.printMessage("欢迎来到多人在线武侠世界！");
        defaultIO.printMessage("当前玩家数量: " + playerManager.getPlayerCount());
        defaultIO.printMessage("最大回合数: " + MAX_ROUNDS);

        // 进入主循环
        mainLoop();
    }
    
    /**
     * 游戏启动方法（网络模式，不需要加载存档）
     */
    public void startNetworkMode() {
        gameStarted = true;
        defaultIO.printMessage("欢迎来到多人在线武侠世界！");
        defaultIO.printMessage("当前玩家数量: " + playerManager.getPlayerCount());
        defaultIO.printMessage("最大回合数: " + MAX_ROUNDS);

        // 进入主循环
        mainLoop();
    }
    
    /**
     * 游戏主循环
     */
    public void mainLoop() {
        while (isRunning && !playerManager.isGameEnded()) {
            // 获取所有人类玩家（不包括AI接管的）
            List<Player> humanPlayers = playerManager.getHumanPlayers();

            // 如果没有人类玩家，游戏结束
            if (humanPlayers.isEmpty()) {
                defaultIO.printMessage("没有活跃玩家，游戏结束。");
                playerManager.setGameEnded(true);
                break;
            }

            // 开始新回合
            startNewRound();

            // 所有人类玩家并行操作（AI接管的玩家不进行回合）
            if (!humanPlayers.isEmpty()) {
                // 创建线程池来并行处理玩家操作
                java.util.concurrent.ExecutorService executor =
                    java.util.concurrent.Executors.newFixedThreadPool(humanPlayers.size());

                // 提交所有玩家的操作任务
                java.util.List<java.util.concurrent.Future<Void>> futures = new java.util.ArrayList<>();
                for (Player player : humanPlayers) {
                    if (!isRunning || playerManager.isGameEnded()) {
                        break;
                    }

                    java.util.concurrent.Future<Void> future = executor.submit(() -> {
                        playerTurn(player);
                        return null;
                    });
                    futures.add(future);
                }

                // 等待所有玩家完成操作
                try {
                    for (int i = 0; i < futures.size(); i++) {
                        java.util.concurrent.Future<Void> future = futures.get(i);
                        Player player = humanPlayers.get(i);
                        try {
                            future.get(); // 等待任务完成
                        } catch (java.util.concurrent.ExecutionException e) {
                            // 获取玩家的IO并发送错误消息
                            GameIO playerIO = getPlayerIO(player.getName());
                            Throwable cause = e.getCause();
                            String errorMsg = cause != null ? cause.getMessage() : e.getMessage();
                            if (errorMsg == null) {
                                errorMsg = "未知错误";
                            }
                            playerIO.printErrorMessage("操作过程中出现错误: " + errorMsg);
                            playerIO.printMessage("请重新选择操作。");
                            // 在服务器端也记录错误用于调试
                            defaultIO.printErrorMessage("玩家 " + player.getName() + " 操作执行出错: " + errorMsg);
                            if (cause != null) {
                                cause.printStackTrace();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    defaultIO.printErrorMessage("等待玩家操作时被中断");
                } finally {
                    executor.shutdown();
                }
            }

            // 进入下一回合
            playerManager.nextRound();

            // 检查游戏是否应该结束
            checkGameEnd();
        }
        
        // 游戏结束，显示排行榜
        showFinalRanking();
    }
    
    /**
     * 开始新回合
     */
    private void startNewRound() {
        int currentRound = playerManager.getCurrentRound();
        // 向所有玩家广播回合开始
        for (Player player : playerManager.getHumanPlayers()) {
            GameIO io = getPlayerIO(player.getName());
            io.printMessage("\n====================================");
            io.printMessage("===== 第 " + (currentRound + 1) + " 回合 =====");
            io.printMessage("====================================");
        }

        // 重置所有人类玩家的准备状态（不包括AI接管的）
        playerReadyMap.clear();
        for (Player player : playerManager.getHumanPlayers()) {
            playerReadyMap.put(player.getName(), false);
            // 清除等待状态，开始新的回合
            GameIO io = getPlayerIO(player.getName());
            if (io instanceof main.server.NetworkConsoleIO) {
                ((main.server.NetworkConsoleIO) io).setWaitingForOthers(false);
            }
        }

        // 并行模式下不再需要CountDownLatch
        currentRoundLatch = null;
    }
    
    /**
     * 玩家回合
     */
    private void playerTurn(Player player) {
        GameIO io = getPlayerIO(player.getName());
        
        // 清除等待状态，玩家开始操作
        if (io instanceof main.server.NetworkConsoleIO) {
            ((main.server.NetworkConsoleIO) io).setWaitingForOthers(false);
        }
        
        try {
            // 检查超时和连接状态
            if (io.isTimedOut() || !io.isConnected()) {
                // 超时或断线，标记为AI接管
                playerManager.setAiControlled(player.getName(), true);
                defaultIO.printMessage(player.getName() + " 超时或断线，已标记为AI接管");
                markPlayerReady(player);
                return;
            }
            
            io.printMessage("\n===== " + player.getName() + " 的回合 =====");
            io.printMessage("当前回合数: " + (playerManager.getCurrentRound() + 1) + "/" + MAX_ROUNDS);
            
            boolean turnComplete = false;
            while (!turnComplete && isRunning && !playerManager.isGameEnded()) {
                try {
                    // 再次检查连接状态
                    if (!io.isConnected() || io.isTimedOut()) {
                        playerManager.setAiControlled(player.getName(), true);
                        defaultIO.printMessage(player.getName() + " 连接断开或超时，已标记为AI接管");
                        markPlayerReady(player);
                        return;
                    }
                    
                    io.printMainMenu();
                    int choice = io.readIntInput("请选择操作: ", 1, 6);
                    
                    // 检查是否超时
                    if (io.isTimedOut()) {
                        playerManager.setAiControlled(player.getName(), true);
                        defaultIO.printMessage(player.getName() + " 输入超时，已标记为AI接管");
                        markPlayerReady(player);
                        return;
                    }
                    
                    switch (choice) {
                        case 1:
                            showStatus(player, io);
                            // 查看状态不算回合，继续选择
                            break;
                        case 2:
                            goTraining(player, io);
                            turnComplete = true;
                            break;
                        case 3:
                            goBattleWithNpc(player, io);
                            turnComplete = true;
                            break;
                        case 4:
                            goBattleWithPlayer(player, io);
                            turnComplete = true;
                            break;
                        case 5:
                            saveAndExit(io);
                            turnComplete = true;
                            return; // 直接返回，不标记完成
                        case 6:
                            exitPlayer(player, io);
                            turnComplete = true;
                            break;
                        default:
                            io.printErrorMessage("无效的选择，请重新输入。");
                    }
                } catch (Exception e) {
                    // 捕获操作中的异常，发送给玩家
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    io.printErrorMessage("操作过程中出现错误: " + errorMsg);
                    io.printMessage("请重新选择操作。");
                    // 在服务器端也记录错误用于调试
                    defaultIO.printErrorMessage("玩家 " + player.getName() + " 操作出错: " + errorMsg);
                    e.printStackTrace();
                    // 继续循环，让玩家重新选择
                }
            }
            
            // 标记玩家完成当前回合（如果还没有退出）
            if (isRunning) {
                markPlayerReady(player);
            }
        } catch (Exception e) {
            // 捕获整个回合的异常
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            io.printErrorMessage("回合执行出错: " + errorMsg);
            io.printMessage("请等待下一回合。");
            defaultIO.printErrorMessage("玩家 " + player.getName() + " 回合执行出错: " + errorMsg);
            e.printStackTrace();
            markPlayerReady(player); // 标记完成，避免卡住
        }
    }
    
    /**
     * 显示玩家状态
     */
    private void showStatus(Player player, GameIO io) {
        io.printPlayerStatus(player);
        io.pressEnterToContinue();
    }
    
    /**
     * 去后山练功
     */
    private void goTraining(Player player, GameIO io) {
        // 使用玩家的IO创建TrainingService
        TrainingService playerTrainingService = new TrainingService(io);
        try {
            playerTrainingService.train(player, () -> {
                // 练功消耗回合，但在这里不增加回合数，因为回合数由回合系统统一管理
                return true;
            });
            player.incrementRound(); // 练功消耗一个回合
            io.printMessage(player.getName() + " 完成了练功，等待其他玩家...");
        } catch (Exception e) {
            io.printErrorMessage("练功过程中出现错误: " + e.getMessage());
            io.printMessage("请重新选择操作。");
        }
    }
    
    /**
     * 和NPC战斗
     */
    private void goBattleWithNpc(Player player, GameIO io) {
        try {
            io.printBattleDifficultyMenu();
            int choice = io.readIntInput("请选择难度: ", 1, 5);
            
            if (choice == 5) {
                return;
            }
            
            Difficulty difficulty = Difficulty.values()[choice - 1];
            io.printMessage("\n===== 下山找人切磋 =====");
            io.printMessage("你选择了" + difficulty.getDisplayName() + "难度...");
            
            // 创建NPC
            Npc npc = npcFactory.createNpc(difficulty, player.getPower());
            io.printMessage("\n" + npc.getDisplayName());
            io.printMessage(npc.getDescription());
            
            io.pressEnterToContinue();
            
            // 使用玩家的IO创建BattleService进行战斗
            BattleService playerBattleService = new BattleService(io);
            BattleResult result = playerBattleService.fight(player, npc);
            
            if (result == BattleResult.WIN) {
                io.printMessage("\n战斗结束！" + player.getName() + " 获胜了！");
            } else {
                io.printMessage("\n战斗结束！" + player.getName() + " 失败了...");
            }
            
            player.incrementRound();
            io.printMessage(player.getName() + " 完成了战斗，等待其他玩家...");
            io.pressEnterToContinue();
        } catch (Exception e) {
            io.printErrorMessage("战斗过程中出现错误: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            io.printMessage("请重新选择操作。");
            e.printStackTrace(); // 在服务器端打印堆栈跟踪用于调试
        }
    }
    
    /**
     * 和玩家战斗（PvP）
     */
    private void goBattleWithPlayer(Player player, GameIO io) {
        try {
            // 使用玩家的IO和所有玩家的IO映射创建PvPBattleService
            PvPBattleService playerPvpService = new PvPBattleService(playerManager, io, playerIOs);
            boolean battleOccurred = playerPvpService.enterArena(player);
            if (battleOccurred) {
                player.incrementRound();
                io.printMessage(player.getName() + " 完成了PvP战斗，等待其他玩家...");
            } else {
                io.printMessage(player.getName() + " 退出了决斗池，未消耗回合。");
            }
            io.pressEnterToContinue();
        } catch (Exception e) {
            io.printErrorMessage("PvP战斗过程中出现错误: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            io.printMessage("请重新选择操作。");
            e.printStackTrace(); // 在服务器端打印堆栈跟踪用于调试
        }
    }
    
    /**
     * 保存并退出
     */
    private void saveAndExit(GameIO io) {
        try {
            if (saveData != null) {
                // 更新存档数据
                saveData.getPlayers().clear();
                saveData.getPlayers().addAll(playerManager.getAllPlayers());
                saveData.setCurrentRound(playerManager.getCurrentRound());
                
                saveLoadService.saveMultiPlayer(saveData);
            }
            io.printSuccessMessage("游戏已保存！");
            isRunning = false;
            io.printMessage("感谢游玩，再见！");
        } catch (Exception e) {
            io.printErrorMessage("保存失败：" + e.getMessage());
        }
    }
    
    /**
     * 玩家退出（NPC接管或删除角色）
     */
    private void exitPlayer(Player player, GameIO io) {
        io.printMessage("\n" + player.getName() + " 选择退出游戏");
        io.printMessage("1. NPC接管（角色由AI控制，不再出现回合）");
        io.printMessage("2. 删除角色");
        
        int choice = io.readIntInput("请选择: ", 1, 2);
        
        if (choice == 1) {
            // NPC接管：将玩家转换为AI控制，之后不再出现回合
            playerManager.setAiControlled(player.getName(), true);
            io.printMessage(player.getName() + " 的角色将由AI接管，之后不再出现回合。");
            markPlayerReady(player);
        } else {
            // 删除角色
            if (io.confirmAction("确定要删除角色 " + player.getName() + " 吗？(y/n): ")) {
                playerManager.removePlayer(player.getName());
                if (saveData != null) {
                    saveData.removePlayer(player.getName());
                }
                io.printMessage("角色 " + player.getName() + " 已删除。");
                markPlayerReady(player);
            }
        }
    }
    
    /**
     * 标记玩家完成当前回合
     */
    private void markPlayerReady(Player player) {
        playerReadyMap.put(player.getName(), true);
        // 标记玩家为等待其他玩家状态（等待期间不计入超时检测）
        GameIO io = getPlayerIO(player.getName());
        if (io instanceof main.server.NetworkConsoleIO) {
            ((main.server.NetworkConsoleIO) io).setWaitingForOthers(true);
        }
    }
    
    /**
     * 等待所有玩家完成当前回合
     */
    private void waitForAllPlayersReady() {
        // 如果没有人类玩家需要等待，直接返回
        List<Player> humanPlayers = playerManager.getHumanPlayers();
        if (humanPlayers.isEmpty()) {
            return;
        }

        try {
            if (currentRoundLatch != null) {
                playerManager.waitForRoundCompletion(currentRoundLatch);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            defaultIO.printErrorMessage("等待回合完成时被中断");
        }
    }
    
    /**
     * 检查游戏是否应该结束
     */
    private void checkGameEnd() {
        int currentRound = playerManager.getCurrentRound();
        
        // 检查是否达到最大回合数
        if (currentRound >= MAX_ROUNDS) {
            playerManager.setGameEnded(true);
            // 向所有玩家广播
            for (Player player : playerManager.getAllPlayers()) {
                GameIO io = getPlayerIO(player.getName());
                io.printMessage("\n====================================");
                io.printMessage("已达到最大回合数 " + MAX_ROUNDS + "，游戏结束！");
                io.printMessage("====================================");
            }
        }
        
        // 检查是否还有存活的人类玩家（AI接管的玩家不算）
        List<Player> humanPlayers = playerManager.getHumanPlayers();
        if (humanPlayers.isEmpty()) {
            playerManager.setGameEnded(true);
            // 向所有玩家广播
            for (Player player : playerManager.getAllPlayers()) {
                GameIO io = getPlayerIO(player.getName());
                io.printMessage("\n====================================");
                io.printMessage("所有人类玩家都已死亡或被AI接管，游戏结束！");
                io.printMessage("====================================");
            }
        }
    }
    
    /**
     * 显示最终排行榜
     */
    private void showFinalRanking() {
        List<Player> ranking = playerManager.getRanking();
        
        // 向所有玩家显示排行榜
        for (Player player : ranking) {
            GameIO io = getPlayerIO(player.getName());
            io.printTitle("游戏结束 - 最终排行榜");
            io.printMessage("\n===== 战力排行榜 =====");
            for (int i = 0; i < ranking.size(); i++) {
                Player p = ranking.get(i);
                String status = p.getStats().isAlive() ? "存活" : "已死亡";
                io.printMessage((i + 1) + ". " + p.getName() + 
                             " - 战力: " + p.getPower() + 
                             " - " + status);
            }
        }
        
        // 保存最终状态
        if (saveData != null) {
            try {
                saveData.getPlayers().clear();
                saveData.getPlayers().addAll(ranking);
                saveData.setCurrentRound(playerManager.getCurrentRound());
                saveLoadService.saveMultiPlayer(saveData);
                defaultIO.printSuccessMessage("最终状态已保存。");
            } catch (Exception e) {
                defaultIO.printErrorMessage("保存最终状态失败：" + e.getMessage());
            }
        }
        
        // 向所有玩家显示结束消息
        for (Player player : ranking) {
            GameIO io = getPlayerIO(player.getName());
            io.printMessage("\n感谢游玩！");
        }
    }
    
    /**
     * 添加玩家
     */
    public void addPlayer(Player player) {
        playerManager.addPlayer(player);
    }
    
    /**
     * 获取玩家管理器
     */
    public MultiPlayerManager getPlayerManager() {
        return playerManager;
    }

    /**
     * 检查游戏是否已开始
     */
    public boolean isGameStarted() {
        return gameStarted;
    }

    /**
     * 设置游戏开始状态
     */
    public void setGameStarted(boolean started) {
        this.gameStarted = started;
    }

    /**
     * 设置玩家为AI控制
     */
    public void setPlayerAiControlled(String playerName, boolean aiControlled) {
        playerManager.setAiControlled(playerName, aiControlled);
        if (aiControlled) {
            defaultIO.println("玩家 " + playerName + " 超时或断线，已标记为AI接管");
        }
    }
}

