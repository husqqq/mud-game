package main;// File: CharacterBase.java

import java.io.Serializable;

/**
 * 角色抽象基类
 */
public abstract class CharacterBase implements Serializable {
    // 战斗计算常量
    private static final int BASE_ATTACK_MULTIPLIER = 3;
    private static final int BASE_DEFENSE_MULTIPLIER = 2;
    private static final int BASE_SPEED_MULTIPLIER = 3;
    private static final int BASE_HIT_RATE = 80;
    private static final int BASE_CRIT_RATE = 5;
    private static final int LUK_CRIT_BONUS_PER_POINT = 2;
    private static final int AGI_HIT_BONUS_PER_POINT = 1;
    private static final int AGI_EVASION_PER_POINT = 1;
    
    protected String name;      // 角色名称
    protected Stats stats;      // 属性
    protected Skill mainSkill;  // 主要技能
    
    /**
     * 构造函数
     */
    protected CharacterBase(String name, Stats stats, Skill mainSkill) {
        this.name = name;
        this.stats = stats;
        this.mainSkill = mainSkill;
    }
    
    /**
     * 计算基础攻击力
     */
    private int calculateBaseAttack(int str, int attackBonus) {
        return str * BASE_ATTACK_MULTIPLIER + attackBonus;
    }
    
    /**
     * 计算基础防御力
     */
    private int calculateBaseDefense(int con) {
        return con * BASE_DEFENSE_MULTIPLIER;
    }
    
    /**
     * 计算速度
     */
    private int calculateSpeed(int agi) {
        return agi * BASE_SPEED_MULTIPLIER;
    }
    
    /**
     * 计算命中率
     */
    private int calculateHitRate(CharacterBase attacker, CharacterBase defender) {
        int hitRate = BASE_HIT_RATE + attacker.stats.getAgi() * AGI_HIT_BONUS_PER_POINT;
        int evasion = defender.stats.getAgi() * AGI_EVASION_PER_POINT;
        return Math.max(5, Math.min(95, hitRate - evasion)); // 确保命中率在5%-95%之间
    }
    
    /**
     * 计算暴击率
     */
    private int calculateCritRate(int luk) {
        int critRate = BASE_CRIT_RATE + luk * LUK_CRIT_BONUS_PER_POINT;
        return Math.min(50, critRate); // 最大暴击率50%
    }
    
    public int calcAttack() {
        return calculateBaseAttack(stats.getStr(), mainSkill.getAttackBonus());
    }
    
    /**
     * 计算防御力
     */
    public int calcDefense() {
        return calculateBaseDefense(stats.getCon());
    }
    
    /**
     * 计算速度
     */
    public int calcSpeed() {
        return calculateSpeed(stats.getAgi());
    }
    
    /**
     * 计算命中率
     */
    public int calcHitRate(CharacterBase target) {
        return calculateHitRate(this, target);
    }
    
    /**
     * 计算暴击率
     */
    public int calcCritRate() {
        return calculateCritRate(stats.getLuk());
    }
    
    /**
     * 是否存活
     */
    public boolean isAlive() {
        return stats.isAlive();
    }
    
    /**
     * 受到伤害
     */
    public void takeDamage(int damage) {
        stats.takeDamage(damage);
    }
    
    /**
     * 恢复全部生命值
     */
    public void restoreFullHp() {
        stats.restoreFullHp();
    }
    
    // Getter方法
    public String getName() {
        return name;
    }
    
    public Stats getStats() {
        return stats;
    }
    
    public Skill getMainSkill() {
        return mainSkill;
    }
    
    /**
     * 攻击另一个角色
     * @param target 目标角色
     * @return 造成的伤害值，如果未命中则返回0
     */
    public int attack(CharacterBase target) {
        // 计算是否命中
        int hitRate = calcHitRate(target);
        boolean hit = RandomUtils.isSuccess(hitRate);
        
        if (!hit) {
            return 0; // 未命中
        }
        
        // 计算基础伤害
        int attack = calcAttack();
        int defense = target.calcDefense();
        int baseDamage = Math.max(1, attack - defense);
        
        // 计算是否暴击
        int critRate = calcCritRate();
        boolean crit = RandomUtils.isSuccess(critRate);
        
        // 计算最终伤害
        double damageMultiplier;
        if (crit) {
            damageMultiplier = RandomUtils.getCritDamageMultiplier(); // 暴击伤害1.5-2.0倍
        } else {
            damageMultiplier = RandomUtils.getNormalDamageMultiplier(); // 普通伤害0.8-1.2倍
        }
        
        // 检查技能克制关系
        double counterMultiplier = 1.0;
        if (mainSkill.getType().counters(target.getMainSkill().getType())) {
            counterMultiplier = 1.3; // 克制时伤害提升30%
        }
        
        int finalDamage = (int)(baseDamage * damageMultiplier * counterMultiplier);
        
        // 应用伤害
        target.takeDamage(finalDamage);
        
        return finalDamage;
    }
    
    /**
     * 获取角色状态描述
     */
    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("\n");
        sb.append("生命: ").append(stats.getHpCurrent()).append("/").append(stats.getHpMax()).append("\n");
        sb.append("攻击: ").append(calcAttack()).append("\n");
        sb.append("防御: ").append(calcDefense()).append("\n");
        sb.append("速度: ").append(calcSpeed()).append("\n");
        sb.append("主要技能: ").append(mainSkill.getDisplayName()).append("\n");
        return sb.toString();
    }
}

