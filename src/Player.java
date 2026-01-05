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
    
    private final String saveName;          // 存档名
    private final Map<SkillType, Skill> skills; // 所有技能
    private SkillType mainStyle;      // 当前主用流派
    private int power;                // 战力
    private String title;             // 称号
    private int roundCount;           // 回合数
    
    /**
     * 构造函数
     */
    public Player(String name, String saveName, Stats stats) {
        super(name, stats, new Skill(SkillType.SWORD)); // 默认主技能为剑法
        this.saveName = saveName;
        this.skills = new HashMap<>();
        this.mainStyle = SkillType.SWORD;
        
        // 只初始化主流派技能，其他技能需要学习才能掌握
        skills.put(mainStyle, new Skill(mainStyle));
        
        // 重新计算战力
        recalcPower();
        this.title = "初入江湖";
        this.roundCount = 0; // 初始化回合数为0
    }
    
    /**
     * 带主流派的构造函数（用于创建新角色时指定主流派）
     */
    public Player(String name, String saveName, Stats stats, SkillType mainStyle) {
        super(name, stats, new Skill(mainStyle));
        this.saveName = saveName;
        this.skills = new HashMap<>();
        this.mainStyle = mainStyle;
        
        // 只初始化主流派技能，其他技能需要学习才能掌握
        skills.put(mainStyle, new Skill(mainStyle));
        
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
        // 只计算已掌握技能的等级
        int saberLevel = hasSkill(SkillType.SABER) ? skills.get(SkillType.SABER).getLevel() : 0;
        int swordLevel = hasSkill(SkillType.SWORD) ? skills.get(SkillType.SWORD).getLevel() : 0;
        int fistLevel = hasSkill(SkillType.FIST) ? skills.get(SkillType.FIST).getLevel() : 0;
        
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
     * 检查技能是否已掌握
     */
    public boolean hasSkill(SkillType type) {
        return skills.containsKey(type) && skills.get(type) != null;
    }
    
    /**
     * 学习新技能
     */
    public boolean learnSkill(SkillType type) {
        if (hasSkill(type)) {
            return false; // 已经掌握了
        }
        skills.put(type, new Skill(type));
        recalcPower();
        return true;
    }
    
    /**
     * 获取指定技能等级
     */
    public int getSkillLevel(SkillType type) {
        Skill skill = skills.get(type);
        return skill != null ? skill.getLevel() : 0;
    }
    
    /**
     * 在战斗中使用技能后增加经验
     */
    public void gainSkillExpFromBattle(SkillType type, int expGain) {
        Skill skill = skills.get(type);
        if (skill != null) {
            boolean levelUp = skill.addExp(expGain);
            if (type == mainStyle) {
                this.mainSkill = skill;
            }
            recalcPower();
            
            // 技能升级消息由调用者处理，避免循环依赖
        }
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
     * 获取战斗奖励
     * @param powerBonus 战力加成
     * @return 获得的属性点数
     */
    public int gainBattleRewards(int powerBonus) {
        // PvE战斗奖励：随机增加2-4点属性（高于修炼的1-2点，低于PvP的3-7点）
        int randomPoints = RandomUtils.getRandomInt(2, 4);
        stats.addRandomAttribute(randomPoints);
        
        // 重新计算战力（包括属性点增加和奖励战力）
        recalcPower();
        this.power += powerBonus;
        
        // 更新称号
        updateTitle();
        
        return randomPoints;
    }
    
    // Getter方法
    public String getSaveName() {
        return saveName;
    }
    
    public SkillType getMainStyle() {
        return mainStyle;
    }
    
    public int getPower() {
        return power;
    }
    
    public void setPower(int power) {
        this.power = power;
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
        for (SkillType skillType : SkillType.values()) {
            String mainIndicator = (skillType == mainStyle) ? " (主)" : "";
            if (hasSkill(skillType)) {
                Skill skill = getSkill(skillType);
                sb.append("  ").append(skill.getDisplayName())
                  .append(mainIndicator)
                  .append(" - 经验: ").append(skill.getExpProgress()).append("\n");
            } else {
                sb.append("  ").append(skillType.getName())
                  .append(mainIndicator)
                  .append(" - 未掌握\n");
            }
        }
        return sb.toString();
    }
}