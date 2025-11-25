package main;// File: Game.java

/**
 * 游戏主控制类
 * 负责整体游戏流程的控制
 */
public class Game {
    // 称霸武林门槛战力
    private static final int DOMINANCE_THRESHOLD = 1200;
    // 最大回合数限制
    private static final int MAX_ROUNDS = 100;
    
    private Player currentPlayer;
    private final LoginService loginService;
    private final TrainingService trainingService;
    private final BattleService battleService;
    private final NpcFactory npcFactory;
    private final SaveLoadService saveLoadService;
    private final ConsoleIO consoleIO;
    private boolean isRunning;

    public Game() {
        this.consoleIO = new ConsoleIO();
        this.saveLoadService = new SaveLoadService();
        this.loginService = new LoginService(saveLoadService);
        this.trainingService = new TrainingService();
        this.battleService = new BattleService();
        this.npcFactory = new NpcFactory();
        this.isRunning = true;
    }

    /**
     * 游戏启动方法
     */
    public void start() {
        consoleIO.printWelcomeMessage();
        
        // 登录或注册
        currentPlayer = loginService.loginOrRegister();
        if (currentPlayer == null) {
            consoleIO.printErrorMessage("登录失败，游戏退出。");
            return;
        }
        
        consoleIO.printMessage("欢迎回来，" + currentPlayer.getName() + "！");
        
        // 如果是新角色，回合数应该已经是0；如果是加载的存档，确保回合数已正确加载
        // 显示回合数信息
        consoleIO.printMessage("当前回合数: " + currentPlayer.getRoundCount() + "/" + MAX_ROUNDS);
        consoleIO.printMessage("目标：在" + MAX_ROUNDS + "回合内达到战力" + DOMINANCE_THRESHOLD);
        
        // 进入主循环
        mainLoop();
    }

    /**
     * 游戏主循环
     */
    public void mainLoop() {
        while (isRunning) {
            // 显示当前回合数
            consoleIO.printMessage("\n===== 当前回合数: " + currentPlayer.getRoundCount() + "/" + MAX_ROUNDS + " =====");
            
            // 检查是否已经称霸武林
            if (currentPlayer.getPower() >= DOMINANCE_THRESHOLD) {
                consoleIO.printMessage("你已称霸武林，现在只能选择退出游戏！");
                consoleIO.printMessage("1. 保存并退出");
                consoleIO.printMessage("2. 直接退出");
                int choice = consoleIO.readIntInput("请选择操作: ", 1, 2);
                
                switch (choice) {
                    case 1:
                        saveAndExit();
                        break;
                    case 2:
                        exitWithoutSaving();
                        break;
                    default:
                        consoleIO.printErrorMessage("无效的选择，请重新输入。");
                }
            } else {
                consoleIO.printMainMenu();
                int choice = consoleIO.readIntInput("请选择操作: ", 1, 6);
                
                switch (choice) {
                    case 1:
                        showStatus();
                        // 查看状态不算回合
                        break;
                    case 2:
                        goTraining();
                        break;
                    case 3:
                        goBattle();
                        // 战斗算一个回合
                        currentPlayer.incrementRound();
                        // 立即检查是否超过回合限制
                        if (checkRoundLimit()) {
                            break; // 如果已经失败，跳出循环
                        }
                        break;
                    case 4:
                        goTrainingSweep();
                        break;
                    case 5:
                        saveAndExit();
                        break;
                    case 6:
                        exitWithoutSaving();
                        break;
                    default:
                        consoleIO.printErrorMessage("无效的选择，请重新输入。");
                }
            }
            
            // 每次操作后检查是否称霸武林（只有在未超过回合限制时）
            if (isRunning) {
                checkDominance();
            }
        }
    }

    /**
     * 显示玩家状态
     */
    public void showStatus() {
        consoleIO.printPlayerStatus(currentPlayer);
        consoleIO.pressEnterToContinue();
    }

    /**
     * 去后山练功
     */
    public void goTraining() {
        trainingService.train(currentPlayer, this::consumeTrainingRound);
    }
    
    /**
     * 一键扫荡练功
     */
    public void goTrainingSweep() {
        consoleIO.printMessage("\n===== 一键扫荡练功 =====");
        SkillType selectedSkill = promptSkillSelection();
        if (selectedSkill == null) {
            return;
        }
        
        int remainingRounds = MAX_ROUNDS - currentPlayer.getRoundCount();
        if (remainingRounds <= 0) {
            consoleIO.printErrorMessage("没有剩余回合，无法继续练功。");
            return;
        }
        
        int sweepRounds = consoleIO.readIntInput("请输入扫荡的回合数 (1-" + remainingRounds + "): ", 1, remainingRounds);
        trainingService.sweepTrain(currentPlayer, selectedSkill, sweepRounds, this::consumeTrainingRound);
    }
    
    private SkillType promptSkillSelection() {
        consoleIO.printMessage("请选择要修炼的武学：");
        consoleIO.printMessage("1. 刀法");
        consoleIO.printMessage("2. 剑法");
        consoleIO.printMessage("3. 拳法");
        consoleIO.printMessage("4. 返回");
        
        int choice = consoleIO.readIntInput("请输入选择: ", 1, 4);
        return switch (choice) {
            case 1 -> SkillType.SABER;
            case 2 -> SkillType.SWORD;
            case 3 -> SkillType.FIST;
            default -> null;
        };
    }

    /**
     * 下山找人切磋（战斗）
     */
    public void goBattle() {
        consoleIO.printBattleDifficultyMenu();
        int choice = consoleIO.readIntInput("请选择难度: ", 1, 5);
        
        if (choice == 5) {
            return;
        }
        
        Difficulty difficulty = Difficulty.values()[choice - 1];
        consoleIO.printMessage("\n===== 下山找人切磋 =====");
        consoleIO.printMessage("你选择了" + difficulty.getDisplayName() + "难度...");
        
        // 询问是否启用自动战斗
        boolean autoBattle = consoleIO.confirmAction("是否启用自动战斗模式？(y/n): ");
        battleService.setAutoBattle(autoBattle);
        
        // 创建NPC
        Npc npc = npcFactory.createNpc(difficulty, currentPlayer.getPower());
        consoleIO.printMessage("\n" + npc.getDisplayName());
        consoleIO.printMessage(npc.getDescription());
        
        if (!autoBattle) {
            consoleIO.pressEnterToContinue();
        }
        
        // 进行战斗
        BattleResult result = battleService.fight(currentPlayer, npc);
        
        if (result == BattleResult.WIN) {
            consoleIO.printMessage("\n战斗结束！你获胜了！");
            
            // 战斗奖励已经在BattleService中处理过了
            consoleIO.printMessage("战斗奖励已获得！");
        } else {
            consoleIO.printMessage("\n战斗结束！你失败了...");
        }
        
        if (!autoBattle) {
            consoleIO.pressEnterToContinue();
        }
    }

    /**
     * 保存并退出
     */
    public void saveAndExit() {
        try {
            saveLoadService.save(currentPlayer);
            consoleIO.printSuccessMessage("游戏已保存！");
            isRunning = false;
            consoleIO.printMessage("感谢游玩，再见！");
        } catch (Exception e) {
            consoleIO.printErrorMessage("保存失败：" + e.getMessage());
        }
    }

    /**
     * 直接退出（不保存）
     */
    public void exitWithoutSaving() {
        if (consoleIO.confirmAction("确定要直接退出吗？进度将不会保存！(y/n): ")) {
            isRunning = false;
            consoleIO.printMessage("感谢游玩，再见！");
        }
    }

    /**
     * 检查回合数限制
     * @return true 如果游戏已结束（超过回合限制），false 如果游戏继续
     */
    public boolean checkRoundLimit() {
        if (currentPlayer.getRoundCount() > MAX_ROUNDS && isRunning) {
            consoleIO.printMessage("\n====================================");
            consoleIO.printMessage("回合数已超过限制！");
            consoleIO.printMessage("当前回合数：" + currentPlayer.getRoundCount() + "（超过" + MAX_ROUNDS + "回合限制）");
            consoleIO.printMessage("当前战力：" + currentPlayer.getPower());
            consoleIO.printMessage("目标战力：" + DOMINANCE_THRESHOLD);
            
            // 超过回合数就算失败，即使已经达到目标战力
            consoleIO.printMessage("很遗憾，你未能在" + MAX_ROUNDS + "回合内达到目标战力。");
            consoleIO.printMessage("—— 【挑战失败】结局达成 —— 游戏结束。");
            
            // 自动保存最终状态
            try {
                saveLoadService.save(currentPlayer);
                consoleIO.printSuccessMessage("最终状态已自动保存。");
            } catch (Exception e) {
                // 不显示保存失败信息，避免破坏结局体验
            }
            
            isRunning = false;
            return true; // 返回true表示游戏已结束
        }
        return false; // 返回false表示游戏继续
    }
    
    /**
     * 检查是否称霸武林（必须在100回合内达到）
     */
    public void checkDominance() {
        // 只有在100回合内达到1200战力才算成功
        if (currentPlayer.getPower() >= DOMINANCE_THRESHOLD && 
            currentPlayer.getRoundCount() <= MAX_ROUNDS && 
            isRunning) {
            consoleIO.printMessage("\n====================================");
            consoleIO.printMessage("当前战力：" + currentPlayer.getPower());
            consoleIO.printMessage("当前回合数：" + currentPlayer.getRoundCount() + "/" + MAX_ROUNDS);
            consoleIO.printMessage("恭喜！你在" + MAX_ROUNDS + "回合内达到了目标战力！");
            consoleIO.printMessage("你的名号已经响彻整个江湖，武林中人无不闻风丧胆！");
            consoleIO.printMessage("—— 【称霸武林】结局达成 —— 游戏结束。");
            
            // 自动保存最终状态
            try {
                saveLoadService.save(currentPlayer);
                consoleIO.printSuccessMessage("最终状态已自动保存。");
            } catch (Exception e) {
                // 不显示保存失败信息，避免破坏结局体验
            }
            
            isRunning = false;
        }
    }

    /**
     * 练功时消耗回合的回调
     * @return true 表示可以继续练功，false 表示回合耗尽需要中断
     */
    private boolean consumeTrainingRound() {
        if (!isRunning) {
            return false;
        }
        currentPlayer.incrementRound();
        // 如果超过回合限制将返回false，阻止继续练功
        return !checkRoundLimit();
    }
}