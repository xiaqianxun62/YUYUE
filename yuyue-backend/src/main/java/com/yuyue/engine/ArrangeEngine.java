package com.yuyue.engine;

import com.yuyue.common.Constants;
import com.yuyue.common.ErrorCode;
import com.yuyue.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 自动编排引擎：按报名人员的性别构成自动过滤 6 套方案，选出唯一适用方案并生成对阵
 *
 * <pre>
 * ① 全单打：报名人数 ≥ 4（兜底方案）
 * ② 全混双：男数 = 女数
 * ③ 全男双：女数 ≤ 1
 * ④ 全女双：男数 ≤ 1
 * ⑤ 混搭·混双优先：男数 ≥ 女数（男多 1 时，至多 1 局男双 vs 混双，最弱男进男双队均衡实力）
 * ⑥ 混搭·同性别优先：男数 ≥ 2 且女数 ≥ 2（男双 + 女双互不混合）
 * </pre>
 *
 * 过滤顺序即上表编号顺序：按性别构成从上到下找到第一个满足条件的方案。
 */
@Slf4j
@Component
public class ArrangeEngine {

    /** 报名球员输入 */
    public record Player(Long userId, int gender, int rating) {
    }

    /** 一场编排出的对局 */
    public record ArrangeMatch(int format, List<Long> teamA, List<Long> teamB) {
    }

    /** 编排结果：方案编号 + 名称 + 对阵列表 */
    public record ArrangeResult(int schemeId, String schemeName, List<ArrangeMatch> matches) {
    }

    /**
     * 按报名构成自动过滤方案并生成对阵
     *
     * @throws BizException 报名人数不足 4 人
     */
    public ArrangeResult arrange(List<Player> players) {
        return arrange(players, false);
    }

    /**
     * @param roundRobin true=循环赛（每支队伍与其他所有队伍各打一场），false=单轮（每人只打一场）
     */
    public ArrangeResult arrange(List<Player> players, boolean roundRobin) {
        if (players == null || players.size() < 4) {
            throw new BizException(ErrorCode.PARAM_ERROR, "报名人数不足 4 人，无法编排");
        }
        long males = players.stream().filter(p -> p.gender() == Constants.GENDER_MALE).count();
        long females = players.size() - males;
        log.info("编排开始: 总人数={}, 男={}, 女={}, 循环赛={}", players.size(), males, females, roundRobin);

        ArrangeResult result;
        if (males == females) {
            result = schemeMixedDoubles(players, roundRobin);
        } else if (females <= 1) {
            result = schemeMenDoubles(players, roundRobin);
        } else if (males <= 1) {
            result = schemeWomenDoubles(players, roundRobin);
        } else if (males > females) {
            result = schemeMixedFirst(players, roundRobin);
        } else {
            result = schemeSameGender(players, roundRobin);
        }
        log.info("编排完成: 方案{} [{}], 共 {} 局", result.schemeId(), result.schemeName(), result.matches().size());
        return result;
    }

    /**
     * 按指定方案生成对阵（不做自动过滤，由调用方 / 用户决定用哪套方案）。
     *
     * @param schemeId 1 全单打 2 全混双 3 全男双 4 全女双 5 混搭·混双优先 6 混搭·同性别优先
     * @throws BizException 报名人数不足 4 人或方案不存在
     */
    public ArrangeResult arrangeByScheme(List<Player> players, int schemeId) {
        return arrangeByScheme(players, schemeId, false);
    }

    public ArrangeResult arrangeByScheme(List<Player> players, int schemeId, boolean roundRobin) {
        if (players == null || players.size() < 4) {
            throw new BizException(ErrorCode.PARAM_ERROR, "报名人数不足 4 人，无法编排");
        }
        long males = players.stream().filter(p -> p.gender() == Constants.GENDER_MALE).count();
        long females = players.size() - males;
        log.info("指定方案编排: 方案{}, 总人数={}, 男={}, 女={}, 循环赛={}",
                schemeId, players.size(), males, females, roundRobin);
        return switch (schemeId) {
            case 1 -> schemeSingles(players, roundRobin);
            case 2 -> schemeMixedDoubles(players, roundRobin);
            case 3 -> schemeMenDoubles(players, roundRobin);
            case 4 -> schemeWomenDoubles(players, roundRobin);
            case 5 -> schemeMixedFirst(players, roundRobin);
            case 6 -> schemeSameGender(players, roundRobin);
            case 7 -> schemeRandomMixedDoubles(players, roundRobin);
            case 8 -> schemeRandomDoubles(players, roundRobin);
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "不支持的编排方案: " + schemeId);
        };
    }

    /** ① 全单打：按积分降序，单轮强 vs 弱，循环赛则两两互打 */
    private ArrangeResult schemeSingles(List<Player> players, boolean roundRobin) {
        List<Player> sorted = new ArrayList<>(players);
        sortDesc(sorted);
        List<List<Long>> teams = sorted.stream().map(p -> List.of(p.userId())).toList();
        return new ArrangeResult(1, "全单打",
                roundRobin
                        ? roundRobinMatches(teams, Constants.FORMAT_SINGLES)
                        : headTailMatches(teams, Constants.FORMAT_SINGLES));
    }

    /** ② 全混双：男数 = 女数，男女按积分排序后蛇形配对 */
    private ArrangeResult schemeMixedDoubles(List<Player> players, boolean roundRobin) {
        List<Player> males = byGender(players, Constants.GENDER_MALE);
        List<Player> females = byGender(players, Constants.GENDER_FEMALE);
        sortDesc(males);
        sortDesc(females);
        return new ArrangeResult(2, "全混双", mixedPairs(males, females, roundRobin));
    }

    /** ③ 全男双：女数 ≤ 1，男队员蛇形组队后相邻队伍对阵 */
    private ArrangeResult schemeMenDoubles(List<Player> players, boolean roundRobin) {
        List<Player> males = byGender(players, Constants.GENDER_MALE);
        return new ArrangeResult(3, "全男双", snakePairs(males, Constants.FORMAT_MEN_DOUBLES, roundRobin));
    }

    /** ④ 全女双：男数 ≤ 1 */
    private ArrangeResult schemeWomenDoubles(List<Player> players, boolean roundRobin) {
        List<Player> females = byGender(players, Constants.GENDER_FEMALE);
        return new ArrangeResult(4, "全女双", snakePairs(females, Constants.FORMAT_WOMEN_DOUBLES, roundRobin));
    }

    /** ⑤ 混搭·混双优先：男数 ≥ 女数，混双为主；男多 1 时至多 1 局男双 vs 混双（最弱男进男双队均衡实力） */
    private ArrangeResult schemeMixedFirst(List<Player> players, boolean roundRobin) {
        List<Player> males = byGender(players, Constants.GENDER_MALE);
        List<Player> females = byGender(players, Constants.GENDER_FEMALE);
        sortDesc(males);
        sortDesc(females);

        List<ArrangeMatch> matches = new ArrayList<>();
        if (males.size() - females.size() == 1) {
            // 男多 1：1 局男双 vs 混双——最弱两名男进男双队，最强男 + 最强女组成混双队
            Player weakest = males.get(males.size() - 1);
            Player secondWeakest = males.get(males.size() - 2);
            matches.add(new ArrangeMatch(Constants.FORMAT_MEN_DOUBLES,
                    List.of(secondWeakest.userId(), weakest.userId()),
                    List.of(males.get(0).userId(), females.get(0).userId())));
            // 剩余男女人数相等，继续混双编排
            List<Player> restMales = new ArrayList<>(males.subList(1, males.size() - 2));
            List<Player> restFemales = new ArrayList<>(females.subList(1, females.size()));
            matches.addAll(mixedPairs(restMales, restFemales, roundRobin));
        } else {
            // 男多 ≥2：女队员全部上场组混双，多余男轮换
            matches.addAll(mixedPairs(males, females, roundRobin));
        }
        return new ArrangeResult(5, "混搭·混双优先", dedup(matches));
    }

    /** ⑥ 混搭·同性别优先：男数 ≥ 2 且女数 ≥ 2，男双 + 女双互不混合 */
    private ArrangeResult schemeSameGender(List<Player> players, boolean roundRobin) {
        List<Player> males = byGender(players, Constants.GENDER_MALE);
        List<Player> females = byGender(players, Constants.GENDER_FEMALE);
        List<ArrangeMatch> matches = new ArrayList<>(
                snakePairs(males, Constants.FORMAT_MEN_DOUBLES, roundRobin));
        matches.addAll(snakePairs(females, Constants.FORMAT_WOMEN_DOUBLES, roundRobin));
        return new ArrangeResult(6, "混搭·同性别优先", matches);
    }

    /** ⑦ 混双·纯随机：仍是一男一女组队，但谁配谁、谁打谁全部随机（不看积分） */
    private ArrangeResult schemeRandomMixedDoubles(List<Player> players, boolean roundRobin) {
        List<Player> males = byGender(players, Constants.GENDER_MALE);
        List<Player> females = byGender(players, Constants.GENDER_FEMALE);
        Collections.shuffle(males);
        Collections.shuffle(females);

        int pairs = Math.min(males.size(), females.size());
        List<List<Long>> teams = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            teams.add(List.of(males.get(i).userId(), females.get(i).userId()));
        }
        return new ArrangeResult(7, "混双·纯随机",
                randomMatches(teams, Constants.FORMAT_MIXED_DOUBLES, roundRobin));
    }

    /** ⑧ 全随机：完全不看性别与积分，随机两人一队、随机两两对阵 */
    private ArrangeResult schemeRandomDoubles(List<Player> players, boolean roundRobin) {
        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        List<List<Long>> teams = new ArrayList<>();
        for (int i = 0; i + 1 < shuffled.size(); i += 2) {
            teams.add(List.of(shuffled.get(i).userId(), shuffled.get(i + 1).userId()));
        }
        return new ArrangeResult(8, "全随机（不分性别）",
                randomMatches(teams, Constants.FORMAT_RANDOM_DOUBLES, roundRobin));
    }

    /** 队伍顺序打乱后按赛制生成对阵 */
    private List<ArrangeMatch> randomMatches(List<List<Long>> teams, int format, boolean roundRobin) {
        List<List<Long>> shuffled = new ArrayList<>(teams);
        Collections.shuffle(shuffled);
        return roundRobin
                ? roundRobinMatches(shuffled, format)
                : adjacentMatches(shuffled, format);
    }

    /** 循环赛：每支队伍与其他所有队伍各打一场，共 k*(k-1)/2 局 */
    private List<ArrangeMatch> roundRobinMatches(List<List<Long>> teams, int format) {
        List<ArrangeMatch> matches = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            for (int j = i + 1; j < teams.size(); j++) {
                matches.add(new ArrangeMatch(format, teams.get(i), teams.get(j)));
            }
        }
        return dedup(matches);
    }

    /** 单轮·首尾配对：第 i 队 vs 倒数第 i 队（强 vs 弱） */
    private List<ArrangeMatch> headTailMatches(List<List<Long>> teams, int format) {
        List<ArrangeMatch> matches = new ArrayList<>();
        int n = teams.size();
        for (int i = 0; i < n / 2; i++) {
            int j = n - 1 - i;
            if (i >= j) {
                break;
            }
            matches.add(new ArrangeMatch(format, teams.get(i), teams.get(j)));
        }
        return dedup(matches);
    }

    /** 单轮·相邻配对：第 0 队 vs 第 1 队，第 2 队 vs 第 3 队，奇数时最后一队轮空 */
    private List<ArrangeMatch> adjacentMatches(List<List<Long>> teams, int format) {
        List<ArrangeMatch> matches = new ArrayList<>();
        for (int t = 0; t + 1 < teams.size(); t += 2) {
            matches.add(new ArrangeMatch(format, teams.get(t), teams.get(t + 1)));
        }
        return dedup(matches);
    }

    /**
     * 混双蛇形配对：男 i 配女 i 为一队，男 j 配女 j 为另一队（强强队 vs 弱弱队），
     * i 与倒数第 i 队互相对阵，避免同一人出现在双方
     */
    private List<ArrangeMatch> mixedPairs(List<Player> males, List<Player> females, boolean roundRobin) {
        int pairs = Math.min(males.size(), females.size());
        List<List<Long>> teams = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            teams.add(List.of(males.get(i).userId(), females.get(i).userId()));
        }
        return roundRobin
                ? roundRobinMatches(teams, Constants.FORMAT_MIXED_DOUBLES)
                : headTailMatches(teams, Constants.FORMAT_MIXED_DOUBLES);
    }

    /**
     * 同性别蛇形组队：按积分排序，强 i 配弱 i 组成 n/2 支队伍，相邻队伍两两对阵
     */
    private List<ArrangeMatch> snakePairs(List<Player> group, int format, boolean roundRobin) {
        int n = group.size();
        if (n < 4) {
            return new ArrayList<>();
        }
        sortDesc(group);
        List<List<Long>> teams = new ArrayList<>();
        for (int i = 0; i < n / 2; i++) {
            teams.add(List.of(group.get(i).userId(), group.get(n - 1 - i).userId()));
        }
        return roundRobin
                ? roundRobinMatches(teams, format)
                : adjacentMatches(teams, format);
    }

    private List<Player> byGender(List<Player> players, int gender) {
        List<Player> result = new ArrayList<>();
        for (Player p : players) {
            if (p.gender() == gender) {
                result.add(p);
            }
        }
        return result;
    }

    private void sortDesc(List<Player> players) {
        players.sort(Comparator.comparingInt(Player::rating).reversed());
    }

    /** 去掉自打（同队 id 重复）与重复对局 */
    private List<ArrangeMatch> dedup(List<ArrangeMatch> matches) {
        List<ArrangeMatch> result = new ArrayList<>();
        for (ArrangeMatch m : matches) {
            boolean selfPlay = m.teamA().stream().anyMatch(m.teamB()::contains);
            if (selfPlay) {
                continue;
            }
            boolean dup = result.stream().anyMatch(r ->
                    (r.teamA().equals(m.teamA()) && r.teamB().equals(m.teamB()))
                            || (r.teamA().equals(m.teamB()) && r.teamB().equals(m.teamA())));
            if (!dup) {
                result.add(m);
            }
        }
        return result;
    }
}
