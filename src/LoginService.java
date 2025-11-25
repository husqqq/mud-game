package main;// File: LoginService.java

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录服务类
 */
public class LoginService {
    private static final String USER_DATA_DIR = "./userdata/";
    private static final String USER_DB_FILE = USER_DATA_DIR + "user_database.txt";
    private final SaveLoadService saveLoadService;
    
    public LoginService(SaveLoadService saveLoadService) {
        this.saveLoadService = saveLoadService;
        
        // 确保用户数据目录存在
        try {
            Files.createDirectories(Paths.get(USER_DATA_DIR));
        }
        catch (IOException e) {
            ConsoleIO.println("警告：无法创建用户数据目录！");
        }
    }

    /**
     * 登录或注册用户
     */
    public Player loginOrRegister() {
        ConsoleIO.printTitle("欢迎来到武侠世界！");
        
        while (true) {
            ConsoleIO.println("请输入用户名：");
            String username = ConsoleIO.readLine();
            
            if (username.trim().isEmpty()) {
                ConsoleIO.println("用户名不能为空！");
                continue;
            }
            
            if (userExists(username)) {
                // 用户已存在，尝试登录
                if (authenticateUser(username)) {
                    // 验证成功，检查是否有存档
                    if (saveLoadService.saveExists(username)) {
                        Player player = saveLoadService.load(username);
                        if (player != null) {
                            return player;
                        }
                    }
                    // 没有存档，创建新角色
                    ConsoleIO.println("未找到存档，创建新角色...");
                    return createNewPlayer(username);
                }
            } else {
                // 新用户，询问是否注册
                if (ConsoleIO.confirm("检测到新用户，是否注册？")) {
                    if (registerUser(username)) {
                        return createNewPlayer(username);
                    }
                }
            }
        }
    }
    
    /**
     * 创建新玩家
     */
    public Player createNewPlayer(String username) {
        ConsoleIO.printTitle("创建新角色");
        
        // 输入角色名
        String characterName;
        while (true) {
            ConsoleIO.println("请输入角色名：");
            characterName = ConsoleIO.readLine();
            if (!characterName.trim().isEmpty()) {
                break;
            }
            ConsoleIO.println("角色名不能为空！");
        }
        
        // 分配属性点
        Stats stats = allocateAttributes();
        
        // 选择主用流派
        SkillType mainStyle = chooseMainStyle();
        
        // 初始化技能
        Map<SkillType, Skill> skills = new HashMap<>();
        for (SkillType type : SkillType.values()) {
            skills.put(type, new Skill(type));
        }
        
        // 创建玩家
        Player player = new Player(characterName, username, stats);
        player.setMainStyle(mainStyle);
        player.recalcPower();
        
        ConsoleIO.println("\n角色创建成功！");
        ConsoleIO.println("你的名字是：" + characterName);
        ConsoleIO.println("主用流派：" + mainStyle.getName());
        ConsoleIO.println("初始战力：" + player.getPower());
        ConsoleIO.waitForEnter();
        
        return player;
    }
    
    // 基础属性常量
    private static final int BASE_ATTRIBUTE_VALUE = 10;
    private static final int INITIAL_ATTRIBUTE_POINTS = 5;
    
    /**
     * 分配属性点
     */
    private Stats allocateAttributes() {
        int baseValue = BASE_ATTRIBUTE_VALUE;
        int totalPoints = INITIAL_ATTRIBUTE_POINTS;
        int remainingPoints = totalPoints;
        
        int str = baseValue;
        int agi = baseValue;
        int con = baseValue;
        int intel = baseValue;
        int luk = baseValue;
        
        ConsoleIO.println("\n你有 " + totalPoints + " 点属性可以自由分配，每项基础为 " + baseValue + " 点。");
        
        while (remainingPoints > 0) {
            showAttributeStatus(str, agi, con, intel, luk, remainingPoints);
            
            ConsoleIO.println("\n请输入要加点的属性名 (str/agi/con/int/luk)，或输入 done 结束：");
            String input = ConsoleIO.readLine().toLowerCase();
            
            if (input.equals("done")) {
                break;
            }
            
            int pointsToAdd = ConsoleIO.readInt("要加多少点？");
            
            if (pointsToAdd <= 0 || pointsToAdd > remainingPoints) {
                ConsoleIO.println("无效的点数，请输入1-" + remainingPoints + "之间的数字。");
                continue;
            }
            
            switch (input) {
                case "str":
                    str += pointsToAdd;
                    break;
                case "agi":
                    agi += pointsToAdd;
                    break;
                case "con":
                    con += pointsToAdd;
                    break;
                case "int":
                case "intel":
                    intel += pointsToAdd;
                    break;
                case "luk":
                    luk += pointsToAdd;
                    break;
                default:
                    ConsoleIO.println("无效的属性名！");
                    continue;
            }
            
            remainingPoints -= pointsToAdd;
        }
        
        Stats stats = new Stats(str, agi, con, intel, luk);
        stats.recalcHpMax();
        
        return stats;
    }
    
    /**
     * 显示当前属性状态
     */
    private void showAttributeStatus(int str, int agi, int con, int intel, int luk, int remainingPoints) {
        ConsoleIO.println("\n当前属性：");
        ConsoleIO.println("STR = " + str + " (力量)");
        ConsoleIO.println("AGI = " + agi + " (敏捷)");
        ConsoleIO.println("CON = " + con + " (体质)");
        ConsoleIO.println("INT = " + intel + " (智力)");
        ConsoleIO.println("LUK = " + luk + " (幸运)");
        ConsoleIO.println("剩余点数：" + remainingPoints);
    }
    
    /**
     * 选择主用流派
     */
    private SkillType chooseMainStyle() {
        ConsoleIO.println("\n请选择你的主用流派：");
        int choice = ConsoleIO.readChoice(3, "1. 刀法 (SABER)\n2. 剑法 (SWORD)\n3. 拳法 (FIST)");
        
        switch (choice) {
            case 1:
                return SkillType.SABER;
            case 2:
                return SkillType.SWORD;
            case 3:
                return SkillType.FIST;
            default:
                return SkillType.SABER;
        }
    }
    
    /**
     * 检查用户是否存在
     */
    private boolean userExists(String username) {
        try (BufferedReader reader = new BufferedReader(new FileReader(USER_DB_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length >= 1 && parts[0].equals(username)) {
                    return true;
                }
            }
        } catch (IOException e) {
            // 文件不存在视为没有用户
        }
        return false;
    }
    
    /**
     * 注册新用户
     */
    private boolean registerUser(String username) {
        ConsoleIO.println("请输入密码：");
        String password = ConsoleIO.readLine();
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_DB_FILE, true))) {
            writer.write(username + ":" + password + "\n");
            ConsoleIO.println("注册成功！");
            return true;
        } catch (IOException e) {
            ConsoleIO.println("注册失败：" + e.getMessage());
            return false;
        }
    }
    
    /**
     * 验证用户
     */
    private boolean authenticateUser(String username) {
        ConsoleIO.println("请输入密码：");
        String password = ConsoleIO.readLine();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(USER_DB_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length >= 2 && parts[0].equals(username) && parts[1].equals(password)) {
                    return true;
                }
            }
        } catch (IOException e) {
            ConsoleIO.println("验证失败：" + e.getMessage());
        }
        
        ConsoleIO.println("密码错误！");
        return false;
    }
}

