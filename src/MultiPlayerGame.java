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
    private final NpcFactory npcFactory;
    private final SaveLoadService saveLoadService;
    private final Map<String, GameIO> playerIOs;  // 玩家名 -> GameIO
    private final GameIO defaultIO;  // 默认IO（用于没有绑定IO的玩家）
    private MultiPlayerSaveData saveData;
    private volatile boolean isRunning;  // 使用 volatile 保证可见性
    private volatile boolean gameStarted;  // 使用 volatile 保证可见性
    private volatile CountDownLatch currentRoundLatch;  // 使用 volatile 保证可见性
    private final Map<String, Boolean> playerReadyMap; // 玩家是否完成当前回合，已经是 ConcurrentHashMap
    private final Object participantsLock = new Object();  // 用于同步决斗池参与者列表的修改
    
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

            // 检查是否有决斗池参与者，如果有，进行决斗
            if (playerManager.hasArenaParticipants()) {
                handleArenaBattle();
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
        List<Player> humanPlayers = playerManager.getHumanPlayers();
        // 向所有玩家广播回合开始
        for (Player player : humanPlayers) {
            GameIO io = getPlayerIO(player.getName());
            io.printMessage("\n====================================");
            io.printMessage("===== 第 " + (currentRound + 1) + " 回合 =====");
            io.printMessage("====================================");
        }

        // 重置所有人类玩家的准备状态（不包括AI接管的）
        playerReadyMap.clear();
        for (Player player : humanPlayers) {
            playerReadyMap.put(player.getName(), false);
            // 清除等待状态，开始新的回合
            GameIO io = getPlayerIO(player.getName());
            if (io instanceof main.server.NetworkConsoleIO) {
                ((main.server.NetworkConsoleIO) io).setWaitingForOthers(false);
            }
        }

        // 按当前人类玩家数量创建新的 CountDownLatch，辅助等待逻辑
        currentRoundLatch = humanPlayers.isEmpty() ? null : new CountDownLatch(humanPlayers.size());
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
            // 检查超时和连接状态（已暂时注释，用于调试）
            // if (io.isTimedOut() || !io.isConnected()) {
            //     // 超时或断线，标记为AI接管
            //     playerManager.setAiControlled(player.getName(), true);
            //     defaultIO.printMessage(player.getName() + " 超时或断线，已标记为AI接管");
            //     markPlayerReady(player);
            //     return;
            // }
            
            // 仅检查连接状态，不检查超时
            if (!io.isConnected()) {
                playerManager.setAiControlled(player.getName(), true);
                defaultIO.printMessage(player.getName() + " 连接断开，已标记为AI接管");
                markPlayerReady(player);
                return;
            }
            
            io.printMessage("\n===== " + player.getName() + " 的回合 =====");
            io.printMessage("当前回合数: " + (playerManager.getCurrentRound() + 1) + "/" + MAX_ROUNDS);
            
            boolean turnComplete = false;
            while (!turnComplete && isRunning && !playerManager.isGameEnded()) {
                try {
                    // 再次检查连接状态（超时检测已暂时注释）
                    // if (!io.isConnected() || io.isTimedOut()) {
                    if (!io.isConnected()) {
                        playerManager.setAiControlled(player.getName(), true);
                        defaultIO.printMessage(player.getName() + " 连接断开，已标记为AI接管");
                        markPlayerReady(player);
                        return;
                    }
                    
                    io.printMainMenu();
                    int choice = io.readIntInput("请选择操作: ", 1, 5);
                    
                    // 检查是否超时（已暂时注释）
                    // if (io.isTimedOut()) {
                    //     playerManager.setAiControlled(player.getName(), true);
                    //     defaultIO.printMessage(player.getName() + " 输入超时，已标记为AI接管");
                    //     markPlayerReady(player);
                    //     return;
                    // }
                    
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
            
            // 标记玩家完成当前回合（如果还没有退出且还没被标记）
            // 注意：goBattleWithPlayer 会提前调用 markPlayerReady，避免重复标记
            if (isRunning && !playerReadyMap.getOrDefault(player.getName(), false)) {
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
            // 玩家选择进入决斗池，标记并等待
            io.printMessage("\n===== 进入决斗池 =====");
            io.printMessage("你已选择进入决斗池，等待其他玩家完成操作...");
            io.printMessage("当所有玩家完成当前回合后，所有选择进入决斗池的玩家将一起进行决斗。");
            
            // 添加到决斗池参与者
            playerManager.addArenaParticipant(player.getName());
            
            // 标记玩家完成回合（但不消耗回合，等决斗结束后再消耗）
            markPlayerReady(player);
            
            // 等待所有玩家完成回合，但如果自己被淘汰/逃跑则立即退出
            synchronized (this) {
                try {
                    while (isRunning && !playerManager.isGameEnded()) {
                        // 检查自己是否还在决斗池中（如果被淘汰或逃跑则不在）
                        boolean stillInArena = playerManager.getArenaParticipants().stream()
                            .anyMatch(p -> p.getName().equals(player.getName()));
                        if (!stillInArena) {
                            // 已被淘汰或逃跑，立即退出等待
                            io.printMessage("你已退出决斗池，回到主菜单。");
                            break;
                        }
                        
                        // 检查是否所有玩家都完成了回合
                        boolean allReady = true;
                        for (Player p : playerManager.getHumanPlayers()) {
                            if (!playerReadyMap.getOrDefault(p.getName(), false)) {
                                allReady = false;
                                break;
                            }
                        }
                        if (allReady) {
                            break; // 所有玩家都完成了，退出等待
                        }
                        this.wait(100); // 等待100ms后再次检查
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // 检查是否还在决斗池中
            boolean stillInArena = playerManager.getArenaParticipants().stream()
                .anyMatch(p -> p.getName().equals(player.getName()));
            
            if (stillInArena) {
                // 仍在决斗池中，说明是正常等待结束，准备进入决斗
                io.printMessage("所有玩家已完成回合，准备进入决斗池...");
            }
            // 如果不在决斗池中，说明已被淘汰或逃跑，已经按过回车，直接返回即可
            
        } catch (Exception e) {
            io.printErrorMessage("进入决斗池时出现错误: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            io.printMessage("请重新选择操作。");
            playerManager.removeArenaParticipant(player.getName());
            e.printStackTrace(); // 在服务器端打印堆栈跟踪用于调试
        }
    }
    
    /**
     * 处理决斗池战斗（并行处理版本）
     * 所有玩家同时选择技能和目标，然后一起计算伤害
     */
    private void handleArenaBattle() {
        List<Player> participants = playerManager.getArenaParticipants();
        
        if (participants.isEmpty()) {
            return;
        }
        
        // 如果只有一个玩家，直接认为他获胜并给予奖励
        if (participants.size() == 1) {
            Player soloPlayer = participants.get(0);
            GameIO io = getPlayerIO(soloPlayer.getName());
            io.printMessage("\n===== 决斗池 =====");
            io.printMessage("只有你一个人进入了决斗池，你自动获胜！");
            
            // 给予胜利奖励（决斗池胜利奖励：3-7点属性 + 15-25战力）
            int rewardPoints = main.RandomUtils.getRandomInt(3, 7);
            soloPlayer.getStats().addRandomAttribute(rewardPoints);
            soloPlayer.recalcPower();
            
            // PvP额外战力奖励
            int powerBonus = main.RandomUtils.getRandomInt(15, 25);
            soloPlayer.setPower(soloPlayer.getPower() + powerBonus);
            
            io.printMessage("获得奖励：" + rewardPoints + " 点属性 + " + powerBonus + " 战力");
            soloPlayer.incrementRound();
            
            // 清空决斗池参与者
            playerManager.clearArenaParticipants();
            return;
        }
        
        // 使用新的并行战斗逻辑
        handleParallelArenaBattle();
    }
    
    /**
     * 并行处理决斗池战斗（新版本）
     */
    /**
     * 处理并行决斗池战斗
     * 所有玩家同时选择行动，然后一起执行
     */
    private void handleParallelArenaBattle() {
        List<Player> participants = playerManager.getArenaParticipants();
        
        // 向所有参与者广播决斗开始
        for (Player participant : participants) {
            GameIO io = getPlayerIO(participant.getName());
            io.printMessage("\n===== 决斗池开始 =====");
            io.printMessage("参加玩家：" + participants.size() + "人");
            for (int i = 0; i < participants.size(); i++) {
                Player p = participants.get(i);
                String aiTag = playerManager.isAiControlled(p.getName()) ? " [AI托管]" : "";
                io.printMessage((i + 1) + ". " + p.getName() + aiTag +
                             " (战力: " + p.getPower() +
                             ", HP: " + p.getStats().getHpCurrent() + "/" + p.getStats().getHpMax() +
                             ", 防御: " + p.getStats().getDef() + ")");
            }
        }
        
        // 进行多轮决斗，直到只剩一个玩家
        while (participants.size() > 1 && isRunning) {
            // 更新参与者列表（移除已死亡的玩家）
            participants = playerManager.getArenaParticipants();
            participants.removeIf(p -> !p.getStats().isAlive());
            
            if (participants.size() <= 1) {
                break;
            }
            
            // 第一步：并行收集所有玩家的行动选择
            Map<String, PvPBattleAction> actions = collectParallelActions(participants);
            
            // 第二步：处理逃跑
            List<Player> escapedPlayers = new ArrayList<>();
            for (PvPBattleAction action : actions.values()) {
                if (action.isEscape()) {
                    escapedPlayers.add(action.getPlayer());
                }
            }
            
            // 移除逃跑的玩家
            for (Player escapedPlayer : escapedPlayers) {
                handlePlayerEscape(escapedPlayer);
                participants.remove(escapedPlayer);
            }
            
            // 检查是否只剩一个玩家
            if (participants.size() <= 1) {
                break;
            }
            
            // 第三步：同时执行所有攻击（并行伤害计算）
            executeParallelAttacks(actions, participants);
            
            // 第四步：移除死亡玩家
            List<Player> defeatedPlayers = new ArrayList<>();
            for (Player p : participants) {
                if (!p.getStats().isAlive()) {
                    defeatedPlayers.add(p);
                }
            }
            
            for (Player defeated : defeatedPlayers) {
                handlePlayerDefeat(defeated);
                participants.remove(defeated);
                playerManager.removeArenaParticipant(defeated.getName());
            }
            
            // 唤醒等待中的玩家线程
            synchronized (this) {
                this.notifyAll();
            }
        }
        
        // 决斗结束，处理结果
        participants = playerManager.getArenaParticipants();
        participants.removeIf(p -> !p.getStats().isAlive());
        
        if (participants.size() == 1) {
            // 有胜利者
            Player winner = participants.get(0);
            handleArenaWinner(winner);
        } else if (participants.isEmpty()) {
            // 没有胜利者（所有人都死了或逃跑了）
            defaultIO.printMessage("决斗池结束，没有胜利者。");
        }
        
        // 清空决斗池参与者
        playerManager.clearArenaParticipants();
        
        // 战斗结束广播
        defaultIO.printMessage("决斗池战斗已结束。");
    }
    
    /**
     * 并行收集所有玩家的行动选择
     */
    private Map<String, PvPBattleAction> collectParallelActions(List<Player> participants) {
        Map<String, PvPBattleAction> actions = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(participants.size());
        
        // 为每个玩家创建一个线程来收集选择
        for (Player player : participants) {
            new Thread(() -> {
                try {
                    PvPBattleAction action = collectPlayerAction(player, participants);
                    if (action != null) {
                        actions.put(player.getName(), action);
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        // 等待所有玩家完成选择
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return actions;
    }
    
    /**
     * 收集单个玩家的行动选择
     */
    private PvPBattleAction collectPlayerAction(Player player, List<Player> participants) {
        GameIO io = getPlayerIO(player.getName());
        boolean isAI = playerManager.isAiControlled(player.getName());
        
        // 检查连接状态
        if (!isAI && (io == null || !io.isConnected())) {
            defaultIO.printMessage(player.getName() + " 连接已断开，标记为AI接管");
            playerManager.setAiControlled(player.getName(), true);
            isAI = true;
        }
        
        try {
            // 第一步：选择目标
            List<Player> targets = new ArrayList<>(participants);
            targets.remove(player);
            targets.removeIf(p -> !p.getStats().isAlive());
            
            if (targets.isEmpty()) {
                return null;
            }
            
            Player target;
            if (isAI) {
                // AI 自动选择目标
                target = targets.get(new java.util.Random().nextInt(targets.size()));
            } else {
                // 真人玩家选择目标
                io.printMessage("\n" + player.getName() + " 请选择攻击目标：");
                for (int i = 0; i < targets.size(); i++) {
                    Player t = targets.get(i);
                    String aiTag = playerManager.isAiControlled(t.getName()) ? " [AI托管]" : "";
                    io.printMessage((i + 1) + ". " + t.getName() + aiTag +
                                 " (战力: " + t.getPower() +
                                 ", HP: " + t.getStats().getHpCurrent() + "/" + t.getStats().getHpMax() +
                                 ", 防御: " + t.getStats().getDef() + ")");
                }
                io.printMessage((targets.size() + 1) + ". 逃跑 (退出决斗池)");
                
                int choice = io.readIntInput("请选择 (1-" + (targets.size() + 1) + "): ", 1, targets.size() + 1);
                
                if (choice == targets.size() + 1) {
                    // 逃跑
                    return new PvPBattleAction(player);
                }
                
                target = targets.get(choice - 1);
            }
            
            // 第二步：选择技能
            SkillType skill;
            if (isAI) {
                // AI 自动选择技能（使用主技能）
                skill = player.getMainStyle();
            } else {
                // 真人玩家选择技能
                io.printMessage("\n" + player.getName() + " 请选择你的招式：");
                List<SkillType> availableSkills = new ArrayList<>();
                int index = 1;
                for (SkillType skillType : SkillType.values()) {
                    if (player.hasSkill(skillType)) {
                        availableSkills.add(skillType);
                        String mainIndicator = (skillType == player.getMainStyle()) ? " (主)" : "";
                        io.printMessage(index + ". " + skillType.getName() + mainIndicator +
                                     " (当前熟练度: " + player.getSkillLevel(skillType) + ")");
                        index++;
                    }
                }
                
                int skillChoice = io.readIntInput("请输入选择 (1-" + availableSkills.size() + "): ", 
                                                 1, availableSkills.size());
                skill = availableSkills.get(skillChoice - 1);
            }
            
            return new PvPBattleAction(player, skill, target);
            
        } catch (Exception e) {
            defaultIO.printErrorMessage(player.getName() + " 选择行动时出错: " + e.getMessage());
            // 出错时标记为AI并自动选择
            playerManager.setAiControlled(player.getName(), true);
            List<Player> targets = new ArrayList<>(participants);
            targets.remove(player);
            targets.removeIf(p -> !p.getStats().isAlive());
            if (!targets.isEmpty()) {
                Player target = targets.get(new java.util.Random().nextInt(targets.size()));
                return new PvPBattleAction(player, player.getMainStyle(), target);
            }
            return null;
        }
    }
    
    /**
     * 并行执行所有攻击
     */
    private void executeParallelAttacks(Map<String, PvPBattleAction> actions, List<Player> participants) {
        // 第一步：计算所有伤害（不立即应用）
        Map<String, Integer> damageMap = new HashMap<>();
        Map<String, List<String>> attackInfoMap = new HashMap<>();
        
        for (PvPBattleAction action : actions.values()) {
            if (action.isEscape()) {
                continue;
            }
            
            Player attacker = action.getPlayer();
            Player defender = action.getTarget();
            SkillType attackerSkill = action.getSkill();
            
            // 检查是否存在互相攻击的情况（技能克制只在互相攻击时生效）
            PvPBattleAction defenderAction = actions.get(defender.getName());
            SkillType defenderSkill = defender.getMainStyle(); // 防御者使用主技能进行防御
            boolean isMutualAttack = false;
            
            if (defenderAction != null && !defenderAction.isEscape()) {
                // 检查防御者是否也在攻击攻击者
                if (defenderAction.getTarget() != null && 
                    defenderAction.getTarget().getName().equals(attacker.getName())) {
                    isMutualAttack = true;
                    defenderSkill = defenderAction.getSkill(); // 如果互相攻击，使用防御者选择的技能
                }
            }
            
            // 计算伤害（使用新的 PvP 伤害计算方法）
            // 如果不是互相攻击，技能克制不生效（传入 null）
            int damage = defender.calculatePvPDamage(attacker, attackerSkill, 
                                                    isMutualAttack ? defenderSkill : null);
            
            // 累加伤害
            damageMap.put(defender.getName(), damageMap.getOrDefault(defender.getName(), 0) + damage);
            
            // 记录攻击信息
            String attackInfo = attacker.getName() + " 使用「" + attackerSkill.getName() + "」攻击 " + 
                              defender.getName() + "，造成 " + damage + " 点伤害";
            if (isMutualAttack && attackerSkill.counters(defenderSkill)) {
                attackInfo += "（技能克制！）";
            }
            attackInfoMap.computeIfAbsent(defender.getName(), k -> new ArrayList<>()).add(attackInfo);
        }
        
        // 第二步：同时应用所有伤害
        for (Map.Entry<String, Integer> entry : damageMap.entrySet()) {
            String playerName = entry.getKey();
            int totalDamage = entry.getValue();
            
            Player player = playerManager.getPlayer(playerName);
            if (player != null) {
                player.takeDamage(totalDamage);
            }
        }
        
        // 第三步：广播战斗结果
        for (Player participant : participants) {
            GameIO io = getPlayerIO(participant.getName());
            if (io == null || !io.isConnected()) {
                continue;
            }
            
            try {
                io.printMessage("\n===== 战斗回合结果 =====");
                
                // 显示所有攻击信息
                for (List<String> infoList : attackInfoMap.values()) {
                    for (String info : infoList) {
                        io.printMessage(info);
                    }
                }
                
                // 显示所有玩家的状态
                io.printMessage("\n当前状态：");
                for (Player p : participants) {
                    io.printMessage(p.getName() + ": HP " + p.getStats().getHpCurrent() + "/" + 
                                  p.getStats().getHpMax() + ", 防御 " + p.getStats().getDef());
                }
            } catch (Exception e) {
                defaultIO.printErrorMessage("向 " + participant.getName() + " 发送战斗结果失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 处理玩家逃跑
     */
    private void handlePlayerEscape(Player player) {
        GameIO io = getPlayerIO(player.getName());
        playerManager.removeArenaParticipant(player.getName());
        
        // 逃跑惩罚：损失5-10点属性（比战败的2-4点重）
        int penaltyPoints = main.RandomUtils.getRandomInt(5, 10);
        player.getStats().addRandomAttribute(-penaltyPoints);
        player.recalcPower();
        
        // 逃跑后也要确保HP大于0，防止无法开始新回合
        if (!player.isAlive()) {
            player.getStats().setHpCurrent(player.getStats().getHpMax() / 2);
        }
        
        try {
            if (io != null && io.isConnected()) {
                io.printMessage("\n" + player.getName() + " 选择了逃跑，退出决斗池。");
                io.printMessage("逃跑惩罚：损失了 " + penaltyPoints + " 点属性");
                io.printMessage("请等待下一回合。");
            }
        } catch (Exception e) {
            defaultIO.printErrorMessage("通知逃跑玩家 " + player.getName() + " 失败: " + e.getMessage());
        }
        
        // 唤醒等待中的玩家线程
        synchronized (this) {
            this.notifyAll();
        }
    }
    
    /**
     * 处理玩家战败
     */
    private void handlePlayerDefeat(Player player) {
        GameIO io = getPlayerIO(player.getName());
        
        // 战败惩罚：损失2-4点属性（比逃跑的5-10点轻）
        int penaltyPoints = main.RandomUtils.getRandomInt(2, 4);
        player.getStats().addRandomAttribute(-penaltyPoints);
        player.recalcPower();
        
        // 战败保底恢复生命，确保能进入下一回合
        player.getStats().setHpCurrent(player.getStats().getHpMax() / 2);
        
        try {
            if (io != null && io.isConnected()) {
                io.printMessage("\n你已被淘汰出局！");
                io.printMessage("战败惩罚：损失了 " + penaltyPoints + " 点属性");
                io.printMessage("请等待下一回合。");
            }
        } catch (Exception e) {
            defaultIO.printErrorMessage("通知战败玩家 " + player.getName() + " 失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理决斗池胜利者
     */
    private void handleArenaWinner(Player winner) {
        GameIO winnerIO = getPlayerIO(winner.getName());
        try {
            winnerIO.printMessage("\n===== 决斗池结束 =====");
            winnerIO.printMessage("恭喜！" + winner.getName() + " 是最后的胜利者！");
            
            // 胜利奖励（决斗池胜利奖励：3-7点属性 + 15-25战力）
            int rewardPoints = main.RandomUtils.getRandomInt(3, 7);
            winner.getStats().addRandomAttribute(rewardPoints);
            winner.recalcPower();
            
            // PvP额外战力奖励
            int powerBonus = main.RandomUtils.getRandomInt(15, 25);
            winner.setPower(winner.getPower() + powerBonus);
            
            winnerIO.printMessage("获得奖励：" + rewardPoints + " 点属性 + " + powerBonus + " 战力");
            winner.incrementRound();
        } catch (Exception e) {
            defaultIO.printErrorMessage("向胜利者发送消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行决斗池中的一次攻击（旧方法，保留用于兼容）
     * @return true 如果攻击者被打败（HP降到0），false 否则
     */
    private boolean performArenaAttack(Player attacker, Player defender, GameIO attackerIO) {
        // 使用PvPBattleService进行战斗
        PvPBattleService pvpService = new PvPBattleService(playerManager, attackerIO, playerIOs);
        
        // 获取防御者的IO
        GameIO defenderIO = getPlayerIO(defender.getName());
        
        // 进行单次攻击（简化版，直接使用PvP战斗逻辑）
        try {
            // 如果攻击者是AI托管，直接使用AI逻辑，不检查连接状态
            boolean attackerIsAI = playerManager.isAiControlled(attacker.getName());
            boolean defenderIsAI = playerManager.isAiControlled(defender.getName());
            
            // 如果攻击者不是AI但连接断开，标记为AI托管
            if (!attackerIsAI && (attackerIO == null || !attackerIO.isConnected() || attackerIO.isTimedOut())) {
                defaultIO.printMessage(attacker.getName() + " 连接已断开，标记为AI接管");
                playerManager.setAiControlled(attacker.getName(), true);
                attackerIsAI = true;
                attackerIO = defaultIO; // 使用默认IO
            }
            
            // 如果防御者不是AI但连接断开，标记为AI托管
            if (!defenderIsAI && (defenderIO == null || !defenderIO.isConnected() || defenderIO.isTimedOut())) {
                defaultIO.printMessage(defender.getName() + " 连接已断开，标记为AI接管");
                playerManager.setAiControlled(defender.getName(), true);
                defenderIsAI = true;
                defenderIO = defaultIO; // 使用默认IO
            }
            
            // 攻击者选择技能（如果是AI，自动选择）
            SkillType attackerSkill = null;
            if (attackerIsAI) {
                // AI托管，自动选择技能（不能逃跑）
                attackerSkill = pvpService.getAiSkillChoice(attacker);
                try {
                    // AI托管玩家使用默认IO，总是可以发送消息
                    if (attackerIO != null) {
                        attackerIO.println(attacker.getName() + " (AI) 选择了 " + attackerSkill.getName());
                    }
                    if (defenderIO != null && defenderIO.isConnected() && !defenderIO.isTimedOut()) {
                        defenderIO.println(attacker.getName() + " (AI) 选择了 " + attackerSkill.getName());
                    }
                } catch (Exception e) {
                    // 发送消息失败，但继续执行
                    defaultIO.printErrorMessage("发送消息失败: " + e.getMessage());
                }
            } else {
                // 真人玩家，选择技能
                try {
                    attackerSkill = pvpService.getPlayerSkillChoice(attacker, attackerIO);
                    if (attackerSkill == null) {
                        // 逃跑
                        return false; // 攻击者未被打败（只是逃跑）
                    }
                } catch (Exception e) {
                    defaultIO.printErrorMessage(attacker.getName() + " 选择技能时出错: " + e.getMessage());
                    // 如果攻击者连接断开，标记为AI接管，然后使用AI选择技能
                    if (!attackerIO.isConnected() || attackerIO.isTimedOut()) {
                        playerManager.setAiControlled(attacker.getName(), true);
                        // 使用AI选择技能（不能逃跑）
                        attackerSkill = pvpService.getAiSkillChoice(attacker);
                        try {
                            if (defenderIO.isConnected() && !defenderIO.isTimedOut()) {
                                defenderIO.println(attacker.getName() + " (AI) 选择了 " + attackerSkill.getName());
                            }
                        } catch (Exception e2) {
                            // 发送消息失败，但继续执行
                            defaultIO.printErrorMessage("发送消息失败: " + e2.getMessage());
                        }
                    } else {
                        // IO错误但不是连接断开，重试一次
                        defaultIO.printMessage("IO错误，重试...");
                        try {
                            Thread.sleep(100);
                            attackerSkill = pvpService.getPlayerSkillChoice(attacker, attackerIO);
                            if (attackerSkill == null) {
                                return false;
                            }
                        } catch (Exception e2) {
                            // 重试失败，标记为AI接管
                            defaultIO.printErrorMessage("重试失败，标记为AI接管: " + e2.getMessage());
                            playerManager.setAiControlled(attacker.getName(), true);
                            attackerSkill = pvpService.getAiSkillChoice(attacker);
                        }
                    }
                }
            }
            
            // 防御者选择技能（如果是AI，自动选择）
            SkillType defenderSkill;
            if (defenderIsAI) {
                defenderSkill = pvpService.getAiSkillChoice(defender);
                try {
                    // AI托管玩家使用默认IO，总是可以发送消息
                    if (attackerIO != null && attackerIO.isConnected() && !attackerIO.isTimedOut()) {
                        attackerIO.println(defender.getName() + " (AI) 选择了 " + defenderSkill.getName());
                    }
                    if (defenderIO != null) {
                        defenderIO.println(defender.getName() + " (AI) 选择了 " + defenderSkill.getName());
                    }
                } catch (Exception e) {
                    // 发送消息失败，但继续执行
                    defaultIO.printErrorMessage("发送消息失败: " + e.getMessage());
                }
            } else {
                // 真人玩家，选择技能
                try {
                    defenderSkill = pvpService.getPlayerSkillChoice(defender, defenderIO);
                    if (defenderSkill == null) {
                        // 防御者逃跑，攻击者获胜
                        return false; // 攻击者未被打败
                    }
                } catch (Exception e) {
                    defaultIO.printErrorMessage(defender.getName() + " 选择技能时出错: " + e.getMessage());
                    // 如果防御者连接断开，标记为AI接管
                    if (!defenderIO.isConnected() || defenderIO.isTimedOut()) {
                        playerManager.setAiControlled(defender.getName(), true);
                        // 使用AI选择技能（不能逃跑）
                        defenderSkill = pvpService.getAiSkillChoice(defender);
                        try {
                            if (attackerIO.isConnected() && !attackerIO.isTimedOut()) {
                                attackerIO.println(defender.getName() + " (AI) 选择了 " + defenderSkill.getName());
                            }
                            if (defenderIO.isConnected() && !defenderIO.isTimedOut()) {
                                defenderIO.println(defender.getName() + " (AI) 选择了 " + defenderSkill.getName());
                            }
                        } catch (Exception e2) {
                            // 发送消息失败，但继续执行
                            defaultIO.printErrorMessage("发送消息失败: " + e2.getMessage());
                        }
                    } else {
                        // IO错误但不是连接断开，重试一次
                        defaultIO.printMessage("IO错误，重试...");
                        try {
                            Thread.sleep(100);
                            defenderSkill = pvpService.getPlayerSkillChoice(defender, defenderIO);
                            if (defenderSkill == null) {
                                return false;
                            }
                        } catch (Exception e2) {
                            // 重试失败，标记为AI接管
                            defaultIO.printErrorMessage("重试失败，标记为AI接管: " + e2.getMessage());
                            playerManager.setAiControlled(defender.getName(), true);
                            defenderSkill = pvpService.getAiSkillChoice(defender);
                            try {
                                if (attackerIO.isConnected() && !attackerIO.isTimedOut()) {
                                    attackerIO.println(defender.getName() + " (AI) 选择了 " + defenderSkill.getName());
                                }
                                if (defenderIO.isConnected() && !defenderIO.isTimedOut()) {
                                    defenderIO.println(defender.getName() + " (AI) 选择了 " + defenderSkill.getName());
                                }
                            } catch (Exception e3) {
                                // 发送消息失败，但继续执行
                                defaultIO.printErrorMessage("发送消息失败: " + e3.getMessage());
                            }
                        }
                    }
                }
            }
            
            // 执行攻击（使用try-catch保护，确保即使一个玩家断开也不影响另一个）
            try {
                pvpService.executePvPAttack(attacker, defender, attackerSkill, attackerIO, defenderIO);
            } catch (Exception e) {
                defaultIO.printErrorMessage("执行攻击时出错: " + e.getMessage());
                // 检查连接状态
                if (!attackerIO.isConnected() || attackerIO.isTimedOut()) {
                    playerManager.setAiControlled(attacker.getName(), true);
                }
                if (!defenderIO.isConnected() || defenderIO.isTimedOut()) {
                    playerManager.setAiControlled(defender.getName(), true);
                }
                // 即使出错也继续，不中断战斗
            }
            
            // 如果防御者还活着，进行反击
            if (defender.isAlive() && attacker.isAlive()) {
                try {
                    pvpService.executePvPAttack(defender, attacker, defenderSkill, defenderIO, attackerIO);
                } catch (Exception e) {
                    defaultIO.printErrorMessage("执行反击时出错: " + e.getMessage());
                    // 检查连接状态
                    if (!attackerIO.isConnected() || attackerIO.isTimedOut()) {
                        playerManager.setAiControlled(attacker.getName(), true);
                    }
                    if (!defenderIO.isConnected() || defenderIO.isTimedOut()) {
                        playerManager.setAiControlled(defender.getName(), true);
                    }
                }
            }
            
            // 显示状态（使用try-catch保护）
            try {
                String status = "\n战斗状态：\n" +
                    attacker.getName() + " HP: " + attacker.getStats().getHpCurrent() + "/" + attacker.getStats().getHpMax() + "\n" +
                    defender.getName() + " HP: " + defender.getStats().getHpCurrent() + "/" + defender.getStats().getHpMax();
                if (attackerIO.isConnected() && !attackerIO.isTimedOut()) {
                    attackerIO.println(status);
                }
                if (defenderIO.isConnected() && !defenderIO.isTimedOut()) {
                    defenderIO.println(status);
                }
            } catch (Exception e) {
                defaultIO.printErrorMessage("显示战斗状态时出错: " + e.getMessage());
            }
            
            // 检查并通知战败方
            if (!defender.getStats().isAlive()) {
                try {
                    if (defenderIO.isConnected() && !defenderIO.isTimedOut()) {
                        defenderIO.printMessage("\n你已被淘汰出局！请等待下一回合。");
                        // 战败保底恢复生命，确保能进入下一回合
                        defender.getStats().setHpCurrent(defender.getStats().getHpMax() / 2); // 恢复一半生命
                    }
                } catch (Exception e) {
                    defaultIO.printErrorMessage("通知战败防御者 " + defender.getName() + " 失败: " + e.getMessage());
                }
                // 从参与者列表中移除防御者
                playerManager.removeArenaParticipant(defender.getName());
                // 唤醒等待中的玩家线程（虽然可能已经没有在等待的了）
                synchronized (this) {
                    this.notifyAll();
                }
            }
            
            // 返回攻击者是否被打败
            return !attacker.getStats().isAlive();
            
        } catch (Exception e) {
            defaultIO.printErrorMessage("决斗过程中出现错误: " + e.getMessage());
            e.printStackTrace();
            // 检查连接状态并标记为AI接管
            if (!attackerIO.isConnected() || attackerIO.isTimedOut()) {
                playerManager.setAiControlled(attacker.getName(), true);
            }
            if (!defenderIO.isConnected() || defenderIO.isTimedOut()) {
                playerManager.setAiControlled(defender.getName(), true);
            }
            // 返回攻击者是否被打败
            return !attacker.getStats().isAlive();
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
        
        // 合并“保存并退出”逻辑：在退出流程中可选保存
        if (saveData != null) {
            String saveAns = io.readInput("是否保存当前进度？(回车默认保存，y/n): ").trim().toLowerCase();
            boolean doSave = saveAns.isEmpty() || saveAns.equals("y") || saveAns.equals("yes");
            if (doSave) {
                try {
                    saveData.getPlayers().clear();
                    saveData.getPlayers().addAll(playerManager.getAllPlayers());
                    saveData.setCurrentRound(playerManager.getCurrentRound());
                    saveLoadService.saveMultiPlayer(saveData);
                    io.printSuccessMessage("游戏已保存！");
                } catch (Exception e) {
                    io.printErrorMessage("保存失败：" + e.getMessage());
                }
            }
        }
        
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
    private synchronized void markPlayerReady(Player player) {
        playerReadyMap.put(player.getName(), true);
        // 标记玩家为等待其他玩家状态（等待期间不计入超时检测）
        GameIO io = getPlayerIO(player.getName());
        if (io instanceof main.server.NetworkConsoleIO) {
            ((main.server.NetworkConsoleIO) io).setWaitingForOthers(true);
        }
        
        // 检查是否所有玩家都完成了回合
        boolean allReady = true;
        for (Player p : playerManager.getHumanPlayers()) {
            if (!playerReadyMap.getOrDefault(p.getName(), false)) {
                allReady = false;
                break;
            }
        }

        // 如果有 CountDownLatch，减少计数
        CountDownLatch latch = currentRoundLatch;
        if (latch != null) {
            latch.countDown();
        }
        
        // 如果所有玩家都完成了，通知等待的线程
        if (allReady) {
            this.notifyAll();
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

