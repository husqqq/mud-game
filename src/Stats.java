package main;// File: Stats.java

import java.io.Serializable;

/**
 * 角色属性统计类
 */
public class Stats implements Serializable {
    // 基础属性常量
    private static final int BASE_ATTRIBUTE_VALUE = 3;
    private static final int BASE_HP = 50;
    private static final int HP_PER_CON = 2;
    
    private int str; // 力量
    private int agi; // 敏捷
    private int con; // 体质
    private int intel; // 智力
    private int luk; // 幸运
    private int def; // 防御值（独立属性，可分配）
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
        this.def = BASE_ATTRIBUTE_VALUE;
        this.hpMax = calculateMaxHP(con);
        this.hpCurrent = hpMax;
    }
    
    /**
     * 构造函数（6个属性）
     */
    public Stats(int str, int agi, int con, int intel, int luk, int def) {
        this.str = str;
        this.agi = agi;
        this.con = con;
        this.intel = intel;
        this.luk = luk;
        this.def = def;
        this.hpMax = calculateMaxHP(con);
        this.hpCurrent = hpMax;
    }
    
    /**
     * 构造函数（5个属性，兼容旧代码）
     */
    public Stats(int str, int agi, int con, int intel, int luk) {
        this(str, agi, con, intel, luk, BASE_ATTRIBUTE_VALUE);
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
     * 根据对数增长恢复生命值（不练功的情况下）
     * @return 实际恢复的血量
     */
    public int recoverHpByLogarithmic() {
        if (hpCurrent >= hpMax) {
            return 0; // 已经满血，不恢复
        }
        
        // 计算损失的血量比例
        double lostHpRatio = (double)(hpMax - hpCurrent) / hpMax;
        
        // 使用对数增长公式：恢复量 = 基础恢复 * log(1 + 损失比例 * 系数)
        // 使用自然对数，系数用于调整恢复速度
        double recoveryCoefficient = 5.0; // 调整恢复速度的系数
        double logValue = Math.log(1 + lostHpRatio * recoveryCoefficient);
        
        // 基础恢复量（根据最大血量的一定比例）
        int baseRecovery = Math.max(1, hpMax / 10); // 至少恢复1点，最多恢复最大血量的5%
        
        // 计算恢复量：基础恢复 * 对数因子
        int recoveryAmount = (int)(baseRecovery * logValue);
        
        // 确保不超过最大血量
        int actualRecovery = Math.min(recoveryAmount, hpMax - hpCurrent);
        hpCurrent = Math.min(hpMax, hpCurrent + actualRecovery); // 确保不超过上限
        
        return actualRecovery;
    }
    
    /**
     * 练功时的血量恢复（有几率恢复更多）
     * @return 实际恢复的血量
     */
    public int recoverHpFromTraining() {
        if (hpCurrent >= hpMax) {
            return 0; // 已经满血，不恢复
        }
        
        // 先计算基础恢复量（不应用，只计算）
        double lostHpRatio = (double)(hpMax - hpCurrent) / hpMax;
        double recoveryCoefficient = 5.0;
        double logValue = Math.log(1 + lostHpRatio * recoveryCoefficient);
        int baseRecovery = Math.max(1, hpMax / 20);
        int calculatedBaseRecovery = (int)(baseRecovery * logValue);
        int actualBaseRecovery = Math.min(calculatedBaseRecovery, hpMax - hpCurrent);
        
        // 30%几率额外恢复（恢复量为基础恢复的1.5-2倍）
        int totalRecovery = actualBaseRecovery;
        if (RandomUtils.isSuccess(30)) {
            double bonusMultiplier = 1.5 + Math.random() * 0.5; // 1.5-2.0倍
            int bonusRecovery = (int)(actualBaseRecovery * bonusMultiplier);
            
            // 确保不超过最大血量
            int maxBonusRecovery = hpMax - (hpCurrent + actualBaseRecovery);
            int actualBonusRecovery = Math.min(bonusRecovery, maxBonusRecovery);
            totalRecovery += actualBonusRecovery;
        }
        
        // 应用恢复，确保不超过最大血量
        hpCurrent = Math.min(hpMax, hpCurrent + totalRecovery);
        
        return totalRecovery;
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
    
    public int getDef() {
        return def;
    }
    
    public void setDef(int def) {
        this.def = def;
    }
    
    public void addDef(int value) {
        this.def += value;
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
        // 随机选择一个属性进行提升或减少（包括防御值）
        int attributeIndex = (int) (Math.random() * 6);
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
            case 5:
                def = Math.max(1, def + points);
                break;
        }
    }
    
    public boolean isAlive() {
        return hpCurrent > 0;
    }
}

