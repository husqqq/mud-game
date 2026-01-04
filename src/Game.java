package main;

import java.util.Scanner;

/**
 * 单人游戏主控制类
 */
public class Game {
    private final ConsoleIO consoleIO;
    private final SaveLoadService saveLoadService;
    private final TrainingService trainingService;
    private final BattleService battleService;
    private final PvPBattleService pvpBattleService;
    private final NpcFactory npcFactory;
    private Player player;
    private boolean isRunning;

    public Game() {
        this.consoleIO = new ConsoleIO();
        this.saveLoadService = new SaveLoadService();
        this.trainingService = new TrainingService(consoleIO);
        this.battleService = new BattleService(consoleIO);
        this.npcFactory = new NpcFactory();
        this.pvpBattleService = new PvPBattleService(new MultiPlayerManager(100), consoleIO);
        this.isRunning = true;
    }

    /**
     * 游戏启动方法
     */
    public void start() {
        consoleIO.printWelcomeMessage();

        // 创建或加载角色
        player = createOrLoadPlayer();

        if (player == null) {
            consoleIO.printErrorMessage("角色创建失败，游戏退出。");
            return;
        }

        consoleIO.printMessage("欢迎来到武侠世界，" + player.getName() + "！");
        consoleIO.printMessage("游戏目标：通过不断修炼和战斗，提升战力，最终成为武林至尊！");
        consoleIO.printMessage("注意：游戏没有固定的胜利条件，你可以无限进行下去。");
        consoleIO.waitForEnter();

        // 进入主循环
        mainLoop();
    }

    /**
     * 创建或加载角色
     */
    private Player createOrLoadPlayer() {
        consoleIO.printTitle("角色选择");

        while (true) {
            consoleIO.println("请选择：");
            consoleIO.println("1. 创建新角色");
            consoleIO.println("2. 加载已有角色");
            consoleIO.println("3. 退出游戏");

            int choice = consoleIO.readChoice(3, "请选择 (1-3): ");

            switch (choice) {
                case 1:
                    return createNewPlayer();
                case 2:
                    Player loadedPlayer = loadPlayer();
                    if (loadedPlayer != null) {
                        return loadedPlayer;
                    }
                    consoleIO.println("加载失败，请重试。");
                    break;
                case 3:
                    return null;
                default:
                    consoleIO.printErrorMessage("无效选择");
            }
        }
    }

    /**
     * 创建新角色
     */
    private Player createNewPlayer() {
        consoleIO.printTitle("创建新角色");

        // 输入角色名
        consoleIO.println("请输入角色名：");
        String name = consoleIO.readLine().trim();
        if (name.isEmpty()) {
            name = "侠客";
        }

        // 分配属性点
        consoleIO.println("\n你有5点属性可以自由分配，每项基础为10点。");
        consoleIO.println("属性包括：力量(STR)、敏捷(AGI)、体质(CON)、智力(INT)、运气(LUK)");

        Stats stats = allocateAttributes();

        // 选择主用流派
        consoleIO.println("\n请选择主用流派：");
        consoleIO.println("1. 刀法 (SABER)");
        consoleIO.println("2. 剑法 (SWORD)");
        consoleIO.println("3. 拳法 (FIST)");

        int choice = consoleIO.readChoice(3, "请选择 (1-3): ");
        SkillType mainStyle = SkillType.values()[choice - 1];

        // 创建玩家
        Player newPlayer = new Player(name, "SINGLE_" + name, stats, mainStyle);

        consoleIO.println("\n角色创建成功！");
        consoleIO.println("角色名：" + newPlayer.getName());
        consoleIO.println("主用流派：" + mainStyle.getName());
        consoleIO.println("初始战力：" + newPlayer.getPower());
        consoleIO.waitForEnter();

        return newPlayer;
    }

    /**
     * 加载角色
     */
    private Player loadPlayer() {
        consoleIO.println("请输入存档名：");
        String saveName = consoleIO.readLine().trim();

        if (saveName.isEmpty()) {
            return null;
        }

        return saveLoadService.load(saveName);
    }

    /**
     * 分配属性点
     */
    private Stats allocateAttributes() {
        int baseValue = 10;
        int totalPoints = 5;
        int remainingPoints = totalPoints;

        int str = baseValue;
        int agi = baseValue;
        int con = baseValue;
        int intel = baseValue;
        int luk = baseValue;

        consoleIO.println("当前属性状态：");
        showAttributeStatus(str, agi, con, intel, luk, remainingPoints);

        while (remainingPoints > 0) {
            consoleIO.println("\n请输入要加点的属性名 (str/agi/con/int/luk)：");
            String input = consoleIO.readLine().toLowerCase().trim();

            if (input.equals("done")) {
                consoleIO.println("还有 " + remainingPoints + " 点属性未分配，请先分配完所有属性点！");
                continue;
            }

            consoleIO.println("要增加多少点？(1-" + remainingPoints + ")");
            int pointsToAdd = consoleIO.readIntInput("请输入: ", 1, remainingPoints);

            switch (input) {
                case "str":
                    str += pointsToAdd;
                    break;
                case "agi":
                    agi += pointsToAdd;
                    break;
                case "con":
                    con += pointsToAdd;
                    break;
                case "int":
                case "intel":
                    intel += pointsToAdd;
                    break;
                case "luk":
                    luk += pointsToAdd;
                    break;
                default:
                    consoleIO.println("无效的属性名，请输入 str/agi/con/int/luk 之一。");
                    continue;
            }

            remainingPoints -= pointsToAdd;
            showAttributeStatus(str, agi, con, intel, luk, remainingPoints);
        }

        return new Stats(str, agi, con, intel, luk);
    }

    /**
     * 显示属性状态
     */
    private void showAttributeStatus(int str, int agi, int con, int intel, int luk, int remaining) {
        consoleIO.println("STR = " + str + " (力量)");
        consoleIO.println("AGI = " + agi + " (敏捷)");
        consoleIO.println("CON = " + con + " (体质)");
        consoleIO.println("INT = " + intel + " (智力)");
        consoleIO.println("LUK = " + luk + " (运气)");
        consoleIO.println("剩余分配点数：" + remaining);
    }

    /**
     * 游戏主循环
     */
    private void mainLoop() {
        while (isRunning && player.getStats().isAlive()) {
            consoleIO.printMainMenu();
            int choice = consoleIO.readChoice(6, "请选择操作 (1-6): ");

            switch (choice) {
                case 1:
                    showStatus();
                    break;
                case 2:
                    goTraining();
                    break;
                case 3:
                    goBattle();
                    break;
                case 4:
                    goPvPBattle();
                    break;
                case 5:
                    saveAndExit();
                    return;
                case 6:
                    exitGame();
                    return;
                default:
                    consoleIO.printErrorMessage("无效的选择");
            }
        }

        if (!player.getStats().isAlive()) {
            consoleIO.println("\n" + player.getName() + " 已经死亡，游戏结束！");
        }
    }

    /**
     * 显示角色状态
     */
    private void showStatus() {
        consoleIO.printPlayerStatus(player);
        consoleIO.waitForEnter();
    }

    /**
     * 去后山练功
     */
    private void goTraining() {
        trainingService.train(player, () -> {
            player.incrementRound();
            return true;
        });
        consoleIO.println(player.getName() + " 完成了练功。");
        consoleIO.waitForEnter();
    }

    /**
     * 找人切磋（NPC战斗）
     */
    private void goBattle() {
        consoleIO.printBattleDifficultyMenu();
        int choice = consoleIO.readChoice(5, "请选择难度 (1-5): ");

        if (choice == 5) {
            return;
        }

        Difficulty difficulty = Difficulty.values()[choice - 1];
        consoleIO.println("\n你选择了" + difficulty.getDisplayName() + "难度...");

        // 创建NPC
        Npc npc = npcFactory.createNpc(difficulty, player.getPower());
        consoleIO.println("\n" + npc.getDisplayName());
        consoleIO.println(npc.getDescription());
        consoleIO.waitForEnter();

        // 进行战斗
        BattleResult result = battleService.fight(player, npc);

        if (result == BattleResult.WIN) {
            consoleIO.println("\n战斗胜利！" + player.getName() + " 获得了一些经验。");
        } else {
            consoleIO.println("\n战斗失败..." + player.getName() + " 损失了一些属性。");
        }

        player.incrementRound();
        consoleIO.println(player.getName() + " 完成了战斗。");
        consoleIO.waitForEnter();
    }

    /**
     * PvP战斗（单人模式下的模拟）
     */
    private void goPvPBattle() {
        consoleIO.println("单人模式下不支持玩家对战，请选择其他模式进行多人游戏。");
        consoleIO.waitForEnter();
    }

    /**
     * 保存并退出
     */
    private void saveAndExit() {
        try {
            saveLoadService.save(player);
            consoleIO.println("角色已保存！");
        } catch (Exception e) {
            consoleIO.printErrorMessage("保存失败：" + e.getMessage());
        }
        consoleIO.println("感谢游玩，再见！");
    }

    /**
     * 退出游戏
     */
    private void exitGame() {
        consoleIO.println("感谢游玩，再见！");
    }
}
