package com.yuyue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuyue.common.Constants;
import com.yuyue.common.ErrorCode;
import com.yuyue.config.EloProperties;
import com.yuyue.dto.RotationRequest;
import com.yuyue.dto.RotationResponse;
import com.yuyue.engine.RotationEngine;
import com.yuyue.entity.Game;
import com.yuyue.entity.Registration;
import com.yuyue.exception.BizException;
import com.yuyue.mapper.GameMapper;
import com.yuyue.mapper.RegistrationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轮排：多人、少场地时按轮安排上场与休息，保证机会均等、实力均衡。
 * <p>
 * /rotation/plan 是纯试算；/rotation/game/{id} 直接读球局报名名单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RotationService {

    private final RotationEngine rotationEngine;
    private final GameMapper gameMapper;
    private final RegistrationMapper registrationMapper;
    private final EloProperties eloProperties;

    /** 试算：直接用传入的名单 */
    public RotationResponse plan(RotationRequest req) {
        List<RotationEngine.RotationPlayer> players = new ArrayList<>();
        for (int i = 0; i < req.getPlayers().size(); i++) {
            RotationRequest.RotationPlayer p = req.getPlayers().get(i);
            long userId = p.getUserId() == null ? i + 1L : p.getUserId();
            players.add(new RotationEngine.RotationPlayer(
                    userId,
                    label(p.getLabel(), userId),
                    p.getGender() == null ? 0 : p.getGender(),
                    p.getRating() == null ? eloProperties.getDefaultRating() : p.getRating()));
        }
        return build(players, req);
    }

    /** 真实球局：读报名名单（对外匿名） */
    public RotationResponse planForGame(Long gameId, RotationRequest req) {
        Game game = gameMapper.selectById(gameId);
        if (game == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "球局不存在");
        }
        List<Registration> regs = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>().eq(Registration::getGameId, gameId));
        if (regs.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该球局还没有人报名");
        }
        List<RotationEngine.RotationPlayer> players = regs.stream()
                .map(r -> new RotationEngine.RotationPlayer(
                        r.getUserId(),
                        UserService.anonymousName(r.getUserId()),
                        r.getGender(),
                        r.getRating()))
                .toList();
        return build(players, req);
    }

    private RotationResponse build(List<RotationEngine.RotationPlayer> players, RotationRequest req) {
        boolean balance = !Boolean.FALSE.equals(req.getBalanceStrength());
        RotationEngine.RotationPlan result = rotationEngine.plan(
                players, req.getCourts(), req.getRounds(), req.getFormat(), balance);

        Map<Long, RotationEngine.RotationPlayer> byId = new LinkedHashMap<>();
        players.forEach(p -> byId.put(p.userId(), p));

        List<RotationResponse.PlayerBrief> briefs = briefs(players);

        List<RotationResponse.RoundItem> rounds = result.rotation().stream()
                .map(r -> RotationResponse.RoundItem.builder()
                        .round(r.round())
                        .matches(r.matches().stream()
                                .map(m -> RotationResponse.MatchItem.builder()
                                        .court(m.court())
                                        .format(m.format())
                                        .teamA(briefs(m.teamA(), byId))
                                        .teamB(briefs(m.teamB(), byId))
                                        .sumA(sum(m.teamA(), byId))
                                        .sumB(sum(m.teamB(), byId))
                                        .build())
                                .toList())
                        .resting(briefs(r.resting(), byId))
                        .build())
                .toList();

        List<RotationResponse.StatItem> stats = result.stats().stream()
                .map(s -> RotationResponse.StatItem.builder()
                        .userId(s.userId())
                        .label(s.label())
                        .gender(s.gender())
                        .rating(s.rating())
                        .playCount(s.playCount())
                        .restCount(s.restCount())
                        .partners(s.partners().stream()
                                .map(pc -> {
                                    RotationEngine.RotationPlayer who = byId.get(pc.userId());
                                    return RotationResponse.PartnerItem.builder()
                                            .userId(pc.userId())
                                            .label(who == null ? String.valueOf(pc.userId()) : who.label())
                                            .times(pc.times())
                                            .build();
                                })
                                .toList())
                        .build())
                .toList();

        return RotationResponse.builder()
                .courts(result.courts())
                .rounds(result.rounds())
                .format(result.format())
                .formatName(result.format() == Constants.FORMAT_SINGLES ? "单打" : "双打")
                .players(briefs)
                .rotation(rounds)
                .stats(stats)
                .build();
    }

    private List<RotationResponse.PlayerBrief> briefs(List<RotationEngine.RotationPlayer> players) {
        return players.stream()
                .map(p -> RotationResponse.PlayerBrief.builder()
                        .userId(p.userId())
                        .label(p.label())
                        .gender(p.gender())
                        .rating(p.rating())
                        .build())
                .toList();
    }

    private List<RotationResponse.PlayerBrief> briefs(List<Long> ids,
                                                      Map<Long, RotationEngine.RotationPlayer> byId) {
        List<RotationResponse.PlayerBrief> list = new ArrayList<>();
        for (Long id : ids) {
            RotationEngine.RotationPlayer p = byId.get(id);
            if (p != null) {
                list.add(RotationResponse.PlayerBrief.builder()
                        .userId(id).label(p.label()).gender(p.gender()).rating(p.rating()).build());
            }
        }
        return list;
    }

    private int sum(List<Long> ids, Map<Long, RotationEngine.RotationPlayer> byId) {
        int total = 0;
        for (Long id : ids) {
            RotationEngine.RotationPlayer p = byId.get(id);
            if (p != null) {
                total += p.rating();
            }
        }
        return total;
    }

    private String label(String input, long fallbackId) {
        if (input != null && !input.isBlank()) {
            return input.trim();
        }
        return "球员" + fallbackId;
    }
}
