package com.yuyue.kafka;

import com.yuyue.common.Constants;
import com.yuyue.engine.EloCalculator;
import com.yuyue.engine.EloCalculator.PlayerInput;
import com.yuyue.entity.MatchGame;
import com.yuyue.entity.RatingHistory;
import com.yuyue.entity.User;
import com.yuyue.event.MatchSettleEvent;
import com.yuyue.mapper.MatchGameMapper;
import com.yuyue.mapper.RatingHistoryMapper;
import com.yuyue.mapper.UserMapper;
import com.yuyue.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对局结算消费者：消费结算事件，完成 ELO 积分更新。
 *
 * 幂等：对局已结算（settle_status=1）时直接跳过，Kafka at-least-once 重投不会重复加分。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "yuyue.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class MatchSettleConsumer {

    private final UserMapper userMapper;
    private final RatingHistoryMapper ratingHistoryMapper;
    private final MatchGameMapper matchGameMapper;
    private final EloCalculator eloCalculator;
    private final RankingService rankingService;

    @KafkaListener(topics = Constants.TOPIC_MATCH_SETTLE, groupId = "yuyue-group")
    @Transactional
    public void onMatchSettle(MatchSettleEvent event) {
        // 幂等检查：不存在或已结算直接跳过（不抛异常，避免无谓重投）
        MatchGame match = matchGameMapper.selectById(event.getMatchId());
        if (match == null) {
            log.warn("对局 {} 不存在，跳过结算", event.getMatchId());
            return;
        }
        if (match.getSettleStatus() == Constants.SETTLE_DONE) {
            log.info("对局 {} 已结算，跳过重复事件", event.getMatchId());
            return;
        }

        List<PlayerInput> teamA = loadTeam(event.getTeamA());
        List<PlayerInput> teamB = loadTeam(event.getTeamB());
        if (teamA.size() != event.getTeamA().size() || teamB.size() != event.getTeamB().size()) {
            log.error("对局 {} 有成员不存在，放弃结算", event.getMatchId());
            return;
        }

        EloCalculator.TeamResult[] results = eloCalculator.settle(teamA, teamB, event.getWinner());
        apply(results[0], event.getMatchId(), event.getWinner() == Constants.WINNER_A);
        apply(results[1], event.getMatchId(), event.getWinner() == Constants.WINNER_B);

        match.setSettleStatus(Constants.SETTLE_DONE);
        match.setSettleTime(LocalDateTime.now());
        matchGameMapper.updateById(match);

        log.info("对局 {} 结算完成: A队期望={}, B队期望={}",
                event.getMatchId(),
                String.format("%.3f", results[0].expected()),
                String.format("%.3f", results[1].expected()));
    }

    private void apply(EloCalculator.TeamResult team, Long matchId, boolean won) {
        for (EloCalculator.PlayerResult r : team.players()) {
            User user = userMapper.selectById(r.userId());
            if (user == null) {
                continue;
            }
            user.setRating(r.ratingAfter());
            user.setGamesPlayed(user.getGamesPlayed() + 1);
            if (won) {
                user.setWinCount(user.getWinCount() + 1);
            } else {
                user.setLossCount(user.getLossCount() + 1);
            }
            userMapper.updateById(user);

            RatingHistory history = new RatingHistory();
            history.setUserId(r.userId());
            history.setMatchId(matchId);
            history.setRatingBefore(r.ratingBefore());
            history.setRatingAfter(r.ratingAfter());
            history.setDelta(r.delta());
            history.setKFactor(r.kFactor());
            history.setExpected(BigDecimal.valueOf(r.expected()).setScale(4, RoundingMode.HALF_UP));
            ratingHistoryMapper.insert(history);

            // 同步 Redis 积分榜
            rankingService.updateScore(r.userId(), r.ratingAfter());

            log.info("积分更新: user={} {} -> {} ({}{}, K={})",
                    r.userId(), r.ratingBefore(), r.ratingAfter(),
                    r.delta() >= 0 ? "+" : "", r.delta(), r.kFactor());
        }
    }

    private List<PlayerInput> loadTeam(List<Long> userIds) {
        return userIds.stream()
                .map(id -> {
                    User u = userMapper.selectById(id);
                    return u == null ? null : new PlayerInput(u.getId(), u.getRating(), u.getGamesPlayed());
                })
                .filter(p -> p != null)
                .toList();
    }
}
