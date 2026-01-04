package main;// File: Npc.java

/**
 * NPC类（敌人），继承自Player
 */
public class Npc extends Player {
    private final Difficulty difficulty;  // 难度
    private final String title;           // 称号
    
    /**
     * 构造函数
     */
    public Npc(String name, Difficulty difficulty, SkillType mainSkillType, Stats stats, String title, int mainSkillLevel) {
        // 使用NPC专用的saveName（格式：NPC_名称），使用带主流派参数的构造函数
        super(name, "NPC_" + name, stats, mainSkillType);
        this.difficulty = difficulty;
        this.title = title;
        
        // 确保主流派技能存在（理论上应该已经通过构造函数初始化，但为了安全起见再检查一次）
        Skill mainSkill = getSkill(mainSkillType);
        if (mainSkill == null) {
            // 如果技能不存在，创建它
            learnSkill(mainSkillType);
            mainSkill = getSkill(mainSkillType);
            if (mainSkill == null) {
                throw new IllegalStateException("无法创建主流派技能: " + mainSkillType.getName());
            }
        }
        
        // 设置主流派技能等级（通过循环升级来达到目标等级）
        while (mainSkill.getLevel() < mainSkillLevel) {
            int expNeeded = mainSkill.getExpNeededForNextLevel();
            mainSkill.addExp(expNeeded);
        }
        
        // 设置其他流派的技能等级（较低，根据难度决定，至少为1级）
        int otherSkillLevel = Math.max(1, mainSkillLevel - RandomUtils.getRandomInt(1, 3));
        for (SkillType type : SkillType.values()) {
            if (type != mainSkillType) {
                // 如果技能未掌握，先学习它
                if (!hasSkill(type)) {
                    learnSkill(type);
                }
                Skill skill = getSkill(type);
                while (skill.getLevel() < otherSkillLevel) {
                    int expNeeded = skill.getExpNeededForNextLevel();
                    skill.addExp(expNeeded);
                }
            }
        }
        
        // 重新计算战力
        recalcPower();
    }
    
    // Getter方法
    public Difficulty getDifficulty() {
        return difficulty;
    }
    
    public String getTitle() {
        return title;
    }
    
    /**
     * 获取NPC的显示名称
     */
    public String getDisplayName() {
        return "【" + difficulty.getName() + "】" + title + getName() + "（" + getMainStyle().getName() + "）";
    }
    
    /**
     * 获取NPC的描述
     */
    public String getDescription() {
        switch (difficulty) {
            case EASY:
                return "他看起来实力不强，应该是个好对付的对手。";
            case NORMAL:
                return "他的气息看起来不弱……";
            case HARD:
                return "此人身上散发着强大的气息，小心应对！";
            case HELL:
                return "天啊！这股气息让人窒息，难道是传说中的高手？！";
            default:
                return "你遇到了一个对手。";
        }
    }
}

