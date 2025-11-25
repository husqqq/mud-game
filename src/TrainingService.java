package main;// File: TrainingService.java

import java.util.function.Supplier;

/**
 * 练功服务类
 */
public class TrainingService {
    // 练功相关常量
    private static final int BASE_TRAINING_SUCCESS_RATE = 70;
    private static final int INT_TRAINING_BONUS_PER_POINT = 2;
    private static final double HP_INCREASE_CHANCE = 0.3; // 30%几率提升生命值上限
    
    /**
     * 开始练功
     * @param player 当前玩家
     * @param roundConsumer 用于消耗回合数的回调（每次练功结束调用一次），返回false表示回合耗尽需要立即停止
     */
    public void train(Player player, Supplier<Boolean> roundConsumer) {
        ConsoleIO.printTitle("后山竹林 · 练功");
        
        while (true) {
            // 显示当前技能信息
            showSkillInfo(player);
            
            // 选择要修炼的技能
            SkillType selectedSkill = selectSkillToTrain(player);
            if (selectedSkill == null) {
                return; // 返回主菜单
            }
            
        // 进行一次练功
        performTraining(player, selectedSkill);
            
            // 每次练功结束都视为消耗一个回合
            if (roundConsumer != null) {
                boolean canContinue = roundConsumer.get();
                if (!canContinue) {
                    ConsoleIO.println("\n回合数已耗尽，无法继续练功。");
                    return;
                }
            }
            
            // 检查是否继续
            if (!ConsoleIO.confirm("是否继续练功？")) {
                return;
            }
        }
    }
    
    /**
     * 显示技能信息
     */
    private void showSkillInfo(Player player) {
        ConsoleIO.println("\n当前主流派：" + player.getMainStyle().getName());
        ConsoleIO.println("");
    }
    
    /**
     * 选择要修炼的技能
     */
    private SkillType selectSkillToTrain(Player player) {
        int choice = ConsoleIO.readChoice(4, "选择要修炼的武学：\n1. 刀法\n2. 剑法\n3. 拳法\n4. 返回");

        return switch (choice) {
            case 1 -> SkillType.SABER;
            case 2 -> SkillType.SWORD;
            case 3 -> SkillType.FIST;
            case 4 -> null; // 返回
            default -> null;
        };
    }
    
    /**
     * 执行一次练功
     */
    private void performTraining(Player player, SkillType skillType) {
        // 显示练功描述
        showTrainingDescription(skillType);
        
        // 计算成功率
        int successRate = calculateSuccessRate(player, skillType);
        boolean success = RandomUtils.isSuccess(successRate);
        
        boolean isMainStyle = player.getMainStyle() == skillType;
        
        // 根据结果处理
        if (success) {
            // 随机提升属性
            int attributePoint = RandomUtils.getRandomInt(1, 5);
            if (isMainStyle) {
                attributePoint = (int) Math.ceil(attributePoint * 1.2);
            }
            player.getStats().addRandomAttribute(attributePoint);
            player.recalcPower();
            ConsoleIO.println("\n练功结果：成功！获得" + attributePoint + "点属性点！");
            
            // 30%几率提升生命值上限（通过增加体质）
            if (RandomUtils.isSuccess((int) (HP_INCREASE_CHANCE * 100))) {
                player.getStats().addCon(1);
                ConsoleIO.println("身体得到锻炼，体质增强，生命值上限提升了！");
            }

        } else {
            int penaltyPoints = RandomUtils.getRandomInt(5, 10);
            player.getStats().addRandomAttribute(-penaltyPoints);
            player.recalcPower();
            ConsoleIO.println("\n练功结果：失败！今天似乎不在状态...");
            ConsoleIO.println("练功受挫，损失了" + penaltyPoints + "点属性...");
        }
    }
    
    /**
     * 显示练功描述
     */
    private void showTrainingDescription(SkillType skillType) {
        switch (skillType) {
            case SABER:
                ConsoleIO.println("你抽出腰间佩刀，开始练习刀法招式...");
                break;
            case SWORD:
                ConsoleIO.println("你开始默念剑谱，闭目挥剑...");
                break;
            case FIST:
                ConsoleIO.println("你扎稳马步，一拳一式地练习拳法...");
                break;
        }
    }
    
    /**
     * 计算练功成功率
     */
    private int calculateSuccessRate(Player player, SkillType skillType) {
        int intValue = player.getStats().getIntel();
        
        // 基础成功率 + 智力加成
        int successRate = BASE_TRAINING_SUCCESS_RATE + 
                         (intValue * INT_TRAINING_BONUS_PER_POINT);
        
        if (player.getMainStyle() == skillType) {
            successRate += 10;
        }
        
        // 确保成功率在40%-95%之间
        return Math.max(30, Math.min(95, successRate));
    }
    
    /**
     * 扫荡练功
     */
    public void sweepTrain(Player player, SkillType skillType, int rounds, Supplier<Boolean> roundConsumer) {
        if (rounds <= 0) {
            ConsoleIO.println("扫荡回合数必须大于0。");
            return;
        }
        ConsoleIO.printTitle("一键扫荡 · " + skillType.getName());
        for (int i = 1; i <= rounds; i++) {
            ConsoleIO.println("\n--- 第 " + i + " 次练功 ---");
            performTraining(player, skillType);
            
            if (roundConsumer != null) {
                boolean canContinue = roundConsumer.get();
                if (!canContinue) {
                    ConsoleIO.println("\n回合不足，扫荡提前结束。");
                    break;
                }
            }
        }
        ConsoleIO.println("\n扫荡结束。");
    }
    
}