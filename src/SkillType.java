package main;// File: SkillType.java

import java.io.Serializable;

/**
 * 技能类型枚举
 */
public enum SkillType implements Serializable {
    SABER("刀法", 5),    // 刀法，每级攻击加成5
    SWORD("剑法", 6),   // 剑法，每级攻击加成6
    FIST("拳法", 4);    // 拳法，每级攻击加成4
    
    private final String name;         // 技能名称
    private final int attackBonusPerLevel; // 每级攻击加成
    
    /**
     * 构造函数
     */
    SkillType(String name, int attackBonusPerLevel) {
        this.name = name;
        this.attackBonusPerLevel = attackBonusPerLevel;
    }
    
    /**
     * 获取技能名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取每级攻击加成
     */
    public int getAttackBonusPerLevel() {
        return attackBonusPerLevel;
    }
    
    /**
     * 判断是否克制另一个技能类型
     * 剑克刀，刀克拳，拳克剑
     */
    public boolean counters(SkillType other) {
        if (this == SABER && other == FIST) return true;
        if (this == SWORD && other == SABER) return true;
        return this == FIST && other == SWORD;
    }
    
    /**
     * 获取克制描述
     */
    public String getCounterDescription() {
        return switch (this) {
            case SABER -> "刀克拳";
            case SWORD -> "剑克刀";
            case FIST -> "拳克剑";
            default -> "";
        };
    }
}

