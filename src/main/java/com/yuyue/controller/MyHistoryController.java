package com.yuyue.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuyue.common.ApiResponse;
import com.yuyue.entity.Game;
import com.yuyue.entity.MatchGame;
import com.yuyue.entity.RatingHistory;
import com.yuyue.entity.User;
import com.yuyue.mapper.GameMapper;
import com.yuyue.mapper.MatchGameMapper;
import com.yuyue.mapper.RatingHistoryMapper;
import com.yuyue.mapper.UserMapper;
import com.yuyue.web.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 我的历史比赛 & 积分明细（均需登录）
 */
@Tag(name = "我的历史", description = "当前用户的 match 明细 / ELO 积分流水")
@RestController
@RequestMapping("me")
@RequiredArgsConstructor
public class MyHistoryController {

    private final MatchGameMapper matchGameMapper;
    private final RatingHistoryMapper ratingHistoryMapper;
    private final GameMapper gameMapper;
    private final UserMapper userMapper;

    /**
     * 当前用户所有 match：头像 + 比分 + 积分变化
     */
    @Operation(summary = "我的 match 明细")
    @GetMapping("matches")
    public ApiResponse<List<MyMatchItem>> myMatches() {
        Long userId = UserContext.require();

        List<MatchGame> all = matchGameMapper.selectList(null);
        List<MatchGame> mine = all.stream()
                .filter(m -> (m.getSettleStatus() != null && m.getSettleStatus() == 1)
                        && (contains(m.getTeamA(), userId) || contains(m.getTeamB(), userId)))
                .sorted((a, b) -> b.getSettleTime().compareTo(a.getSettleTime()))
                .toList();

        List<Long> gameIds = mine.stream().map(MatchGame::getGameId).distinct().toList();
        List<Long> allUserIds = new ArrayList<>();
        for (MatchGame m : mine) {
            allUserIds.addAll(parseIds(m.getTeamA()));
            allUserIds.addAll(parseIds(m.getTeamB()));
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Game> gameMap = gameMapper.selectBatchIds(gameIds).stream()
                .collect(Collectors.toMap(Game::getId, g -> g));

        // 当前用户的所有 rating_history：按 matchId 分组，matchId → RatingHistory
        Map<Long, RatingHistory> rhByMatch = ratingHistoryMapper.selectList(
                        new LambdaQueryWrapper<RatingHistory>().eq(RatingHistory::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(RatingHistory::getMatchId, rh -> rh, (a, b) -> a));

        List<MyMatchItem> result = mine.stream().map(m -> {
            boolean inA = contains(m.getTeamA(), userId);
            String myTeam = inA ? m.getTeamA() : m.getTeamB();
            String oppTeam = inA ? m.getTeamB() : m.getTeamA();
            int myScore = inA ? nz(m.getScoreA()) : nz(m.getScoreB());
            int oppScore = inA ? nz(m.getScoreB()) : nz(m.getScoreA());
            int winnerRaw = m.getWinner() == null ? 0 : m.getWinner();
            Integer myWin = winnerRaw == 0 ? null : (inA ? winnerRaw == 1 : winnerRaw == 2) ? 1 : 0;

            // teammates: 我 + 队友（我放最前面，头像组里最显眼的位置）
            List<PlayerBrief> teammates = new ArrayList<>();
            User me = userMap.get(userId);
            teammates.add(toBrief(me, userId));
            teammates.addAll(parseIds(myTeam).stream()
                    .filter(id -> !id.equals(userId))
                    .map(id -> toBrief(userMap.get(id), id))
                    .toList());
            List<PlayerBrief> opponents = parseIds(oppTeam).stream()
                    .map(id -> toBrief(userMap.get(id), id))
                    .toList();

            Game g = gameMap.get(m.getGameId());
            RatingHistory rh = rhByMatch.get(m.getId());

            MyMatchItem item = new MyMatchItem();
            item.setMatchId(m.getId());
            item.setGameId(m.getGameId());
            item.setGameTitle(g == null ? "(未知球局)" : g.getTitle());
            item.setGamePlayDate(g == null ? null : g.getPlayDate());
            item.setFormat(m.getFormat());
            item.setMyScore(myScore);
            item.setOppScore(oppScore);
            item.setMyWin(myWin);
            item.setSettled(m.getSettleStatus() != null && m.getSettleStatus() == 1);
            item.setTeammates(teammates);
            item.setOpponents(opponents);
            item.setMyDelta(rh == null ? null : rh.getDelta());
            item.setMyRatingAfter(rh == null ? null : rh.getRatingAfter());
            item.setSettleTime(m.getSettleTime());
            return item;
        }).toList();

        return ApiResponse.ok(result);
    }

    @Operation(summary = "我的积分明细", description = "ELO 积分变动流水")
    @GetMapping("rating-history")
    public ApiResponse<List<RatingHistory>> myRatingHistory() {
        Long userId = UserContext.require();
        List<RatingHistory> list = ratingHistoryMapper.selectList(new LambdaQueryWrapper<RatingHistory>()
                .eq(RatingHistory::getUserId, userId)
                .orderByDesc(RatingHistory::getCreateTime));
        return ApiResponse.ok(list);
    }

    /* ---- helpers ---- */

    private PlayerBrief toBrief(User u, Long fallbackId) {
        PlayerBrief p = new PlayerBrief();
        if (u == null) {
            p.setName("未知");
            p.setAvatar(null);
        } else {
            p.setName(u.getName());
            p.setAvatar(u.getAvatar());
        }
        return p;
    }

    private static boolean contains(String csv, Long id) {
        if (csv == null || csv.isBlank() || id == null) return false;
        for (String s : csv.split(",")) {
            if (s.trim().equals(String.valueOf(id))) return true;
        }
        return false;
    }

    private static List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).toList();
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }

    /* ---------- DTO ---------- */

    @lombok.Data
    public static class PlayerBrief {
        private String name;
        /** 头像 URL（相对路径，前端用 resolveUrl 拼前缀） */
        private String avatar;
    }

    @lombok.Data
    public static class MyMatchItem {
        private Long matchId;
        private Long gameId;
        private String gameTitle;
        private java.time.LocalDate gamePlayDate;
        /** 1单打 2男双 3女双 4混双 */
        private Integer format;
        private Integer myScore;
        private Integer oppScore;
        /** null=未定 1=我赢 0=我输 */
        private Integer myWin;
        private Boolean settled;
        /** 队友（单打为空） */
        private List<PlayerBrief> teammates;
        /** 对手 */
        private List<PlayerBrief> opponents;
        /** 这场对局我的 ELO 变动（未结算=null） */
        private Integer myDelta;
        /** 这场对局结算后我的 ELO */
        private Integer myRatingAfter;
        private java.time.LocalDateTime settleTime;
    }
}
