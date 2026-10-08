package com.yuyue.engine;

import com.yuyue.common.Constants;
import com.yuyue.common.ErrorCode;
import com.yuyue.exception.BizException;
import lombok.RequiredArgsConstructor;
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
 *   <li>尽量均衡实力：用 ELO 期望胜率（与真实结算同一个公式），让每场双方的期望胜率尽量接近 50%。</li>
 * </ol>
 * 两条软约束（通过代价函数做随机重启贪心，取最优）：
 * <ul>
 *   <li>避免重复搭档（权重最高）；</li>
 *   <li>避免重复对手。</li>
 * </ul>
 * 性别规则：双打可按「不分性别 / 男双 / 女双 / 混双」配队，落库赛制由每场的实际性别构成判定。
 * 纯计算，不落库：既可用于试算，也可用于真实球局。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RotationEngine {

    /** 与真实结算同一套 ELO 公式：编排时算的期望胜率，和打完结算时用的是一个算法 */
    private final EloCalculator eloCalculator;

    /** 随机重启次数：人数少、轮数少时开销可忽略 */
    private static final int RESTARTS = 60;
    /** 重复搭档惩罚 */
    private static final double W_PARTNER = 3.0;
    /** 重复对手惩罚 */
    private static final double W_OPPONENT = 1.0;
    /** 实力不均衡惩罚：按期望胜率偏离 50% 的程度计（差 200 分 ≈ 偏离 0.25） */
    private static final double W_IMBALANCE = 4.0;

    /* ---------------- 数据模型 ---------------- */

    public record RotationPlayer(Long userId, String label, int gender, int rating) {
    }

    /** 一片场地上的一场对局；expectedA 是 A 队的 ELO 期望胜率（0-1） */
    public record CourtMatch(int court, int format, List<Long> teamA, List<Long> teamB, double expectedA) {
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
     * @param balanceStrength 是否按 ELO 期望胜率均衡组队；false 则纯随机配对
     */
    public RotationPlan plan(List<RotationPlayer> players, int courts, int rounds,
                             int format, boolean balanceStrength) {
        return plan(players, courts, rounds, format, balanceStrength, Constants.GENDER_RULE_ANY);
    }

    /**
     * @param genderRule 双打怎么配队：{@link Constants#GENDER_RULE_ANY} 不分性别、
     *                   {@link Constants#GENDER_RULE_MEN} 男双、{@link Constants#GENDER_RULE_WOMEN} 女双、
     *                   {@link Constants#GENDER_RULE_MIXED} 混双（每队一男一女）；单打时忽略
     */
    public RotationPlan plan(List<RotationPlayer> players, int courts, int rounds,
                             int format, boolean balanceStrength, int genderRule) {
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

        int males = countGender(players, Constants.GENDER_MALE);
        int females = players.size() - males;

        int matchPerRound = Math.min(courts, players.size() / perMatch);
        if (perMatch == 4) {
            // 男双 / 女双 / 混双 / 男女分场 对可上场人数有额外要求
            if (genderRule == Constants.GENDER_RULE_MIXED) {
                matchPerRound = Math.min(matchPerRound, Math.min(males / 2, females / 2));
            } else if (genderRule == Constants.GENDER_RULE_MEN) {
                matchPerRound = Math.min(matchPerRound, males / 4);
            } else if (genderRule == Constants.GENDER_RULE_WOMEN) {
                matchPerRound = Math.min(matchPerRound, females / 4);
            } else if (genderRule == Constants.GENDER_RULE_SEPARATE) {
                // 男女分场：按男女比分配场地，各性别至少 4 人才能开一片
                matchPerRound = Math.min(matchPerRound, males / 4 + females / 4);
            }
        }
        if (matchPerRound < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    ruleHint(genderRule, perMatch, males, females));
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
            List<RotationPlayer> picked = pickPlayers(players, need, genderRule, playCount, lastRound);
            List<CourtMatch> matches = buildMatches(picked, matchPerRound, perMatch, format,
                    genderRule, balanceStrength, partnerHistory, opponentHistory, byId);

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
        log.info("轮排完成: 人数={}, 场地={}, 轮数={}, 每轮{}场, 性别规则={}",
                players.size(), courts, rounds, matchPerRound, genderRule);
        return new RotationPlan(courts, rounds, format, out, stats);
    }

    private String ruleHint(int genderRule, int perMatch, int males, int females) {
        if (genderRule == Constants.GENDER_RULE_MIXED) {
            return "混双每场需要 2 男 2 女，当前男 " + males + " 人、女 " + females + " 人，无法开赛";
        }
        if (genderRule == Constants.GENDER_RULE_MEN) {
            return "男双每场需要 4 名男生，当前男生 " + males + " 人，无法开赛";
        }
        if (genderRule == Constants.GENDER_RULE_WOMEN) {
            return "女双每场需要 4 名女生，当前女生 " + females + " 人，无法开赛";
        }
        if (genderRule == Constants.GENDER_RULE_SEPARATE) {
            return "男女分场需要男女生各至少 4 人，当前男 " + males + " 人、女 " + females + " 人";
        }
        return "场地数与人数不匹配，无法开赛";
    }

    /* ---------------- 选人：少打的先上，久没打的先上 ---------------- */

    private List<RotationPlayer> pickPlayers(List<RotationPlayer> players, int need, int genderRule,
                                             Map<Long, Integer> playCount,
                                             Map<Long, Integer> lastRound) {
        if (genderRule == Constants.GENDER_RULE_MIXED) {
            // 混双：男女对半，各自按「打得少的先上」挑
            List<RotationPlayer> picked = new ArrayList<>();
            picked.addAll(take(byGender(players, Constants.GENDER_MALE), need / 2, playCount, lastRound));
            picked.addAll(take(byGender(players, Constants.GENDER_FEMALE), need / 2, playCount, lastRound));
            return picked;
        }
        List<RotationPlayer> pool = players;
        if (genderRule == Constants.GENDER_RULE_MEN) {
            pool = byGender(players, Constants.GENDER_MALE);
        } else if (genderRule == Constants.GENDER_RULE_WOMEN) {
            pool = byGender(players, Constants.GENDER_FEMALE);
        } else if (genderRule == Constants.GENDER_RULE_SEPARATE) {
            // 男女分场：男女各自按「打得少的先上」挑
            int perGender = need / 2;
            if (need % 2 != 0) {
                // 奇数：让人数多的一方多上
                int males = countGender(players, Constants.GENDER_MALE);
                if (males >= players.size() - males) {
                    List<RotationPlayer> picked = new ArrayList<>();
                    picked.addAll(take(byGender(players, Constants.GENDER_MALE), perGender + 1, playCount, lastRound));
                    picked.addAll(take(byGender(players, Constants.GENDER_FEMALE), perGender, playCount, lastRound));
                    return picked;
                } else {
                    List<RotationPlayer> picked = new ArrayList<>();
                    picked.addAll(take(byGender(players, Constants.GENDER_MALE), perGender, playCount, lastRound));
                    picked.addAll(take(byGender(players, Constants.GENDER_FEMALE), perGender + 1, playCount, lastRound));
                    return picked;
                }
            }
            List<RotationPlayer> picked = new ArrayList<>();
            picked.addAll(take(byGender(players, Constants.GENDER_MALE), perGender, playCount, lastRound));
            picked.addAll(take(byGender(players, Constants.GENDER_FEMALE), perGender, playCount, lastRound));
            return picked;
        }
        return take(pool, need, playCount, lastRound);
    }

    private List<RotationPlayer> take(List<RotationPlayer> pool, int need,
                                      Map<Long, Integer> playCount,
                                      Map<Long, Integer> lastRound) {
        List<RotationPlayer> shuffled = new ArrayList<>(pool);
        // 先打乱，保证同等条件下轮换顺序不固定，避免总让名单末尾的人休息
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        shuffled.sort(Comparator
                .comparingInt((RotationPlayer p) -> playCount.getOrDefault(p.userId(), 0))
                .thenComparingInt(p -> lastRound.getOrDefault(p.userId(), 0)));
        return new ArrayList<>(shuffled.subList(0, Math.min(need, shuffled.size())));
    }

    private List<RotationPlayer> byGender(List<RotationPlayer> players, int gender) {
        List<RotationPlayer> result = new ArrayList<>();
        for (RotationPlayer p : players) {
            if (p.gender() == gender) {
                result.add(p);
            }
        }
        return result;
    }

    private int countGender(List<RotationPlayer> players, int gender) {
        return byGender(players, gender).size();
    }

    /* ---------------- 生成一轮的所有场地对局 ---------------- */

    private List<CourtMatch> buildMatches(List<RotationPlayer> picked, int matchCount, int perMatch,
                                          int format, int genderRule, boolean balanceStrength,
                                          Map<String, Integer> partnerHistory,
                                          Map<String, Integer> opponentHistory,
                                          Map<Long, RotationPlayer> byId) {
        List<CourtMatch> best = null;
        double bestCost = Double.MAX_VALUE;

        for (int t = 0; t < RESTARTS; t++) {
            List<CourtMatch> candidate = new ArrayList<>();
            if (genderRule == Constants.GENDER_RULE_MIXED && perMatch == 4) {
                // 混双：男女分开洗牌，保证每片场地恰好 2 男 2 女
                List<RotationPlayer> males = byGender(picked, Constants.GENDER_MALE);
                List<RotationPlayer> females = byGender(picked, Constants.GENDER_FEMALE);
                Collections.shuffle(males, ThreadLocalRandom.current());
                Collections.shuffle(females, ThreadLocalRandom.current());
                for (int k = 0; k < matchCount; k++) {
                    List<RotationPlayer> group = new ArrayList<>();
                    group.add(males.get(k * 2));
                    group.add(males.get(k * 2 + 1));
                    group.add(females.get(k * 2));
                    group.add(females.get(k * 2 + 1));
                    candidate.add(randomSplit(group, k + 1, format, genderRule, byId));
                }
            } else if (genderRule == Constants.GENDER_RULE_SEPARATE && perMatch == 4) {
                // 男女分场：男女各自洗牌、各自配对，互不干扰
                List<RotationPlayer> males = byGender(picked, Constants.GENDER_MALE);
                List<RotationPlayer> females = byGender(picked, Constants.GENDER_FEMALE);
                Collections.shuffle(males, ThreadLocalRandom.current());
                Collections.shuffle(females, ThreadLocalRandom.current());
                int maleCourts = Math.min(matchCount, males.size() / 4);
                int femaleCourts = matchCount - maleCourts;
                int courtIdx = 1;
                for (int k = 0; k < maleCourts; k++) {
                    List<RotationPlayer> group = new ArrayList<>(
                            males.subList(k * 4, (k + 1) * 4));
                    candidate.add(randomSplit(group, courtIdx++, format, Constants.GENDER_RULE_MEN, byId));
                }
                for (int k = 0; k < femaleCourts; k++) {
                    List<RotationPlayer> group = new ArrayList<>(
                            females.subList(k * 4, (k + 1) * 4));
                    candidate.add(randomSplit(group, courtIdx++, format, Constants.GENDER_RULE_WOMEN, byId));
                }
            } else {
                List<RotationPlayer> shuffled = new ArrayList<>(picked);
                Collections.shuffle(shuffled, ThreadLocalRandom.current());
                for (int k = 0; k < matchCount; k++) {
                    List<RotationPlayer> group = new ArrayList<>(
                            shuffled.subList(k * perMatch, (k + 1) * perMatch));
                    candidate.add(randomSplit(group, k + 1, format, genderRule, byId));
                }
            }

            double cost = cost(candidate, partnerHistory, opponentHistory, balanceStrength);
            if (cost < bestCost) {
                bestCost = cost;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 在一场的人里随机挑一种合法组队方式：
     * 混双只在一男一女之间配（男强配女强 / 男强配女弱两种），其余三种两两组合都算合法。
     * 哪种最优由代价函数（重复搭档 / 重复对手 / ELO 均衡）在 60 次重启里挑出来。
     */
    private CourtMatch randomSplit(List<RotationPlayer> group, int court, int format,
                                   int genderRule, Map<Long, RotationPlayer> byId) {
        List<int[][]> ways = pairings(group, genderRule);
        int[][] way = ways.get(ThreadLocalRandom.current().nextInt(ways.size()));

        List<Long> teamA = new ArrayList<>();
        for (int idx : way[0]) {
            teamA.add(group.get(idx).userId());
        }
        List<Long> teamB = new ArrayList<>();
        for (int idx : way[1]) {
            teamB.add(group.get(idx).userId());
        }
        return new CourtMatch(court, formatOf(teamA, teamB, format, byId),
                teamA, teamB, expectedOf(teamA, teamB, byId));
    }

    /** 一场内部所有合法的组队方式（下标组合） */
    private List<int[][]> pairings(List<RotationPlayer> group, int genderRule) {
        if (group.size() <= 2) {
            return List.<int[][]>of(new int[][]{{0}, {1}});
        }
        if (genderRule == Constants.GENDER_RULE_MIXED) {
            List<Integer> m = new ArrayList<>();
            List<Integer> f = new ArrayList<>();
            for (int i = 0; i < group.size(); i++) {
                if (group.get(i).gender() == Constants.GENDER_MALE) {
                    m.add(i);
                } else {
                    f.add(i);
                }
            }
            if (m.size() == 2 && f.size() == 2) {
                return List.<int[][]>of(
                        new int[][]{{m.get(0), f.get(0)}, {m.get(1), f.get(1)}},
                        new int[][]{{m.get(0), f.get(1)}, {m.get(1), f.get(0)}});
            }
        }
        return List.<int[][]>of(
                new int[][]{{0, 1}, {2, 3}},
                new int[][]{{0, 2}, {1, 3}},
                new int[][]{{0, 3}, {1, 2}});
    }

    /** 落库赛制由实际性别构成判定：每队一男一女=混双，全男=男双，全女=女双 */
    private int formatOf(List<Long> teamA, List<Long> teamB, int baseFormat,
                         Map<Long, RotationPlayer> byId) {
        if (baseFormat == Constants.FORMAT_SINGLES) {
            return Constants.FORMAT_SINGLES;
        }
        int maleA = malesOf(teamA, byId);
        int maleB = malesOf(teamB, byId);
        if (teamA.size() == 2 && teamB.size() == 2 && maleA == 1 && maleB == 1) {
            return Constants.FORMAT_MIXED_DOUBLES;
        }
        if (maleA == teamA.size() && maleB == teamB.size()) {
            return Constants.FORMAT_MEN_DOUBLES;
        }
        if (maleA == 0 && maleB == 0) {
            return Constants.FORMAT_WOMEN_DOUBLES;
        }
        // 双方构成不一致（如男双 vs 混双）：按 A 队构成定
        return maleA == teamA.size() ? Constants.FORMAT_MEN_DOUBLES
                : (maleA == 0 ? Constants.FORMAT_WOMEN_DOUBLES : Constants.FORMAT_MIXED_DOUBLES);
    }

    private int malesOf(List<Long> ids, Map<Long, RotationPlayer> byId) {
        int n = 0;
        for (Long id : ids) {
            RotationPlayer p = byId.get(id);
            if (p != null && p.gender() == Constants.GENDER_MALE) {
                n++;
            }
        }
        return n;
    }

    /** A 队期望胜率：与 EloCalculator.settle 用的是同一个公式（队伍平均分 → E） */
    private double expectedOf(List<Long> teamA, List<Long> teamB, Map<Long, RotationPlayer> byId) {
        return eloCalculator.expected(avgRating(teamA, byId), avgRating(teamB, byId));
    }

    private double avgRating(List<Long> ids, Map<Long, RotationPlayer> byId) {
        double sum = 0;
        int n = 0;
        for (Long id : ids) {
            RotationPlayer p = byId.get(id);
            if (p != null) {
                sum += p.rating();
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    /** 代价：重复搭档 + 重复对手 + ELO 期望偏离 50%，越小越好 */
    private double cost(List<CourtMatch> matches, Map<String, Integer> partnerHistory,
                        Map<String, Integer> opponentHistory, boolean balanceStrength) {
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
                total += W_IMBALANCE * Math.abs(m.expectedA() - 0.5);
            }
        }
        return total;
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
