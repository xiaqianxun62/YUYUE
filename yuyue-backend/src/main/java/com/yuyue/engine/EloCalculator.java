package com.yuyue.engine;

import com.yuyue.config.EloProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ELO 积分计算器
 *
 * 核心规则：赢强的多加分、赢弱的少加分、输给弱的扣分多。
 * - K 因子按个人历史场次衰减：≤20 场 K=40，21-60 场 K=24，>60 场 K=16
 * - 双打：同队两人共用组合期望 E（队伍平均分计算），但各自按自己的 K 更新积分
 * - 防刷：积分差拉开后强方期望 E 趋近 1，胜方几乎不得分
 */
@Component
@RequiredArgsConstructor
public class EloCalculator {

    private final EloProperties props;

    /** 单个玩家的对局输入 */
    public record PlayerInput(Long userId, int rating, int gamesPlayed) {
    }

    /** 单个玩家的结算输出 */
    public record PlayerResult(Long userId, int ratingBefore, int ratingAfter,
                               int delta, int kFactor, double expected) {
    }

    public record TeamResult(List<PlayerResult> players, double expected) {
    }

    /**
     * 结算一场对局（单打或双打通用）。
     *
     * @param teamA      A 队成员
     * @param teamB      B 队成员
     * @param winnerTeam 胜方：1=A队 2=B队
     */
    public TeamResult[] settle(List<PlayerInput> teamA, List<PlayerInput> teamB, int winnerTeam) {
        if (teamA.isEmpty() || teamB.isEmpty()) {
            throw new IllegalArgumentException("对局双方队伍不能为空");
        }
        if (winnerTeam != 1 && winnerTeam != 2) {
            throw new IllegalArgumentException("胜方只能为 1(A队) 或 2(B队)");
        }

        // 组合期望 E：用队伍平均积分计算（双打同队共用同一个 E）
        double ratingA = teamA.stream().mapToInt(PlayerInput::rating).average().orElse(0);
        double ratingB = teamB.stream().mapToInt(PlayerInput::rating).average().orElse(0);
        double expectedA = expected(ratingA, ratingB);
        double expectedB = 1.0 - expectedA;

        double scoreA = winnerTeam == 1 ? 1.0 : 0.0;
        double scoreB = 1.0 - scoreA;

        return new TeamResult[]{
                settleTeam(teamA, expectedA, scoreA),
                settleTeam(teamB, expectedB, scoreB)
        };
    }

    private TeamResult settleTeam(List<PlayerInput> team, double expected, double score) {
        List<PlayerResult> results = new ArrayList<>(team.size());
        for (PlayerInput p : team) {
            int k = kFactor(p.gamesPlayed());
            // 各自按自己的 K 更新：delta = K * (S - E)
            int delta = (int) Math.round(k * (score - expected));
            results.add(new PlayerResult(p.userId(), p.rating(), p.rating() + delta, delta, k, expected));
        }
        return new TeamResult(results, expected);
    }

    /**
     * 期望胜率 E = 1 / (1 + 10^((对手分 - 己方分) / 400))
     */
    public double expected(double own, double opponent) {
        return 1.0 / (1.0 + Math.pow(10, (opponent - own) / 400.0));
    }

    /**
     * K 因子按历史场次衰减：新手波动大、老手稳定
     */
    public int kFactor(int gamesPlayed) {
        if (gamesPlayed <= 20) {
            return props.getKNew();
        }
        if (gamesPlayed <= 60) {
            return props.getKMid();
        }
        return props.getKPro();
    }
}
