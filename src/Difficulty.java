package main;// File: Difficulty.java

import java.io.Serializable;

/**
 * 难度枚举
 */
public enum Difficulty implements Serializable {
    EASY("简单", 0.8, 0.9, 5),      // 简单难度：属性系数0.8，血量系数0.9，奖励倍数5
    NORMAL("普通", 1.0, 1.0, 10),    // 普通难度：属性系数1.0，血量系数1.0，奖励倍数10
    HARD("困难", 1.3, 1.2, 15),      // 困难难度：属性系数1.3，血量系数1.2，奖励倍数15
    HELL("地狱", 1.8, 1.5, 25);      // 地狱难度：属性系数1.8，血量系数1.5，奖励倍数25
    
    private final String name;          // 难度名称
    private final double statsMultiplier; // 属性系数
    private final double hpMultiplier;    // 血量系数
    private final int rewardMultiplier;   // 奖励倍数
    
    /**
     * 构造函数
     */
    Difficulty(String name, double statsMultiplier, double hpMultiplier, int rewardMultiplier) {
        this.name = name;
        this.statsMultiplier = statsMultiplier;
        this.hpMultiplier = hpMultiplier;
        this.rewardMultiplier = rewardMultiplier;
    }
    
    /**
     * 获取难度名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return name;
    }
    
    /**
     * 获取属性系数
     */
    public double getStatsMultiplier() {
        return statsMultiplier;
    }
    
    /**
     * 获取血量系数
     */
    public double getHpMultiplier() {
        return hpMultiplier;
    }
    
    /**
     * 获取奖励倍数
     */
    public int getRewardMultiplier() {
        return rewardMultiplier;
    }
    
    /**
     * 根据索引获取难度
     */
    public static Difficulty getByIndex(int index) {
        Difficulty[] difficulties = values();
        if (index >= 0 && index < difficulties.length) {
            return difficulties[index];
        }
        return NORMAL; // 默认返回普通难度
    }
    
    /**
     * 获取难度选择提示
     */
    public static String getSelectionPrompt() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values().length; i++) {
            sb.append(i + 1).append(". ").append(values()[i].name).append("\n");
        }
        sb.append(values().length + 1).append(". 返回");
        return sb.toString();
    }
}

