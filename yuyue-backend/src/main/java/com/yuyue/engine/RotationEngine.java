package com.yuyue.engine;

import com.yuyue.common.Constants;
import com.yuyue.common.ErrorCode;
import com.yuyue.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 轮排引擎（场地轮转上场）
 * <p>
 * 场景：报名人数多于同时上场人数时，按「轮」安排谁上场、谁休息，
 * 典型如 12 人 2 片场地打双打：每轮 8 人上场、4 人休息，轮与轮之间自动轮换。
 * <p>
 * 三条硬约束：
 * <ol>
 *   <li>同一轮里同一人只能上一片场地；</li>
 *   <li>各人上场次数差 ≤ 1（优先让打得少的人上场）；</li>
 *   <li>尽量均衡实力：每场两队积分和接近。</li>
 * </ol>
 * 两条软约束（通过代价函数做随机重启贪心，取最优）：
 * <ul>
 *   <li>避免重复搭档（权重最高）；</li>
 *   <li>避免重复对手。</li>
 * </ul>
 * 纯计算，不落库：既可用于试算，也可用于真实球局。
 */
@Slf4j
@Component
public class RotationEngine {

    /** 随机重启次数：人数少、轮数少时开销可忽略 */
    private static final int RESTARTS = 60;
    /** 重复搭档惩罚 */
    private static final double W_PARTNER = 3.0;
    /** 重复对手惩罚 */
    private static final double W_OPPONENT = 1.0;
    /** 实力不均衡惩罚：每差 100 分计 0.5 */
    private static final double W_IMBALANCE = 0.5;

    /* ---------------- 数据模型 ---------------- */

    public record RotationPlayer(Long userId, String label, int gender, int rating) {
    }

    /** 一片场地上的一场对局 */
    public record CourtMatch(int court, int format, List<Long> teamA, List<Long> teamB) {
    }

    /** 一轮：若干片场地同时开打 + 本轮休息的人 */
    public record RotationRound(int round, List<CourtMatch> matches, List<Long> resting) {
    }

    public record PartnerCount(Long userId, int times) {
    }

    /** 个人统计：上场 / 休息次数，以及和谁搭档过几次 */
    public record PlayerStat(Long userId, String label, int gender, int rating,
                             int playCount, int restCount, List<PartnerCount> partners) {
    }

    public record RotationPlan(int courts, int rounds, int format,
                               List<RotationRound> rotation, List<PlayerStat> stats) {
    }

    /* ---------------- 主流程 ---------------- */

    /**
     * @param players         参与轮排的人（真实球局传报名名单即可）
     * @param courts          场地数（每轮同时开打的场数）
     * @param rounds          轮数
     * @param format          {@link Constants#FORMAT_SINGLES} 单打（2 人/场）或 {@link Constants#FORMAT_MEN_DOUBLES} 双打（4 人/场）
     * @param balanceStrength 是否按积分均衡组队；false 则纯随机配对
     */
    public RotationPlan plan(List<RotationPlayer> players, int courts, int rounds,
                             int format, boolean balanceStrength) {
        int perMatch = format == Constants.FORMAT_SINGLES ? 2 : 4;
        if (players == null || players.size() < perMatch) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "人数不足，至少需要 " + perMatch + " 人才能开始轮排");
        }
        if (courts < 1 || courts > 20) {
            throw new BizException(ErrorCode.PARAM_ERROR, "场地数需在 1-20 之间");
        }
        if (rounds < 1 || rounds > 30) {
            throw new BizException(ErrorCode.PARAM_ERROR, "轮数需在 1-30 之间");
        }

        int matchPerRound = Math.min(courts, players.size() / perMatch);
        if (matchPerRound < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "场地数与人数不匹配，无法开赛");
        }
        int need = matchPerRound * perMatch;

        Map<Long, RotationPlayer> byId = new LinkedHashMap<>();
        players.forEach(p -> byId.put(p.userId(), p));

        Map<Long, Integer> playCount = new LinkedHashMap<>();
        Map<Long, Integer> restCount = new LinkedHashMap<>();
        Map<Long, Integer> lastRound = new LinkedHashMap<>();
        players.forEach(p -> {
            playCount.put(p.userId(), 0);
            restCount.put(p.userId(), 0);
            lastRound.put(p.userId(), 0);
        });

        Map<String, Integer> partnerHistory = new LinkedHashMap<>();
        Map<String, Integer> opponentHistory = new LinkedHashMap<>();

        List<RotationRound> out = new ArrayList<>();
        for (int r = 1; r <= rounds; r++) {
            List<RotationPlayer> picked = pickPlayers(players, need, playCount, lastRound);
            List<CourtMatch> matches = buildMatches(picked, matchPerRound, perMatch, format,
                    balanceStrength, partnerHistory, opponentHistory, byId);

            // 应用本轮结果：更新上场 / 休息 / 搭档 / 对手历史
            List<Long> pickedIds = picked.stream().map(RotationPlayer::userId).toList();
            for (RotationPlayer p : picked) {
                playCount.merge(p.userId(), 1, Integer::sum);
                lastRound.put(p.userId(), r);
            }
            for (RotationPlayer p : players) {
                if (!pickedIds.contains(p.userId())) {
                    restCount.merge(p.userId(), 1, Integer::sum);
                }
            }
            for (CourtMatch m : matches) {
                recordPairs(partnerHistory, m.teamA());
                recordPairs(partnerHistory, m.teamB());
                for (Long a : m.teamA()) {
                    for (Long b : m.teamB()) {
                        opponentHistory.merge(key(a, b), 1, Integer::sum);
                    }
                }
            }
            out.add(new RotationRound(r, matches,
                    players.stream()
                            .map(RotationPlayer::userId)
                            .filter(id -> !pickedIds.contains(id))
                            .toList()));
        }

        List<PlayerStat> stats = buildStats(players, playCount, restCount, partnerHistory);
        log.info("轮排完成: 人数={}, 场地={}, 轮数={}, 每轮{}场", players.size(), courts, rounds, matchPerRound);
        return new RotationPlan(courts, rounds, format, out, stats);
    }

    /* ---------------- 选人：少打的先上，久没打的先上 ---------------- */

    private List<RotationPlayer> pickPlayers(List<RotationPlayer> players, int need,
                                             Map<Long, Integer> playCount,
                                             Map<Long, Integer> lastRound) {
        List<RotationPlayer> shuffled = new ArrayList<>(players);
        // 先打乱，保证同等条件下轮换顺序不固定，避免总让名单末尾的人休息
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        shuffled.sort(Comparator
                .comparingInt((RotationPlayer p) -> playCount.getOrDefault(p.userId(), 0))
                .thenComparingInt(p -> lastRound.getOrDefault(p.userId(), 0)));
        return new ArrayList<>(shuffled.subList(0, Math.min(need, shuffled.size())));
    }

    /* ---------------- 生成一轮的所有场地对局 ---------------- */

    private List<CourtMatch> buildMatches(List<RotationPlayer> picked, int matchCount, int perMatch,
                                          int format, boolean balanceStrength,
                                          Map<String, Integer> partnerHistory,
                                          Map<String, Integer> opponentHistory,
                                          Map<Long, RotationPlayer> byId) {
        List<CourtMatch> best = null;
        double bestCost = Double.MAX_VALUE;

        for (int t = 0; t < RESTARTS; t++) {
            List<RotationPlayer> shuffled = new ArrayList<>(picked);
            Collections.shuffle(shuffled, ThreadLocalRandom.current());

            List<CourtMatch> candidate = new ArrayList<>();
            for (int k = 0; k < matchCount; k++) {
                List<RotationPlayer> group = new ArrayList<>(
                        shuffled.subList(k * perMatch, (k + 1) * perMatch));
                group.sort(Comparator.comparingInt(RotationPlayer::rating).reversed());
                List<Long> ids = group.stream().map(RotationPlayer::userId).toList();

                List<Long> teamA;
                List<Long> teamB;
                if (perMatch == 2) {
                    teamA = List.of(ids.get(0));
                    teamB = List.of(ids.get(1));
                } else if (balanceStrength) {
                    // 强弱搭配：最强配最弱，中间两人一队，两队总分最接近
                    teamA = List.of(ids.get(0), ids.get(3));
                    teamB = List.of(ids.get(1), ids.get(2));
                } else {
                    teamA = List.of(ids.get(0), ids.get(1));
                    teamB = List.of(ids.get(2), ids.get(3));
                }
                candidate.add(new CourtMatch(k + 1, format, teamA, teamB));
            }

            double cost = cost(candidate, partnerHistory, opponentHistory, byId, balanceStrength);
            if (cost < bestCost) {
                bestCost = cost;
                best = candidate;
            }
        }
        return best;
    }

    /** 代价：重复搭档 + 重复对手 + 实力差，越小越好 */
    private double cost(List<CourtMatch> matches, Map<String, Integer> partnerHistory,
                        Map<String, Integer> opponentHistory, Map<Long, RotationPlayer> byId,
                        boolean balanceStrength) {
        double total = 0;
        for (CourtMatch m : matches) {
            total += W_PARTNER * sumHistory(partnerHistory, pairsOf(m.teamA()));
            total += W_PARTNER * sumHistory(partnerHistory, pairsOf(m.teamB()));
            for (Long a : m.teamA()) {
                for (Long b : m.teamB()) {
                    total += W_OPPONENT * opponentHistory.getOrDefault(key(a, b), 0);
                }
            }
            if (balanceStrength) {
                total += W_IMBALANCE * Math.abs(sumRating(m.teamA(), byId) - sumRating(m.teamB(), byId)) / 100.0;
            }
        }
        return total;
    }

    private int sumRating(List<Long> ids, Map<Long, RotationPlayer> byId) {
        int sum = 0;
        for (Long id : ids) {
            RotationPlayer p = byId.get(id);
            if (p != null) {
                sum += p.rating();
            }
        }
        return sum;
    }

    private List<String> pairsOf(List<Long> team) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < team.size(); i++) {
            for (int j = i + 1; j < team.size(); j++) {
                keys.add(key(team.get(i), team.get(j)));
            }
        }
        return keys;
    }

    private double sumHistory(Map<String, Integer> history, List<String> keys) {
        int sum = 0;
        for (String k : keys) {
            sum += history.getOrDefault(k, 0);
        }
        return sum;
    }

    private void recordPairs(Map<String, Integer> history, List<Long> team) {
        for (int i = 0; i < team.size(); i++) {
            for (int j = i + 1; j < team.size(); j++) {
                history.merge(key(team.get(i), team.get(j)), 1, Integer::sum);
            }
        }
    }

    private String key(Long a, Long b) {
        return a < b ? a + "_" + b : b + "_" + a;
    }

    /* ---------------- 统计 ---------------- */

    private List<PlayerStat> buildStats(List<RotationPlayer> players, Map<Long, Integer> playCount,
                                        Map<Long, Integer> restCount, Map<String, Integer> partnerHistory) {
        return players.stream()
                .map(p -> new PlayerStat(
                        p.userId(),
                        p.label(),
                        p.gender(),
                        p.rating(),
                        playCount.getOrDefault(p.userId(), 0),
                        restCount.getOrDefault(p.userId(), 0),
                        partnersOf(p.userId(), players, partnerHistory)))
                .toList();
    }

    private List<PartnerCount> partnersOf(Long userId, List<RotationPlayer> players,
                                          Map<String, Integer> partnerHistory) {
        return players.stream()
                .map(p -> new PartnerCount(p.userId(),
                        partnerHistory.getOrDefault(key(userId, p.userId()), 0)))
                .filter(pc -> pc.times() > 0 && !pc.userId().equals(userId))
                .sorted(Comparator.comparingInt(PartnerCount::times).reversed())
                .toList();
    }
}
