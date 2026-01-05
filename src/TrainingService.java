package main;// File: TrainingService.java

import java.util.function.Supplier;

import main.io.GameIO;

/**
 * 练功服务类
 */
public class TrainingService {
    private final GameIO io;

    public TrainingService(GameIO io) {
        this.io = io;
    }

    // 为了向后兼容，默认使用ConsoleIO
    public TrainingService() {
        this(new main.ConsoleIO());
    }
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
        io.printTitle("后山竹林 · 练功");
        
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
                io.println("\n回合数已耗尽，无法继续练功。");
                return;
            }
        }
    }
    
    /**
     * 显示技能信息
     */
    private void showSkillInfo(Player player) {
        io.println("\n当前主流派：" + player.getMainStyle().getName());
        io.println("");
    }
    
    /**
     * 选择要修炼的技能
     */
    private SkillType selectSkillToTrain(Player player) {
        StringBuilder prompt = new StringBuilder("选择要修炼的武学：\n");
        int optionNumber = 1;
        java.util.List<SkillType> skillOptions = new java.util.ArrayList<>();
        
        for (SkillType skillType : SkillType.values()) {
            String status = player.hasSkill(skillType) ? " (已掌握)" : " (未掌握，可学习)";
            prompt.append(optionNumber).append(". ").append(skillType.getName()).append(status).append("\n");
            skillOptions.add(skillType);
            optionNumber++;
        }
        prompt.append(optionNumber).append(". 返回");
        
        int choice = io.readChoice(optionNumber, prompt.toString());
        
        if (choice == optionNumber) {
            return null; // 返回
        }
        
        if (choice > 0 && choice <= skillOptions.size()) {
            return skillOptions.get(choice - 1);
        }
        
        return null;
    }
    
    /**
     * 执行一次练功
     */
    private void performTraining(Player player, SkillType skillType) {
        // 如果技能未掌握，先学习它
        if (!player.hasSkill(skillType)) {
            player.learnSkill(skillType);
            io.println("\n你开始学习" + skillType.getName() + "，初步掌握了基础招式！");
            io.println("现在可以继续修炼提升熟练度。");
            io.waitForEnter();
        }
        
        // 显示练功描述
        showTrainingDescription(skillType);
        
        // 计算成功率
        int successRate = calculateSuccessRate(player, skillType);
        boolean success = RandomUtils.isSuccess(successRate);
        
        boolean isMainStyle = player.getMainStyle() == skillType;
        
        // 根据结果处理
        if (success) {
            // 修炼奖励：随机提升1-2点属性（低于PvE战斗的2-4点，远低于PvP的3-7点）
            int attributePoint = RandomUtils.getRandomInt(1, 2);
            if (isMainStyle) {
                // 主流派有20%加成，向上取整（最多2点）
                attributePoint = Math.min(2, (int) Math.ceil(attributePoint * 1.2));
            }
            player.getStats().addRandomAttribute(attributePoint);
            player.recalcPower();
            io.println("\n练功结果：成功！获得 " + attributePoint + " 点属性！");
            
            // 30%几率提升生命值上限（通过增加体质）
            if (RandomUtils.isSuccess((int) (HP_INCREASE_CHANCE * 100))) {
                player.getStats().addCon(1);
                io.println("身体得到锻炼，体质增强，生命值上限提升了！");
            }
            
            // 练功时有几率恢复更多血量
            int recoveredHp = player.getStats().recoverHpFromTraining();
            if (recoveredHp > 0) {
                io.println("练功过程中，你的生命值恢复了 " + recoveredHp + " 点。");
                io.println("当前生命值: " + player.getStats().getHpCurrent() + "/" + player.getStats().getHpMax());
            }

        } else {
            int penaltyPoints = RandomUtils.getRandomInt(5, 10);
            player.getStats().addRandomAttribute(-penaltyPoints);
            player.recalcPower();
            io.println("\n练功结果：失败！今天似乎不在状态...");
            io.println("练功受挫，损失了" + penaltyPoints + "点属性...");
            
            // 即使练功失败，也有基础的血量恢复（对数增长）
            int recoveredHp = player.getStats().recoverHpByLogarithmic();
            if (recoveredHp > 0) {
                io.println("虽然练功失败，但休息让你恢复了 " + recoveredHp + " 点生命值。");
            }
        }
    }
    
    /**
     * 显示练功描述
     */
    private void showTrainingDescription(SkillType skillType) {
        switch (skillType) {
            case SABER:
                io.println("你抽出腰间佩刀，开始练习刀法招式...");
                break;
            case SWORD:
                io.println("你开始默念剑谱，闭目挥剑...");
                break;
            case FIST:
                io.println("你扎稳马步，一拳一式地练习拳法...");
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
    
}