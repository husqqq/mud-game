package main;// File: RandomUtils.java

import java.util.Random;

/**
 * 随机工具类
 */
public class RandomUtils {
    private static final Random random = new Random();
    
    // 随机计算常量
    private static final int MIN_EXP_GAIN = 1;
    private static final int MAX_EXP_GAIN = 3;
    private static final double MIN_CRIT_MULTIPLIER = 1.5;
    private static final double MAX_CRIT_MULTIPLIER = 2.0;
    private static final double MIN_NORMAL_MULTIPLIER = 0.8;
    private static final double MAX_NORMAL_MULTIPLIER = 1.2;
    
    /**
     * 获取指定范围内的随机整数 [min, max]
     */
    public static int getRandomInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min cannot be greater than max");
        }
        return random.nextInt(max - min + 1) + min;
    }
    
    /**
     * 根据概率判断是否成功
     * @param probability 成功率（0-100）
     * @return 是否成功
     */
    public static boolean isSuccess(int probability) {
        return random.nextDouble() * 100 < probability;
    }
    
    /**
     * 随机获取技能经验增加量（1-3）
     */
    public static int getRandomExpGain() {
        return getRandomInt(MIN_EXP_GAIN, MAX_EXP_GAIN);
    }
    
    /**
     * 随机选择一个元素
     */
    public static <T> T getRandomElement(T[] array) {
        if (array == null || array.length == 0) {
            return null;
        }
        return array[random.nextInt(array.length)];
    }
    
    /**
     * 获取随机暴击伤害倍数
     */
    public static double getCritDamageMultiplier() {
        return MIN_CRIT_MULTIPLIER + 
               random.nextDouble() * (MAX_CRIT_MULTIPLIER - MIN_CRIT_MULTIPLIER);
    }
    
    /**
     * 获取随机普通伤害倍数（0.8-1.2之间）
     */
    public static double getNormalDamageMultiplier() {
        return MIN_NORMAL_MULTIPLIER + 
               random.nextDouble() * (MAX_NORMAL_MULTIPLIER - MIN_NORMAL_MULTIPLIER);
    }
}

