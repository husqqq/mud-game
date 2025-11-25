package main;// File: BattleResult.java

/**
 * 战斗结果枚举
 */
public enum BattleResult {
    WIN("胜利"),      // 胜利
    LOSE("失败");     // 失败
    
    private final String name; // 结果名称
    
    /**
     * 构造函数
     */
    BattleResult(String name) {
        this.name = name;
    }
    
    /**
     * 获取结果名称
     */
    public String getName() {
        return name;
    }
}

