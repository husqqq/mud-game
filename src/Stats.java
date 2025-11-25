package main;// File: Stats.java

import java.io.Serializable;

/**
 * 角色属性统计类
 */
public class Stats implements Serializable {
    // 基础属性常量
    private static final int BASE_ATTRIBUTE_VALUE = 3;
    private static final int BASE_HP = 50;
    private static final int HP_PER_CON = 10;
    
    private int str; // 力量
    private int agi; // 敏捷
    private int con; // 体质
    private int intel; // 智力
    private int luk; // 幸运
    private int hpMax; // 最大生命值
    private int hpCurrent; // 当前生命值
    
    /**
     * 默认构造函数，使用基础属性值
     */
    public Stats() {
        this.str = BASE_ATTRIBUTE_VALUE;
        this.agi = BASE_ATTRIBUTE_VALUE;
        this.con = BASE_ATTRIBUTE_VALUE;
        this.intel = BASE_ATTRIBUTE_VALUE;
        this.luk = BASE_ATTRIBUTE_VALUE;
        this.hpMax = calculateMaxHP(con);
        this.hpCurrent = hpMax;
    }
    
    /**
     * 构造函数
     */
    public Stats(int str, int agi, int con, int intel, int luk) {
        this.str = str;
        this.agi = agi;
        this.con = con;
        this.intel = intel;
        this.luk = luk;
        this.hpMax = calculateMaxHP(con);
        this.hpCurrent = hpMax;
    }
    
    /**
     * 计算最大生命值
     */
    private int calculateMaxHP(int con) {
        return BASE_HP + con * HP_PER_CON;
    }
    
    public void recalcHpMax() {
        this.hpMax = calculateMaxHP(con);
        // 如果当前生命值超过最大值，则重置为最大值
        if (hpCurrent > hpMax) {
            hpCurrent = hpMax;
        }
    }
    
    /**
     * 恢复全部生命值
     */
    public void restoreFullHp() {
        this.hpCurrent = hpMax;
    }
    
    /**
     * 受到伤害
     */
    public void takeDamage(int damage) {
        this.hpCurrent = Math.max(0, this.hpCurrent - damage);
    }
    
    /**
     * 克隆当前Stats对象
     */
    @Override
    public Stats clone() {
        return new Stats(str, agi, con, intel, luk);
    }
    
    // Getter和Setter方法
    public int getStr() {
        return str;
    }
    
    public void setStr(int str) {
        this.str = str;
    }
    
    public void addStr(int value) {
        this.str += value;
    }
    
    public int getAgi() {
        return agi;
    }
    
    public void setAgi(int agi) {
        this.agi = agi;
    }
    
    public void addAgi(int value) {
        this.agi += value;
    }
    
    public int getCon() {
        return con;
    }
    
    public void setCon(int con) {
        this.con = con;
        recalcHpMax(); // 体质改变时重新计算最大生命值
    }
    
    public void addCon(int value) {
        this.con += value;
        recalcHpMax(); // 体质改变时重新计算最大生命值
    }
    
    public int getIntel() {
        return intel;
    }
    
    public void setIntel(int intel) {
        this.intel = intel;
    }
    
    public void addIntel(int value) {
        this.intel += value;
    }
    
    public int getLuk() {
        return luk;
    }
    
    public void setLuk(int luk) {
        this.luk = luk;
    }
    
    public void addLuk(int value) {
        this.luk += value;
    }
    
    public int getHpMax() {
        return hpMax;
    }
    
    public int getHpCurrent() {
        return hpCurrent;
    }
    
    public void setHpCurrent(int hpCurrent) {
        this.hpCurrent = Math.max(0, Math.min(hpMax, hpCurrent));
    }
    
    /**
     * 随机增加属性点（可以是负数，用于惩罚）
     */
    public void addRandomAttribute(int points) {
        // 随机选择一个属性进行提升或减少
        int attributeIndex = (int) (Math.random() * 5);
        switch (attributeIndex) {
            case 0: 
                str = Math.max(1, str + points); // 确保属性至少为1
                break;
            case 1: 
                agi = Math.max(1, agi + points);
                break;
            case 2: 
                con = Math.max(1, con + points);
                recalcHpMax(); // 体质改变时需要重新计算生命值
                break;
            case 3: 
                intel = Math.max(1, intel + points);
                break;
            case 4: 
                luk = Math.max(1, luk + points);
                break;
        }
        // 如果体质改变，已经在上面重新计算了，否则这里重新计算
        if (attributeIndex != 2) {
            recalcHpMax();
        }
    }
    
    public boolean isAlive() {
        return hpCurrent > 0;
    }
}

