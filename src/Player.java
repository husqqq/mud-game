package main;// File: Player.java

import java.util.HashMap;
import java.util.Map;
import java.io.Serializable;

/**
 * 玩家类
 */
public class Player extends CharacterBase implements Serializable {
    // 战力计算常量
    private static final int POWER_ATTRIBUTE_WEIGHT = 2;
    private static final int POWER_SKILL_WEIGHT = 10;
    private static final int DOMINANCE_THRESHOLD = 1200;
    
    private final String username;          // 用户名
    private final Map<SkillType, Skill> skills; // 所有技能
    private SkillType mainStyle;      // 当前主用流派
    private int power;                // 战力
    private String title;             // 称号
    private int roundCount;           // 回合数
    
    /**
     * 构造函数
     */
    public Player(String name, String username, Stats stats) {
        super(name, stats, new Skill(SkillType.SWORD)); // 默认主技能为剑法
        this.username = username;
        this.skills = new HashMap<>();
        this.mainStyle = SkillType.SWORD;
        
        // 初始化所有技能
        for (SkillType type : SkillType.values()) {
            skills.put(type, new Skill(type));
        }
        
        // 重新计算战力
        recalcPower();
        this.title = "初入江湖";
        this.roundCount = 0; // 初始化回合数为0
    }
    
    /**
     * 计算战力
     */
    private int calculatePower(int str, int agi, int con, int intel, int luk, int saberLevel, int swordLevel, int fistLevel) {
        // 属性战力
        int attributePower = (str + agi + con + intel + luk) * POWER_ATTRIBUTE_WEIGHT;
        // 技能战力
        int skillPower = (saberLevel + swordLevel + fistLevel) * POWER_SKILL_WEIGHT;
        return attributePower + skillPower;
    }
    
    public void recalcPower() {
        int saberLevel = skills.get(SkillType.SABER).getLevel();
        int swordLevel = skills.get(SkillType.SWORD).getLevel();
        int fistLevel = skills.get(SkillType.FIST).getLevel();
        
        this.power = calculatePower(
            stats.getStr(),
            stats.getAgi(),
            stats.getCon(),
            stats.getIntel(),
            stats.getLuk(),
            saberLevel,
            swordLevel,
            fistLevel
        );
        
        // 更新称号
        updateTitle();
    }
    
    /**
     * 更新称号
     */
    private void updateTitle() {
        if (power >= 1200) {
            title = "武林霸主";
        } else if (power >= 800) {
            title = "一代宗师";
        } else if (power >= 400) {
            title = "江湖豪侠";
        } else if (power >= 200) {
            title = "武林新秀";
        } else {
            title = "初入江湖";
        }
    }
    
    /**
     * 切换主用流派
     */
    public void setMainStyle(SkillType style) {
        this.mainStyle = style;
        this.mainSkill = skills.get(style);
    }
    
    /**
     * 获取指定技能
     */
    public Skill getSkill(SkillType type) {
        return skills.get(type);
    }
    
    /**
     * 增加属性点
     */
    public boolean addAttributePoint(String attributeName, int points) {
        switch (attributeName.toLowerCase()) {
            case "str":
                stats.addStr(points);
                break;
            case "agi":
                stats.addAgi(points);
                break;
            case "con":
                stats.addCon(points);
                break;
            case "int":
            case "intel":
                stats.addIntel(points);
                break;
            case "luk":
                stats.addLuk(points);
                break;
            default:
                return false;
        }
        recalcPower();
        return true;
    }
    
    /**
     * 获取技能等级映射（用于战力计算）
     */
    public Map<String, Integer> getSkills() {
        Map<String, Integer> skillLevels = new HashMap<>();
        for (Map.Entry<SkillType, Skill> entry : skills.entrySet()) {
            skillLevels.put(entry.getKey().name(), entry.getValue().getLevel());
        }
        return skillLevels;
    }
    
    /**
     * 提升技能等级
     */
    public boolean trainSkill(SkillType type, int expGain) {
        Skill skill = skills.get(type);
        boolean levelUp = skill.addExp(expGain);
        if (type == mainStyle) {
            this.mainSkill = skill;
        }
        recalcPower();
        return levelUp;
    }
    
    /**
     * 获取战斗奖励
     */
    public void gainBattleRewards(int powerBonus) {
        // 随机增加属性点
        int randomPoints = 1 + (int)(Math.random() * 2); // 随机增加1-2点属性
        stats.addRandomAttribute(randomPoints);
        
        // 重新计算战力（包括属性点增加和奖励战力）
        recalcPower();
        this.power += powerBonus;
        
        // 更新称号
        updateTitle();
    }
    
    /**
     * 检查是否达到称霸武林条件
     */
    private boolean isDominanceAchieved(int power) {
        return power >= DOMINANCE_THRESHOLD;
    }
    
    public boolean isDominant() {
        return isDominanceAchieved(power);
    }
    
    // Getter方法
    public String getUsername() {
        return username;
    }
    
    public SkillType getMainStyle() {
        return mainStyle;
    }
    
    public int getPower() {
        return power;
    }
    
    public String getTitle() {
        return title;
    }
    
    /**
     * 增加回合数
     */
    public void incrementRound() {
        this.roundCount++;
    }
    
    /**
     * 获取回合数
     */
    public int getRoundCount() {
        return roundCount;
    }
    
    /**
     * 设置回合数
     */
    public void setRoundCount(int roundCount) {
        this.roundCount = roundCount;
    }
    
    /**
     * 获取玩家详细状态
     */
    @Override
    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(title).append("】").append(name).append("\n");
        sb.append("战力: ").append(power).append("\n");
        sb.append("生命: ").append(stats.getHpCurrent()).append("/").append(stats.getHpMax()).append("\n");
        sb.append("属性:\n");
        sb.append("  力量: ").append(stats.getStr()).append("\n");
        sb.append("  敏捷: ").append(stats.getAgi()).append("\n");
        sb.append("  体质: ").append(stats.getCon()).append("\n");
        sb.append("  智力: ").append(stats.getIntel()).append("\n");
        sb.append("  幸运: ").append(stats.getLuk()).append("\n");
        sb.append("武学:\n");
        for (Skill skill : skills.values()) {
            String mainIndicator = (skill.getType() == mainStyle) ? " (主)" : "";
            sb.append("  ").append(skill.getDisplayName())
              .append(mainIndicator)
              .append(" - 经验: ").append(skill.getExpProgress()).append("\n");
        }
        return sb.toString();
    }
}

