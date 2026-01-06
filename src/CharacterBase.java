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
        int baseAttack = calculateBaseAttack(stats.getStr(), mainSkill.getAttackBonus());
        // 攻击力受当前血量影响（血量越低攻击越低）
        double hpRatio = (double) stats.getHpCurrent() / stats.getHpMax();
        double attackMultiplier = 0.5 + (hpRatio * 0.5); // 残血时攻击力为50%-100%
        return (int) (baseAttack * attackMultiplier);
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
        
        // 计算基础伤害（使用新的防御值系统）
        int attack = calcAttack();
        int defense = target.getStats().getDef();
        
        // 防御力提供更强的减伤（防御值 * 2）
        int reducedAttack = Math.max(1, attack - (defense * 2));
        int baseDamage = reducedAttack / 2; // 基础伤害减半
        
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
            counterMultiplier = 1.15; // 克制时伤害提升15%
        }
        
        int finalDamage = (int)(baseDamage * damageMultiplier * counterMultiplier);
        
        // 限制单次伤害不超过目标35%最大血量（防止瞬秒）
        int maxDamage = (int)(target.getStats().getHpMax() * 0.35);
        finalDamage = Math.min(finalDamage, maxDamage);
        finalDamage = Math.max(1, finalDamage); // 至少造成1点伤害
        
        // 应用伤害
        target.takeDamage(finalDamage);
        
        return finalDamage;
    }
    
    /**
     * PvP 战斗中计算受到的伤害（被动防御）
     * @param attacker 攻击者
     * @param attackerSkill 攻击者使用的技能
     * @param defenderSkill 防御者使用的技能（用于判断技能克制）
     * @return 受到的伤害值
     */
    public int calculatePvPDamage(CharacterBase attacker, SkillType attackerSkill, SkillType defenderSkill) {
        // 计算是否命中
        int hitRate = attacker.calcHitRate(this);
        boolean hit = RandomUtils.isSuccess(hitRate);
        
        if (!hit) {
            return 0; // 未命中
        }
        
        // 计算基础伤害
        int attack = attacker.calcAttack();
        int defense = this.getStats().getDef();
        
        // 防御力提供更强的减伤（防御值 * 2）
        int reducedAttack = Math.max(1, attack - (defense * 2));
        int baseDamage = reducedAttack / 2; // 基础伤害减半
        
        // 计算是否暴击
        int critRate = attacker.calcCritRate();
        boolean crit = RandomUtils.isSuccess(critRate);
        
        // 计算最终伤害
        double damageMultiplier;
        if (crit) {
            damageMultiplier = RandomUtils.getCritDamageMultiplier(); // 暴击伤害1.5-2.0倍
        } else {
            damageMultiplier = RandomUtils.getNormalDamageMultiplier(); // 普通伤害0.8-1.2倍
        }
        
        // 检查技能克制关系（只在互相攻击时生效，即 defenderSkill 不为 null）
        double counterMultiplier = 1.0;
        if (defenderSkill != null && attackerSkill.counters(defenderSkill)) {
            counterMultiplier = 1.15; // 克制时伤害提升15%
        }
        
        int finalDamage = (int)(baseDamage * damageMultiplier * counterMultiplier);
        
        // 限制单次伤害不超过目标35%最大血量（防止瞬秒）
        int maxDamage = (int)(this.getStats().getHpMax() * 0.35);
        finalDamage = Math.min(finalDamage, maxDamage);
        finalDamage = Math.max(1, finalDamage); // 至少造成1点伤害
        
        return finalDamage;
    }
    
    /**
     * 获取角色状态描述
     */
    public String getStatus() {
        return name + "\n" +
                "生命: " + stats.getHpCurrent() + "/" + stats.getHpMax() + "\n" +
                "攻击: " + calcAttack() + "\n" +
                "防御: " + stats.getDef() + "\n" +
                "速度: " + calcSpeed() + "\n" +
                "主要技能: " + mainSkill.getDisplayName() + "\n";
    }

    public void setMainSkill(Skill skill) {
        this.mainSkill = skill;
    }
}

