package main;

import main.io.GameIO;
import java.util.*;
import java.util.concurrent.*;

/**
 * 决斗池管理器
 * 管理全局的玩家对战
 */
public class Arena {
    private final MultiPlayerManager playerManager;
    private final Map<String, GameIO> playerIOs;
    private final GameIO defaultIO;
    private List<Player> participants;
    private boolean active;

    public Arena(MultiPlayerManager playerManager, Map<String, GameIO> playerIOs, GameIO defaultIO) {
        this.playerManager = playerManager;
        this.playerIOs = playerIOs;
        this.defaultIO = defaultIO;
        this.participants = new ArrayList<>();
        this.active = false;
    }

    /**
     * 发起决斗池邀请
     * @param initiator 发起者
     * @return 是否成功开始决斗
     */
    public boolean initiateArena(Player initiator) {
        // 向所有玩家广播发起消息
        broadcastMessage("\n" + initiator.getName() + " 发起了决斗池邀请！");
        broadcastMessage("所有玩家将被邀请参加决斗池...");

        // 获取所有人类玩家（不包括AI接管的）
        List<Player> humanPlayers = playerManager.getHumanPlayers();

        if (humanPlayers.size() < 2) {
            broadcastMessage("需要至少2个玩家才能开始决斗池！");
            return false;
        }

        // 所有人类玩家自动参加
        participants = new ArrayList<>(humanPlayers);
        active = true;

        broadcastMessage("\n===== 决斗池开始 =====");
        broadcastMessage("参加玩家：" + participants.size() + "人");
        for (int i = 0; i < participants.size(); i++) {
            Player p = participants.get(i);
            broadcastMessage((i + 1) + ". " + p.getName() + " (战力: " + p.getPower() + ")");
        }

        // 开始决斗
        startArenaBattle();
        return true;
    }

    /**
     * 向所有参与者广播消息
     */
    private void broadcastMessage(String message) {
        for (Player player : participants) {
            GameIO playerIO = playerIOs.get(player.getName());
            if (playerIO != null) {
                playerIO.println(message);
            } else if (defaultIO != null) {
                defaultIO.println("[广播到 " + player.getName() + "] " + message);
            }
        }
    }

    /**
     * 开始决斗池战斗
     */
    private void startArenaBattle() {
        broadcastMessage("\n决斗开始！每回合所有玩家选择攻击目标和招式");

        int round = 1;
        Random random = new Random();

        while (participants.size() > 1 && active) {
            broadcastMessage("\n===== 决斗池 第 " + round + " 回合 =====");
            broadcastMessage("剩余玩家：" + participants.size() + "人");

            // 显示当前状态
            for (int i = 0; i < participants.size(); i++) {
                Player p = participants.get(i);
                broadcastMessage((i + 1) + ". " + p.getName() +
                              " (HP: " + p.getStats().getHpCurrent() + "/" + p.getStats().getHpMax() + ")");
            }

            // 这里应该实现每个玩家的选择逻辑
            // 暂时用简化逻辑：随机选择目标和攻击
            performArenaRound();

            round++;
            if (round > 50) { // 防止无限循环
                broadcastMessage("决斗超时，随机选择胜利者！");
                break;
            }
        }

        // 决斗结束
        endArena();
    }

    /**
     * 执行决斗回合
     */
    private void performArenaRound() {
        // 简化实现：随机选择目标进行攻击
        // 在实际实现中，应该让每个玩家选择目标和招式

        List<Player> attackers = new ArrayList<>(participants);
        Random random = new Random();

        for (Player attacker : attackers) {
            if (!attacker.getStats().isAlive() || participants.size() <= 1) {
                continue;
            }

            // 随机选择一个不是自己的目标
            List<Player> possibleTargets = new ArrayList<>();
            for (Player target : participants) {
                if (target != attacker && target.getStats().isAlive()) {
                    possibleTargets.add(target);
                }
            }

            if (possibleTargets.isEmpty()) {
                continue;
            }

            Player target = possibleTargets.get(random.nextInt(possibleTargets.size()));

            // 执行攻击（简化）
            int damage = random.nextInt(20) + 10; // 10-30随机伤害
            target.getStats().takeDamage(damage);

            broadcastMessage(attacker.getName() + " 攻击了 " + target.getName() +
                          " 造成 " + damage + " 点伤害！");

            if (!target.getStats().isAlive()) {
                broadcastMessage(target.getName() + " 被击败了！");
                participants.remove(target);
            }
        }
    }

    /**
     * 结束决斗池
     */
    private void endArena() {
        active = false;

        if (participants.size() == 1) {
            Player winner = participants.get(0);
            broadcastMessage("\n🎉 " + winner.getName() + " 获得了决斗池胜利！");

            // 随机奖励点数
            Random random = new Random();
            int rewardPoints = random.nextInt(5) + 3; // 3-7随机点数

            // 这里应该实现奖励逻辑
            broadcastMessage(winner.getName() + " 获得 " + rewardPoints + " 点奖励！");

            // 所有参加者消耗回合
            for (Player p : playerManager.getHumanPlayers()) {
                if (p.getStats().isAlive()) { // 只对还活着的玩家消耗回合
                    p.incrementRound();
                }
            }
        } else {
            broadcastMessage("\n决斗池结束，没有明确的胜利者。");
        }

        participants.clear();
    }

    /**
     * 取消决斗池
     */
    public void cancelArena() {
        if (active) {
            broadcastMessage("决斗池被取消了。");
            active = false;
            participants.clear();
        }
    }

    /**
     * 检查是否正在进行决斗
     */
    public boolean isActive() {
        return active;
    }
}
