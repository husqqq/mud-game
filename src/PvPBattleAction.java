package main;

/**
 * PvP 战斗行动类
 * 用于存储玩家在一回合中的战斗选择
 */
public class PvPBattleAction {
    private final Player player;
    private final SkillType skill;
    private final Player target;
    private final boolean isEscape; // 是否逃跑
    
    /**
     * 构造函数 - 攻击行动
     */
    public PvPBattleAction(Player player, SkillType skill, Player target) {
        this.player = player;
        this.skill = skill;
        this.target = target;
        this.isEscape = false;
    }
    
    /**
     * 构造函数 - 逃跑行动
     */
    public PvPBattleAction(Player player) {
        this.player = player;
        this.skill = null;
        this.target = null;
        this.isEscape = true;
    }
    
    public Player getPlayer() {
        return player;
    }
    
    public SkillType getSkill() {
        return skill;
    }
    
    public Player getTarget() {
        return target;
    }
    
    public boolean isEscape() {
        return isEscape;
    }
}
