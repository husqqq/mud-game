package main;// File: BattleService.java

/**
 * 战斗服务类
 */
public class BattleService {
    private boolean autoBattle = false;
    
    /**
     * 设置自动战斗模式
     */
    public void setAutoBattle(boolean auto) {
        this.autoBattle = auto;
    }
    
    /**
     * 进行一场战斗
     */
    public BattleResult fight(Player player, Npc npc) {
        // 初始化战斗状态
        player.restoreFullHp();
        npc.restoreFullHp();
        
        ConsoleIO.printTitle("战斗开始！");
        ConsoleIO.println("你遇到了 " + npc.getDisplayName());
        ConsoleIO.println(npc.getDescription());
        
        if (!autoBattle) {
            ConsoleIO.waitForEnter();
        }
        
        int round = 1;
        
        // 战斗主循环
        while (player.isAlive() && npc.isAlive()) {
            if (!autoBattle) {
                ConsoleIO.println("\n回合 " + round + "：");
            }
            
            // 确定先手顺序
            CharacterBase first, second;
            if (player.calcSpeed() >= npc.calcSpeed()) {
                first = player;
                second = npc;
                if (!autoBattle) {
                    ConsoleIO.println("你抢先出手！");
                }
            } else {
                first = npc;
                second = player;
                if (!autoBattle) {
                    ConsoleIO.println(npc.getName() + " 抢先出手！");
                }
            }
            
            // 第一回合攻击
            executeAttack(first, second);
            
            // 如果第二个人还活着，进行反击
            if (second.isAlive()) {
                executeAttack(second, first);
            }
            
            // 显示当前状态
            if (!autoBattle) {
                showBattleStatus(player, npc);
            }
            
            round++;
            if (!autoBattle) {
                ConsoleIO.waitForEnter();
            }
        }
        
        // 战斗结束
        BattleResult result = determineBattleResult(player, npc);
        
        if (!autoBattle) {
            ConsoleIO.waitForEnter();
        }
        
        return result;
    }
    
    /**
     * 执行一次攻击
     */
    private void executeAttack(CharacterBase attacker, CharacterBase defender) {
        boolean isCritical = RandomUtils.isSuccess(attacker.calcCritRate());
        boolean isCounter = attacker.getMainSkill().getType().counters(defender.getMainSkill().getType());
        
        int damage = attacker.attack(defender);
        String skillName = attacker.getMainSkill().getType().getName();
        
        if (!autoBattle) {
            ConsoleIO.printBattleEffect(
                attacker.getName(),
                skillName,
                defender.getName(),
                damage,
                isCritical,
                isCounter
            );
        }
    }
    
    /**
     * 显示战斗状态
     */
    private void showBattleStatus(Player player, Npc npc) {
        ConsoleIO.println("\n战斗状态：");
        ConsoleIO.println(player.getName() + " HP: " + player.getStats().getHpCurrent() + "/" + player.getStats().getHpMax());
        ConsoleIO.println(npc.getName() + " HP: " + npc.getStats().getHpCurrent() + "/" + npc.getStats().getHpMax());
    }
    
    /**
     * 确定战斗结果
     */
    private BattleResult determineBattleResult(Player player, Npc npc) {
        ConsoleIO.printSeparator();
        
        if (player.isAlive()) {
            ConsoleIO.println("战斗结束！你获胜了！");
            
            // 计算奖励
            int powerBonus = npc.getDifficulty().getRewardMultiplier();
            
            // 给予奖励（gainBattleRewards会处理属性点增加和战力重新计算）
            player.gainBattleRewards(powerBonus);
            
            // 显示奖励
            if (!autoBattle) {
                ConsoleIO.println("\n战斗奖励：");
                ConsoleIO.println("人物战力 +" + powerBonus);
                ConsoleIO.println("根据战斗表现获得了随机属性点！");
            }
            
            return BattleResult.WIN;
        } else {
            ConsoleIO.println("战斗结束！你失败了...");
            ConsoleIO.println("再接再厉，继续努力！");
            
            // 失败惩罚：损失属性点（随机减少5-10点）
            int penaltyPoints = RandomUtils.getRandomInt(5, 10);
            player.getStats().addRandomAttribute(-penaltyPoints);
            player.recalcPower(); // 重新计算战力
            
            if (!autoBattle) {
                ConsoleIO.println("失败惩罚：损失了" + penaltyPoints + "点属性");
            }
            
            return BattleResult.LOSE;
        }
    }
    
}