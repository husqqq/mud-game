package main;// File: Skill.java

import java.io.Serializable;

/**
 * 技能类
 */
public class Skill implements Serializable {
    // 升级经验常量
    private static final int BASE_EXP_FOR_LEVEL = 10;
    private static final int EXP_PER_LEVEL = 5;
    
    private final SkillType type;         // 技能类型
    private int level;              // 技能等级
    private int exp;                // 当前经验
    private final int attackBonusPerLevel; // 每级攻击加成
    
    /**
     * 构造函数
     */
    public Skill(SkillType type) {
        this.type = type;
        this.level = 1;
        this.exp = 0;
        this.attackBonusPerLevel = type.getAttackBonusPerLevel();
    }
    
    /**
     * 构造函数
     */
    public Skill(SkillType type, int level, int exp) {
        this.type = type;
        this.level = level;
        this.exp = exp;
        this.attackBonusPerLevel = type.getAttackBonusPerLevel();
    }
    
    /**
     * 增加经验
     * @return 是否升级
     */
    public boolean addExp(int value) {
        this.exp += value;
        return checkLevelUp();
    }
    
    /**
     * 计算升级所需经验
     */
    private int calculateExpNeededForLevel(int level) {
        return BASE_EXP_FOR_LEVEL + level * EXP_PER_LEVEL;
    }
    
    public boolean checkLevelUp() {
        int expNeeded = calculateExpNeededForLevel(level);
        if (exp >= expNeeded) {
            level++;
            exp = 0; // 重置经验
            return true;
        }
        return false;
    }
    
    /**
     * 获取当前攻击加成
     */
    public int getAttackBonus() {
        return level * attackBonusPerLevel;
    }
    
    /**
     * 获取升级所需经验
     */
    public int getExpNeededForNextLevel() {
        return calculateExpNeededForLevel(level);
    }
    
    // Getter方法
    public SkillType getType() {
        return type;
    }
    
    public int getLevel() {
        return level;
    }
    
    public int getExp() {
        return exp;
    }
    
    public int getAttackBonusPerLevel() {
        return attackBonusPerLevel;
    }
    
    /**
     * 获取技能显示名称
     */
    public String getDisplayName() {
        return type.getName() + " Lv" + level;
    }
    
    /**
     * 获取经验进度描述
     */
    public String getExpProgress() {
        return exp + "/" + getExpNeededForNextLevel();
    }
}

