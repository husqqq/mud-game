package main;// File: BattleService.java

import main.io.GameIO;

/**
 * 战斗服务类
 */
public class BattleService {
    private final GameIO io;

    public BattleService(GameIO io) {
        this.io = io;
    }

    // 为了向后兼容，默认使用ConsoleIO
    public BattleService() {
        this(new main.ConsoleIO());
    }
    
    /**
     * 进行一场战斗
     */
    public BattleResult fight(Player player, Npc npc) {
        // 初始化战斗状态（只恢复NPC血量，玩家保持当前血量）
        npc.restoreFullHp();
        
        io.printTitle("战斗开始！");
        io.println("你遇到了 " + npc.getDisplayName());
        io.println(npc.getDescription());

        io.waitForEnter();
        
        int round = 1;
        int maxRounds = 30; // 限制战斗回合数在30以内
        
        // 战斗主循环
        while (player.isAlive() && npc.isAlive() && round <= maxRounds) {
            io.println("\n回合 " + round + "：");
            
            // 玩家选择招式
            SkillType playerSkill = getPlayerSkillChoice(player);
            if (playerSkill == null) {
                // 如果玩家选择退出战斗，直接结束
                io.println("你选择了退出战斗！");
                return BattleResult.LOSE; // 退出战斗视为失败
            }
            
            // 确定先手顺序
            CharacterBase first, second;
            SkillType firstSkill, secondSkill;
            if (player.calcSpeed() >= npc.calcSpeed()) {
                first = player;
                second = npc;
                firstSkill = playerSkill;
                secondSkill = getNpcSkillChoice(npc);
                io.println("你抢先出手！");
            } else {
                first = npc;
                second = player;
                firstSkill = getNpcSkillChoice(npc);
                secondSkill = playerSkill;
                io.println(npc.getName() + " 抢先出手！");
            }
            
            // 第一回合攻击
            executeAttack(first, second, firstSkill);
            // 如果是玩家攻击，获得技能经验
            if (first == player) {
                player.gainSkillExpFromBattle(firstSkill, 1); // 每次使用技能获得1点经验
            }
            
            // 如果第二个人还活着，进行反击
            if (second.isAlive()) {
                executeAttack(second, first, secondSkill);
                // 如果是玩家反击，获得技能经验
                if (second == player) {
                    player.gainSkillExpFromBattle(secondSkill, 1); // 每次使用技能获得1点经验
                }
            }
            
            // 显示当前状态
            showBattleStatus(player, npc);
            
            round++;
            if (round <= maxRounds) {
                io.waitForEnter();
            } else {
                io.println("\n战斗已超过" + maxRounds + "回合，强制结束！");
                // 如果超过回合数，判断谁的生命值更高
                return determineBattleResultByHp(player, npc);
            }
        }
        
        // 战斗结束
        BattleResult result = determineBattleResult(player, npc);
        
        // 战斗结束后，玩家血量根据对数增长恢复（不练功的情况下）
        int recoveredHp = player.getStats().recoverHpByLogarithmic();
        if (recoveredHp > 0) {
            io.println("\n经过休息，你的生命值恢复了 " + recoveredHp + " 点。");
            io.println("当前生命值: " + player.getStats().getHpCurrent() + "/" + player.getStats().getHpMax());
        }
        
        io.waitForEnter();
        
        return result;
    }
    
    /**
     * 获取玩家选择的招式
     */
    private SkillType getPlayerSkillChoice(Player player) {
        while (true) {
            io.println("\n请选择你的招式：");
            
            // 显示已掌握的技能
            int optionNumber = 1;
            java.util.List<SkillType> availableSkills = new java.util.ArrayList<>();
            
            if (player.hasSkill(SkillType.SABER)) {
                io.println(optionNumber + ". " + SkillType.SABER.getName() + " (当前熟练度: " + player.getSkillLevel(SkillType.SABER) + ")");
                availableSkills.add(SkillType.SABER);
                optionNumber++;
            }
            if (player.hasSkill(SkillType.SWORD)) {
                io.println(optionNumber + ". " + SkillType.SWORD.getName() + " (当前熟练度: " + player.getSkillLevel(SkillType.SWORD) + ")");
                availableSkills.add(SkillType.SWORD);
                optionNumber++;
            }
            if (player.hasSkill(SkillType.FIST)) {
                io.println(optionNumber + ". " + SkillType.FIST.getName() + " (当前熟练度: " + player.getSkillLevel(SkillType.FIST) + ")");
                availableSkills.add(SkillType.FIST);
                optionNumber++;
            }
            
            io.println(optionNumber + ". 逃跑 (战斗失败)");
            int maxChoice = optionNumber;
            
            int choice = io.readIntInput("请输入选择 (1-" + maxChoice + "): ", 1, maxChoice);
            
            if (choice == maxChoice) {
                return null; // 逃跑
            }
            
            // 返回选择的技能
            if (choice > 0 && choice <= availableSkills.size()) {
                return availableSkills.get(choice - 1);
            }
            
            io.println("无效选择，请重新输入！");
        }
    }
    
    /**
     * 获取NPC选择的招式（根据技能熟练度选择）
     */
    private SkillType getNpcSkillChoice(Npc npc) {
        // 获取所有技能的等级
        SkillType mainStyle = npc.getMainStyle();
        int mainLevel = npc.getSkillLevel(mainStyle);
        int saberLevel = npc.getSkillLevel(SkillType.SABER);
        int swordLevel = npc.getSkillLevel(SkillType.SWORD);
        int fistLevel = npc.getSkillLevel(SkillType.FIST);
        
        // 计算总等级（用于权重计算）
        int totalLevel = saberLevel + swordLevel + fistLevel;
        
        // 如果总等级为0，随机选择
        if (totalLevel == 0) {
            SkillType[] skillTypes = SkillType.values();
            return skillTypes[RandomUtils.getRandomInt(0, skillTypes.length - 1)];
        }
        
        // 根据技能等级计算权重，等级越高被选中的概率越大
        // 使用加权随机选择
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
     * 执行一次攻击
     */
    private void executeAttack(CharacterBase attacker, CharacterBase defender, SkillType skillUsed) {
        // 保存原始技能
        Skill originalSkill = attacker.getMainSkill();
        
        // 设置攻击者使用的技能
        // 注意：NPC现在也继承自Player，所以也会使用实际的技能等级
        if (attacker instanceof Player) {
            // 对于玩家和NPC，都使用实际的技能等级
            SkillType skillType = skillUsed;
            int skillLevel = ((Player) attacker).getSkillLevel(skillType);
            attacker.setMainSkill(new Skill(skillType, skillLevel, 0));
        } else {
            // 对于其他类型的角色（理论上不应该出现），使用默认等级1
            attacker.setMainSkill(new Skill(skillUsed, 1, 0));
        }
        
        boolean isCritical = RandomUtils.isSuccess(attacker.calcCritRate());
        boolean isCounter = skillUsed.counters(defender.getMainSkill().getType());
        
        int damage = attacker.attack(defender);
        String skillName = skillUsed.getName();
        
        io.printBattleEffect(
            attacker.getName(),
            skillName,
            defender.getName(),
            damage,
            isCritical,
            isCounter
        );
        
        // 恢复原始技能
        attacker.setMainSkill(originalSkill);
    }
    
    /**
     * 根据血量判断战斗结果（用于回合超时情况）
     */
    private BattleResult determineBattleResultByHp(Player player, Npc npc) {
        if (player.getStats().getHpCurrent() > npc.getStats().getHpCurrent()) {
            io.println("由于回合数限制，根据当前血量判断，你获胜了！");
            return BattleResult.WIN;
        } else if (player.getStats().getHpCurrent() < npc.getStats().getHpCurrent()) {
            io.println("由于回合数限制，根据当前血量判断，你失败了...");
            return BattleResult.LOSE;
        } else {
            // 血量相同，按平局处理，这里按失败处理
            io.println("由于回合数限制，战斗以平局结束，判定为失败...");
            return BattleResult.LOSE;
        }
    }
    
    /**
     * 显示战斗状态
     */
    private void showBattleStatus(Player player, Npc npc) {
        io.println("\n战斗状态：");
        io.println(player.getName() + " HP: " + player.getStats().getHpCurrent() + "/" + player.getStats().getHpMax());
        io.println(npc.getName() + " HP: " + npc.getStats().getHpCurrent() + "/" + npc.getStats().getHpMax());
    }
    
    /**
     * 确定战斗结果
     */
    private BattleResult determineBattleResult(Player player, Npc npc) {
        io.printSeparator();
        
        if (player.isAlive()) {
            io.println("战斗结束！你获胜了！");
            
            // 计算奖励
            int powerBonus = npc.getDifficulty().getRewardMultiplier();
            
            // 给予奖励（gainBattleRewards会处理属性点增加和战力重新计算）
            player.gainBattleRewards(powerBonus);
            
            // 显示奖励
            io.println("\n战斗奖励：");
            io.println("人物战力 +" + powerBonus);
            io.println("根据战斗表现获得了随机属性点！");
            
            return BattleResult.WIN;
        } else {
            io.println("战斗结束！你失败了...");
            io.println("再接再厉，继续努力！");
            
            // 失败惩罚：损失属性点（随机减少5-10点）
            int penaltyPoints = RandomUtils.getRandomInt(5, 10);
            player.getStats().addRandomAttribute(-penaltyPoints);
            player.recalcPower(); // 重新计算战力
            
            io.println("失败惩罚：损失了" + penaltyPoints + "点属性");
            
            return BattleResult.LOSE;
        }
    }
    
}