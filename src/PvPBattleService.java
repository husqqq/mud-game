package main;

import java.util.*;
import main.io.GameIO;

/**
 * PvP战斗服务类
 * 处理玩家之间的战斗
 */
public class PvPBattleService {
    private MultiPlayerManager playerManager;
    private GameIO defaultIO;
    private java.util.Map<String, GameIO> playerIOs;

    public PvPBattleService(MultiPlayerManager playerManager, GameIO io) {
        this.playerManager = playerManager;
        this.defaultIO = io;
        this.playerIOs = new java.util.HashMap<>();
    }

    public PvPBattleService(MultiPlayerManager playerManager, GameIO io, java.util.Map<String, GameIO> playerIOs) {
        this.playerManager = playerManager;
        this.defaultIO = io;
        this.playerIOs = playerIOs != null ? new java.util.HashMap<>(playerIOs) : new java.util.HashMap<>();
    }

    // 为了向后兼容，默认使用ConsoleIO
    public PvPBattleService(MultiPlayerManager playerManager) {
        this(playerManager, new ConsoleIO());
    }
    
    /**
     * 获取玩家的IO
     */
    private GameIO getPlayerIO(String playerName) {
        GameIO io = playerIOs.get(playerName);
        return io != null ? io : defaultIO;
    }
    
    /**
     * 向两个玩家都发送消息
     */
    private void broadcastToBoth(Player player1, Player player2, String message) {
        getPlayerIO(player1.getName()).println(message);
        getPlayerIO(player2.getName()).println(message);
    }
    
    /**
     * 进入决斗池，开始PvP战斗
     * @return true 如果进行了战斗，false 如果退出了决斗池
     */
    public boolean enterArena(Player currentPlayer) {
        // 获取当前玩家的IO
        GameIO currentPlayerIO = getPlayerIO(currentPlayer.getName());
        
        // 在显示选项时重新获取玩家列表，确保数据是最新的
        List<Player> availablePlayers = playerManager.getOtherPlayers(currentPlayer.getName());

        if (availablePlayers.isEmpty()) {
            currentPlayerIO.println("当前没有其他玩家可以战斗！");
            return false;
        }

        currentPlayerIO.println("\n===== 决斗池 =====");
        currentPlayerIO.println("当前在决斗池中的玩家：");

        // 显示所有可战斗的玩家（包括AI接管的）
        for (int i = 0; i < availablePlayers.size(); i++) {
            Player player = availablePlayers.get(i);
            String aiTag = playerManager.isAiControlled(player.getName()) ? " [AI托管]" : "";
            currentPlayerIO.println((i + 1) + ". " + player.getName() + aiTag +
                             " (战力: " + player.getPower() +
                             ", HP: " + player.getStats().getHpCurrent() + "/" + player.getStats().getHpMax() + ")");
        }
        currentPlayerIO.println((availablePlayers.size() + 1) + ". 退出决斗池");

        int maxChoice = availablePlayers.size() + 1;
        int choice = currentPlayerIO.readIntInput("请选择攻击对象 (1-" + maxChoice + "): ",
                                           1, maxChoice);

        // 再次检查玩家列表是否发生了变化（并发保护）
        List<Player> currentPlayers = playerManager.getOtherPlayers(currentPlayer.getName());
        if (currentPlayers.size() != availablePlayers.size()) {
            currentPlayerIO.println("玩家列表已发生变化，请重新选择。");
            return enterArena(currentPlayer); // 递归重新进入
        }

        if (choice == maxChoice) {
            currentPlayerIO.println("你退出了决斗池。");
            return false; // 退出决斗池，不消耗回合
        }

        if (choice < 1 || choice > currentPlayers.size()) {
            currentPlayerIO.println("无效选择，请重新选择。");
            return enterArena(currentPlayer); // 递归重新进入
        }

        Player target = null;
        try {
            target = currentPlayers.get(choice - 1);
            if (target == null) {
                currentPlayerIO.println("目标玩家不存在，请重新选择。");
                return enterArena(currentPlayer); // 递归重新进入
            }

            // 检查目标玩家是否还活着
            if (!target.getStats().isAlive()) {
                currentPlayerIO.println("目标玩家已经死亡，请重新选择。");
                return enterArena(currentPlayer); // 递归重新进入
            }
        } catch (IndexOutOfBoundsException e) {
            currentPlayerIO.println("选择错误，请重新选择。调试信息: choice=" + choice + ", size=" + currentPlayers.size());
            return enterArena(currentPlayer); // 递归重新进入
        }

        startPvPBattle(currentPlayer, target, currentPlayerIO);
        return true; // 进行了战斗，消耗回合
    }
    
    /**
     * 开始PvP战斗
     */
    private void startPvPBattle(Player attacker, Player defender, GameIO attackerIO) {
        GameIO defenderIO = getPlayerIO(defender.getName());
        
        // 向两个玩家都发送战斗开始消息
        attackerIO.printTitle("PvP战斗开始！");
        attackerIO.println(attacker.getName() + " 对 " + defender.getName() + " 发起了挑战！");
        defenderIO.printTitle("PvP战斗开始！");
        defenderIO.println(attacker.getName() + " 对 " + defender.getName() + " 发起了挑战！");
        
        attackerIO.waitForEnter();
        defenderIO.waitForEnter();
        
        // 恢复双方血量到满
        attacker.restoreFullHp();
        defender.restoreFullHp();
        
        int round = 1;
        final int MAX_ROUNDS = 50; // PvP战斗最大回合数
        
        while (attacker.isAlive() && defender.isAlive() && round <= MAX_ROUNDS) {
            broadcastToBoth(attacker, defender, "\n===== 第 " + round + " 回合 =====");
            
            // 攻击者选择技能
            SkillType attackerSkill = getPlayerSkillChoice(attacker, attackerIO);
            if (attackerSkill == null) {
                // 逃跑
                handleEscape(attacker, defender, attackerIO, defenderIO);
                return;
            }
            
            // 防御者选择技能
            // 如果防御者是被AI接管的，自动选择技能；否则让玩家选择
            SkillType defenderSkill;
            if (playerManager.isAiControlled(defender.getName())) {
                // AI接管的玩家，自动选择技能（使用NPC的技能选择逻辑）
                defenderSkill = getAiSkillChoice(defender);
                broadcastToBoth(attacker, defender, defender.getName() + " (AI) 选择了 " + defenderSkill.getName());
            } else {
                // 真人玩家，需要选择技能
                defenderSkill = getPlayerSkillChoice(defender, defenderIO);
                if (defenderSkill == null) {
                    // 逃跑
                    handleEscape(defender, attacker, defenderIO, attackerIO);
                    return;
                }
            }
            
            // 确定先手顺序
            Player first, second;
            SkillType firstSkill, secondSkill;
            if (attacker.calcSpeed() >= defender.calcSpeed()) {
                first = attacker;
                second = defender;
                firstSkill = attackerSkill;
                secondSkill = defenderSkill;
            } else {
                first = defender;
                second = attacker;
                firstSkill = defenderSkill;
                secondSkill = attackerSkill;
            }
            
            // 第一回合攻击
            GameIO firstIO = first == attacker ? attackerIO : defenderIO;
            GameIO secondIO = second == attacker ? attackerIO : defenderIO;
            executePvPAttack(first, second, firstSkill, firstIO, secondIO);
            
            // 如果第二个人还活着，进行反击
            if (second.isAlive()) {
                executePvPAttack(second, first, secondSkill, secondIO, firstIO);
            }
            
            // 显示当前状态
            showPvPStatus(attacker, defender, attackerIO, defenderIO);
            
            round++;
            if (round <= MAX_ROUNDS) {
                attackerIO.waitForEnter();
                if (!playerManager.isAiControlled(defender.getName())) {
                    defenderIO.waitForEnter();
                }
            } else {
                broadcastToBoth(attacker, defender, "\n战斗已超过" + MAX_ROUNDS + "回合，强制结束！");
                handlePvPTimeout(attacker, defender, attackerIO, defenderIO);
                return;
            }
        }
        
        // 战斗结束
        handlePvPEnd(attacker, defender, attackerIO, defenderIO);
    }
    
    /**
     * 获取AI选择的招式（用于被AI接管的玩家）
     */
    public SkillType getAiSkillChoice(Player player) {
        // 使用和NPC相同的技能选择逻辑（根据技能熟练度）
        SkillType mainStyle = player.getMainStyle();
        int mainLevel = player.getSkillLevel(mainStyle);
        int saberLevel = player.getSkillLevel(SkillType.SABER);
        int swordLevel = player.getSkillLevel(SkillType.SWORD);
        int fistLevel = player.getSkillLevel(SkillType.FIST);
        
        // 计算总等级（用于权重计算）
        int totalLevel = saberLevel + swordLevel + fistLevel;
        
        // 如果总等级为0，随机选择
        if (totalLevel == 0) {
            SkillType[] skillTypes = SkillType.values();
            return skillTypes[RandomUtils.getRandomInt(0, skillTypes.length - 1)];
        }
        
        // 根据技能等级计算权重，等级越高被选中的概率越大
        int randomValue = RandomUtils.getRandomInt(1, totalLevel);
        
        if (randomValue <= saberLevel) {
            return SkillType.SABER;
        } else if (randomValue <= saberLevel + swordLevel) {
            return SkillType.SWORD;
        } else {
            return SkillType.FIST;
        }
    }
    
    /**
     * 获取玩家选择的招式
     */
    public SkillType getPlayerSkillChoice(Player player, GameIO playerIO) {
        playerIO.println("\n" + player.getName() + " 请选择你的招式：");
        
        // 显示已掌握的技能
        int optionNumber = 1;
        List<SkillType> availableSkills = new ArrayList<>();
        
        if (player.hasSkill(SkillType.SABER)) {
            playerIO.println(optionNumber + ". " + SkillType.SABER.getName() + 
                             " (当前熟练度: " + player.getSkillLevel(SkillType.SABER) + ")");
            availableSkills.add(SkillType.SABER);
            optionNumber++;
        }
        if (player.hasSkill(SkillType.SWORD)) {
            playerIO.println(optionNumber + ". " + SkillType.SWORD.getName() + 
                             " (当前熟练度: " + player.getSkillLevel(SkillType.SWORD) + ")");
            availableSkills.add(SkillType.SWORD);
            optionNumber++;
        }
        if (player.hasSkill(SkillType.FIST)) {
            playerIO.println(optionNumber + ". " + SkillType.FIST.getName() + 
                             " (当前熟练度: " + player.getSkillLevel(SkillType.FIST) + ")");
            availableSkills.add(SkillType.FIST);
            optionNumber++;
        }
        
        playerIO.println(optionNumber + ". 逃跑 (战斗失败)");
        int maxChoice = optionNumber;
        
        int choice = playerIO.readIntInput("请输入选择 (1-" + maxChoice + "): ", 1, maxChoice);
        
        if (choice == maxChoice) {
            return null; // 逃跑
        }
        
        if (choice > 0 && choice <= availableSkills.size()) {
            return availableSkills.get(choice - 1);
        }
        
        return null;
    }
    
    /**
     * 执行PvP攻击
     */
    public void executePvPAttack(Player attacker, Player defender, SkillType skillUsed, GameIO attackerIO, GameIO defenderIO) {
        // 保存原始技能
        Skill originalSkill = attacker.getMainSkill();
        
        // 设置攻击者使用的技能
        SkillType skillType = skillUsed;
        int skillLevel = attacker.getSkillLevel(skillType);
        attacker.setMainSkill(new Skill(skillType, skillLevel, 0));
        
        boolean isCritical = RandomUtils.isSuccess(attacker.calcCritRate());
        boolean isCounter = skillUsed.counters(defender.getMainSkill().getType());
        
        int damage = attacker.attack(defender);
        String skillName = skillUsed.getName();
        
        // 向两个玩家都显示战斗效果（使用try-catch保护，确保即使一个玩家断开也不影响另一个）
        try {
            if (attackerIO.isConnected() && !attackerIO.isTimedOut()) {
                attackerIO.printBattleEffect(
                    attacker.getName(),
                    skillName,
                    defender.getName(),
                    damage,
                    isCritical,
                    isCounter
                );
            }
        } catch (Exception e) {
            // 发送给攻击者的消息失败，但不影响战斗
            System.err.println("向攻击者发送战斗效果失败: " + e.getMessage());
        }
        
        try {
            if (defenderIO.isConnected() && !defenderIO.isTimedOut()) {
                defenderIO.printBattleEffect(
                    attacker.getName(),
                    skillName,
                    defender.getName(),
                    damage,
                    isCritical,
                    isCounter
                );
            }
        } catch (Exception e) {
            // 发送给防御者的消息失败，但不影响战斗
            System.err.println("向防御者发送战斗效果失败: " + e.getMessage());
        }
        
        // 恢复原始技能
        attacker.setMainSkill(originalSkill);
    }
    
    /**
     * 显示PvP战斗状态
     */
    private void showPvPStatus(Player player1, Player player2, GameIO player1IO, GameIO player2IO) {
        String status = "\n战斗状态：\n" +
            player1.getName() + " HP: " + player1.getStats().getHpCurrent() + "/" + player1.getStats().getHpMax() + "\n" +
            player2.getName() + " HP: " + player2.getStats().getHpCurrent() + "/" + player2.getStats().getHpMax();
        player1IO.println(status);
        player2IO.println(status);
    }
    
    /**
     * 处理逃跑
     */
    private void handleEscape(Player escaper, Player opponent, GameIO escaperIO, GameIO opponentIO) {
        String escapeMsg = escaper.getName() + " 选择了逃跑！";
        escaperIO.println(escapeMsg);
        opponentIO.println(escapeMsg);
        
        // 逃跑惩罚：损失属性点（随机减少5-10点）
        int penaltyPoints = RandomUtils.getRandomInt(5, 10);
        escaper.getStats().addRandomAttribute(-penaltyPoints);
        escaper.recalcPower();
        
        String penaltyMsg = "逃跑惩罚：损失了" + penaltyPoints + "点属性";
        String winMsg = opponent.getName() + " 获胜！";
        escaperIO.println(penaltyMsg);
        escaperIO.println(winMsg);
        opponentIO.println(penaltyMsg);
        opponentIO.println(winMsg);
    }
    
    /**
     * 处理PvP超时
     */
    private void handlePvPTimeout(Player player1, Player player2, GameIO player1IO, GameIO player2IO) {
        String resultMsg;
        if (player1.getStats().getHpCurrent() > player2.getStats().getHpCurrent()) {
            resultMsg = "由于回合数限制，根据当前血量判断，" + player1.getName() + " 获胜！";
            applyPvPWinReward(player1);
            applyPvPLosePenalty(player2);
        } else if (player2.getStats().getHpCurrent() > player1.getStats().getHpCurrent()) {
            resultMsg = "由于回合数限制，根据当前血量判断，" + player2.getName() + " 获胜！";
            applyPvPWinReward(player2);
            applyPvPLosePenalty(player1);
        } else {
            resultMsg = "由于回合数限制，战斗以平局结束，双方都受到惩罚。";
            applyPvPLosePenalty(player1);
            applyPvPLosePenalty(player2);
        }
        broadcastToBoth(player1, player2, resultMsg);
    }
    
    /**
     * 处理PvP战斗结束
     */
    private void handlePvPEnd(Player player1, Player player2, GameIO player1IO, GameIO player2IO) {
        player1IO.printSeparator();
        player2IO.printSeparator();
        
        String resultMsg;
        if (player1.isAlive()) {
            resultMsg = "战斗结束！" + player1.getName() + " 获胜！";
            applyPvPWinReward(player1);
            applyPvPLosePenalty(player2);
        } else {
            resultMsg = "战斗结束！" + player2.getName() + " 获胜！";
            applyPvPWinReward(player2);
            applyPvPLosePenalty(player1);
        }
        player1IO.println(resultMsg);
        player2IO.println(resultMsg);
    }
    
    /**
     * 应用PvP胜利奖励
     */
    private void applyPvPWinReward(Player winner) {
        // PvP胜利奖励：随机增加1-2点属性
        // 提高奖励：随机增加3-5点属性
        int randomPoints = RandomUtils.getRandomInt(3, 5);
        winner.getStats().addRandomAttribute(randomPoints);
        winner.recalcPower();
        getPlayerIO(winner.getName()).println(winner.getName() + " 获得了 " + randomPoints + " 点属性奖励！");
    }
    
    /**
     * 应用PvP失败惩罚
     */
    private void applyPvPLosePenalty(Player loser) {
        // 失败惩罚：损失属性点（随机减少5-10点）
        int penaltyPoints = RandomUtils.getRandomInt(5, 10);
        loser.getStats().addRandomAttribute(-penaltyPoints);
        loser.recalcPower();
        getPlayerIO(loser.getName()).println(loser.getName() + " 失败惩罚：损失了" + penaltyPoints + "点属性");
    }
}

