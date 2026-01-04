package main;// File: NpcFactory.java

/**
 * NPC工厂类，用于生成不同难度的NPC
 */
public class NpcFactory {
    /**
     * 根据难度生成NPC
     */
    public Npc createNpc(Difficulty difficulty, int playerPower) {
        // 根据玩家战力和难度计算NPC的属性值
        int baseAttribute = calculateBaseAttribute(playerPower, difficulty);
        Stats npcStats = generateNpcStats(baseAttribute, difficulty);
        
        // 随机选择NPC的技能类型（主流派）
        SkillType npcSkillType = RandomUtils.getRandomElement(SkillType.values());
        
        // 根据难度计算主流派技能等级
        int mainSkillLevel = calculateMainSkillLevel(difficulty, playerPower);
        
        // 生成NPC名称和称号
        String npcName = generateNpcName(npcSkillType);
        String npcTitle = generateNpcTitle(difficulty, npcSkillType);
        
        // 创建并返回NPC（NPC战力计算在构造函数中已完成）
        return new Npc(npcName, difficulty, npcSkillType, npcStats, npcTitle, mainSkillLevel);
    }
    
    /**
     * 根据难度和玩家战力计算NPC主流派技能等级
     */
    private int calculateMainSkillLevel(Difficulty difficulty, int playerPower) {
        // 基础等级根据难度决定
        int baseLevel = switch (difficulty) {
            case EASY -> 2;
            case NORMAL -> 3;
            case HARD -> 4;
            case HELL -> 5;
        };
        
        // 根据玩家战力微调（玩家战力越高，NPC技能等级也稍微提高）
        int powerAdjustment = Math.min(3, playerPower / 200); // 最多增加3级
        
        return baseLevel + powerAdjustment;
    }
    
    /**
     * 计算NPC的基础属性值
     */
    private int calculateBaseAttribute(int playerPower, Difficulty difficulty) {
        // 限制NPC强度，确保1100战力的玩家能够击败地狱难度的NPC
        // 基础值约为玩家战力的相应比例，现在降低为原来的60%
        double ratio = difficulty.getStatsMultiplier() * 0.6;
        int baseAttribute = (int) ((double) playerPower / 10 * ratio);
        
        // 确保基础属性在合理范围内，并且不会超过上限
        // 当玩家战力超过1100时，NPC的强度不再线性增长
        if (playerPower > 1100) {
            baseAttribute = (int) ((double) 1100 / 10 * ratio);
        }
        
        return Math.max(5, Math.min(50, baseAttribute));
    }
    
    /**
     * 生成NPC的属性
     */
    private Stats generateNpcStats(int baseAttribute, Difficulty difficulty) {
        int str = baseAttribute + RandomUtils.getRandomInt(-2, 3);
        int agi = baseAttribute + RandomUtils.getRandomInt(-2, 3);
        int con = baseAttribute + RandomUtils.getRandomInt(-2, 3);
        int intel = baseAttribute + RandomUtils.getRandomInt(-2, 3);
        int luk = baseAttribute + RandomUtils.getRandomInt(-2, 3);
        
        // 根据难度调整属性，降低NPC强度
        str = (int) (str * difficulty.getStatsMultiplier() * 0.6);
        agi = (int) (agi * difficulty.getStatsMultiplier() * 0.6);
        con = (int) (con * difficulty.getStatsMultiplier() * 0.6);
        intel = (int) (intel * difficulty.getStatsMultiplier() * 0.6);
        luk = (int) (luk * difficulty.getStatsMultiplier() * 0.6);
        
        // 每个NPC都有一个主属性，会额外增加
        int[] attributes = {str, agi, con, intel, luk};
        int mainAttributeIndex = RandomUtils.getRandomInt(0, 4);
        attributes[mainAttributeIndex] += RandomUtils.getRandomInt(2, 5);
        
        // 创建Stats对象
        Stats stats = new Stats(
            attributes[0], // str
            attributes[1], // agi
            attributes[2], // con
            attributes[3], // intel
            attributes[4]  // luk
        );
        
        // 重新计算最大生命值
        stats.recalcHpMax();
        
        return stats;
    }
    
    /**
     * 生成NPC名称
     */
    private String generateNpcName(SkillType skillType) {
        String[] names = switch (skillType) {
            case SABER -> new String[]{"刀客", "刀王五", "菜刀师傅", "独行刀客", "落魄刀客", "醉酒刀客"};
            case SWORD -> new String[]{"剑侠", "书生剑客", "白衣剑师", "青衫剑手", "中年剑客", "逍遥剑仙"};
            case FIST -> new String[]{"拳师", "武痴", "肉掌王", "铁拳老人", "少林俗家弟子", "丐帮弟子"};
        };

        return RandomUtils.getRandomElement(names);
    }
    
    /**
     * 生成NPC称号
     */
    private String generateNpcTitle(Difficulty difficulty, SkillType skillType) {
        String[] titles = switch (difficulty) {
            case EASY -> new String[]{"初出茅庐的", "学艺不精的", "江湖新手", "普通的", "无名的"};
            case NORMAL -> new String[]{"经验丰富的", "有些实力的", "江湖老手", "厉害的", "小有名气的"};
            case HARD -> new String[]{"声名远扬的", "威震一方的", "江湖高手", "强大的", "令人畏惧的"};
            case HELL -> new String[]{"传说中的", "武林至尊", "无敌的", "恐怖的", "鬼神莫测的"};
        };

        return RandomUtils.getRandomElement(titles);
    }
}

