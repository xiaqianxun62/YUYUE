package com.yuyue.service;

import com.yuyue.common.Constants;
import com.yuyue.config.EloProperties;
import com.yuyue.dto.EloSimulateRequest;
import com.yuyue.dto.EloSimulateResponse;
import com.yuyue.engine.ArrangeEngine;
import com.yuyue.engine.EloCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ELO 试算：复用真实编排引擎与 ELO 计算器，输入人数与积分即可看到编排方案和积分变化。
 * <p>
 * 纯计算，不读库不写库，也不会改动任何真实积分。
 */
@Service
@RequiredArgsConstructor
public class EloSimulateService {

    private final ArrangeEngine arrangeEngine;
    private final EloCalculator eloCalculator;
    private final EloProperties eloProperties;

    public EloSimulateResponse simulate(EloSimulateRequest req) {
        int males = Math.max(req.getMaleCount(), 0);
        int females = Math.max(req.getFemaleCount(), 0);

        // userId 用 1..n 的序号占位，label 用「男1/女1」便于前端展示
        List<ArrangeEngine.Player> players = new ArrayList<>();
        Map<Long, ArrangeEngine.Player> byId = new LinkedHashMap<>();
        for (int i = 0; i < males; i++) {
            addPlayer(players, byId, (long) (i + 1), Constants.GENDER_MALE, req, i);
        }
        for (int j = 0; j < females; j++) {
            addPlayer(players, byId, (long) (males + j + 1), Constants.GENDER_FEMALE, req, males + j);
        }

        boolean roundRobin = Boolean.TRUE.equals(req.getRoundRobin());

        // 指定了方案就按方案编排，否则按性别构成自动过滤
        ArrangeEngine.ArrangeResult arranged = req.getSchemeId() == null
                ? arrangeEngine.arrange(players, roundRobin)
                : arrangeEngine.arrangeByScheme(players, req.getSchemeId(), roundRobin);

        // 当前积分：循环赛里同一个人要打多场，逐场累计（下一场以上一场赛后分为起点）
        Map<Long, Integer> current = new LinkedHashMap<>();
        players.forEach(p -> current.put(p.userId(), p.rating()));

        List<EloSimulateResponse.SimMatch> matches = new ArrayList<>();
        for (int i = 0; i < arranged.matches().size(); i++) {
            ArrangeEngine.ArrangeMatch m = arranged.matches().get(i);
            int winner = winnerOf(req, i);

            EloCalculator.TeamResult[] settled = eloCalculator.settle(
                    inputs(m.teamA(), current, req.getGamesPlayed()),
                    inputs(m.teamB(), current, req.getGamesPlayed()),
                    winner);

            List<EloSimulateResponse.SimSide> sideA = toSide(settled[0], byId, males);
            List<EloSimulateResponse.SimSide> sideB = toSide(settled[1], byId, males);
            for (EloSimulateResponse.SimSide s : sideA) {
                current.put(s.getId(), s.getRatingAfter());
            }
            for (EloSimulateResponse.SimSide s : sideB) {
                current.put(s.getId(), s.getRatingAfter());
            }

            matches.add(EloSimulateResponse.SimMatch.builder()
                    .format(m.format())
                    .formatName(formatName(m.format()))
                    .winner(winner)
                    .teamA(sideA)
                    .teamB(sideB)
                    .expectedA(settled[0].expected())
                    .expectedB(settled[1].expected())
                    .build());
        }

        List<EloSimulateResponse.SimPlayer> simPlayers = players.stream()
                .map(p -> EloSimulateResponse.SimPlayer.builder()
                        .id(p.userId())
                        .gender(p.gender())
                        .label(labelOf(p, males))
                        .rating(p.rating())
                        .finalRating(current.getOrDefault(p.userId(), p.rating()))
                        .gamesPlayed(req.getGamesPlayed())
                        .kFactor(eloCalculator.kFactor(req.getGamesPlayed()))
                        .build())
                .toList();

        return EloSimulateResponse.builder()
                .schemeId(arranged.schemeId())
                .schemeName(arranged.schemeName())
                .players(simPlayers)
                .matches(matches)
                .build();
    }

    private void addPlayer(List<ArrangeEngine.Player> players, Map<Long, ArrangeEngine.Player> byId,
                           Long id, int gender, EloSimulateRequest req, int index) {
        ArrangeEngine.Player player = new ArrangeEngine.Player(id, gender, ratingOf(req, index));
        players.add(player);
        byId.put(id, player);
    }

    private int ratingOf(EloSimulateRequest req, int index) {
        List<Integer> ratings = req.getRatings();
        if (ratings == null || index >= ratings.size() || ratings.get(index) == null) {
            return eloProperties.getDefaultRating();
        }
        return ratings.get(index);
    }

    /** 用「当前积分」构造结算输入，循环赛下即为上一场打完后的分数 */
    private List<EloCalculator.PlayerInput> inputs(List<Long> ids, Map<Long, Integer> currentRatings,
                                                   int gamesPlayed) {
        List<EloCalculator.PlayerInput> result = new ArrayList<>();
        for (Long id : ids) {
            Integer rating = currentRatings.get(id);
            if (rating != null) {
                result.add(new EloCalculator.PlayerInput(id, rating, gamesPlayed));
            }
        }
        return result;
    }

    private List<EloSimulateResponse.SimSide> toSide(EloCalculator.TeamResult team,
                                                     Map<Long, ArrangeEngine.Player> byId, int males) {
        return team.players().stream()
                .map(r -> {
                    ArrangeEngine.Player p = byId.get(r.userId());
                    return EloSimulateResponse.SimSide.builder()
                            .id(r.userId())
                            .label(p == null ? String.valueOf(r.userId()) : labelOf(p, males))
                            .ratingBefore(r.ratingBefore())
                            .delta(r.delta())
                            .ratingAfter(r.ratingAfter())
                            .kFactor(r.kFactor())
                            .expected(r.expected())
                            .build();
                })
                .toList();
    }

    /** 展示名：男1 / 女1（序号按性别各自从 1 开始，男在前女在后） */
    private String labelOf(ArrangeEngine.Player p, int males) {
        if (p.gender() == Constants.GENDER_MALE) {
            return "男" + p.userId();
        }
        return "女" + (p.userId() - males);
    }

    /** 胜方：未指定或越界时默认 A 队胜 */
    private int winnerOf(EloSimulateRequest req, int index) {
        List<Integer> winners = req.getWinners();
        if (winners == null || index >= winners.size() || winners.get(index) == null) {
            return Constants.WINNER_A;
        }
        int w = winners.get(index);
        return w == Constants.WINNER_B ? Constants.WINNER_B : Constants.WINNER_A;
    }

    private String formatName(int format) {
        return switch (format) {
            case Constants.FORMAT_SINGLES -> "单打";
            case Constants.FORMAT_MEN_DOUBLES -> "男双";
            case Constants.FORMAT_WOMEN_DOUBLES -> "女双";
            case Constants.FORMAT_MIXED_DOUBLES -> "混双";
            case Constants.FORMAT_RANDOM_DOUBLES -> "随机双打";
            default -> "未知";
        };
    }
}
