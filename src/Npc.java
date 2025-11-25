package main;// File: Npc.java

import java.util.Random;

/**
 * NPC类（敌人）
 */
public class Npc extends CharacterBase {
    // 战力计算常量
    private static final double POWER_PER_STR = 2.0;
    private static final double POWER_PER_AGI = 2.0;
    private static final double POWER_PER_CON = 1.5;
    private static final double POWER_PER_INT = 1.0;
    private static final double POWER_PER_LUK = 1.5;
    private static final double POWER_PER_SKILL_LEVEL = 10.0;
    
    private final Difficulty difficulty;  // 难度
    private final String title;           // 称号
    private static final Random random = new Random();
    
    /**
     * 构造函数
     */
    public Npc(String name, Difficulty difficulty, SkillType skillType, Stats stats, String title) {
        super(name, stats, new Skill(skillType));
        this.difficulty = difficulty;
        this.title = title;
    }
    
    /**
     * 根据难度和玩家战力生成NPC（已废弃，使用NpcFactory）
     */
    @Deprecated
    public static Npc generateNpc(String name, Difficulty difficulty, Player player, SkillType skillType) {
        // 根据玩家战力和难度计算NPC属性
        int basePower = player.getPower();
        double powerMultiplier = difficulty.getStatsMultiplier();
        int targetPower = (int)(basePower * powerMultiplier);
        
        // 生成NPC属性
        Stats npcStats = generateStatsForPower(targetPower);
        
        // 调整生命值
        int hpMultiplier = (int)difficulty.getHpMultiplier() * 10;
        npcStats.setCon(npcStats.getCon() * hpMultiplier / 10);
        npcStats.recalcHpMax();
        
        // 生成称号
        String title = generateTitle(difficulty, skillType);
        
        return new Npc(name, difficulty, skillType, npcStats, title);
    }
    
    /**
     * 根据目标战力生成属性
     */
    private static Stats generateStatsForPower(int targetPower) {
        // 基础属性分配
        int totalPoints = targetPower / 2; // 估算总属性点
        
        // 随机分配属性，但保证每个属性至少有3点
        int str = Math.max(3, random.nextInt(totalPoints / 2));
        int remaining = totalPoints - str;
        
        int agi = Math.max(3, random.nextInt(remaining / 2));
        remaining -= agi;
        
        int con = Math.max(3, random.nextInt(remaining / 2));
        remaining -= con;
        
        int intel = Math.max(3, random.nextInt(remaining / 2));
        remaining -= intel;
        
        int luk = Math.max(3, remaining);
        
        return new Stats(str, agi, con, intel, luk);
    }
    
    /**
     * 生成NPC称号
     */
    private static String generateTitle(Difficulty difficulty, SkillType skillType) {
        String[] titles = {
            "落魄", "流浪", "嚣张", "神秘", "凶狠", "狂傲",
            "无情", "冷血", "嗜血", "独行", "山寨", "恶霸"
        };
        
        String skillTitle = "";
        switch (skillType) {
            case SABER:
                skillTitle = "刀客";
                break;
            case SWORD:
                skillTitle = "剑客";
                break;
            case FIST:
                skillTitle = "拳师";
                break;
        }
        
        String prefix = titles[random.nextInt(titles.length)];
        return prefix + skillTitle;
    }
    
    /**
     * 计算NPC战力
     */
    public int calculatePower() {
        int str = stats.getStr();
        int agi = stats.getAgi();
        int con = stats.getCon();
        int intel = stats.getIntel();
        int luk = stats.getLuk();
        int skillLevel = mainSkill.getLevel();
        
        double power = str * POWER_PER_STR +
                      agi * POWER_PER_AGI +
                      con * POWER_PER_CON +
                      intel * POWER_PER_INT +
                      luk * POWER_PER_LUK +
                      skillLevel * POWER_PER_SKILL_LEVEL;
        
        return (int)power;
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
        return "【" + difficulty.getName() + "】" + title + name + "（" + mainSkill.getType().getName() + "）";
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
    
    /**
     * 获取难度描述
     */
    private String getDifficultyDescription() {
        switch (difficulty) {
            case EASY:
                return "很弱，不堪一击...";
            case NORMAL:
                return "不弱，有一定实力...";
            case HARD:
                return "很强，小心应对...";
            case HELL:
                return "极强，这将是一场恶战！";
            default:
                return "深不可测...";
        }
    }
}

