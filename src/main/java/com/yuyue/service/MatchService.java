package com.yuyue.service;

import com.yuyue.common.Constants;
import com.yuyue.common.ErrorCode;
import com.yuyue.dto.MatchReportRequest;
import com.yuyue.entity.MatchGame;
import com.yuyue.event.MatchSettleEvent;
import com.yuyue.exception.BizException;
import com.yuyue.kafka.EventProducer;
import com.yuyue.mapper.MatchGameMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.StringJoiner;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchGameMapper matchGameMapper;
    private final EventProducer eventProducer;

    /**
     * 上报对局结果：落库对局记录，发 Kafka 结算事件（积分消费者异步完成 ELO 更新 + 榜单刷新）
     */
    @Transactional
    public Long report(MatchReportRequest req) {
        if (req.getTeamA().size() != req.getTeamB().size()
                || (req.getTeamA().size() != 1 && req.getTeamA().size() != 2)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单打每队 1 人，双打每队 2 人");
        }

        MatchGame match = new MatchGame();
        match.setGameId(req.getGameId());
        match.setFormat(req.getFormat());
        match.setTeamA(join(req.getTeamA()));
        match.setTeamB(join(req.getTeamB()));
        match.setScoreA(req.getScoreA());
        match.setScoreB(req.getScoreB());
        match.setWinner(req.getWinner());
        match.setSettleStatus(Constants.SETTLE_PENDING);
        matchGameMapper.insert(match);

        eventProducer.sendMatchSettle(MatchSettleEvent.builder()
                .matchId(match.getId())
                .gameId(req.getGameId())
                .format(req.getFormat())
                .teamA(req.getTeamA())
                .teamB(req.getTeamB())
                .winner(req.getWinner())
                .build());

        log.info("对局上报: match={}, game={}, winner={}", match.getId(), req.getGameId(), req.getWinner());
        return match.getId();
    }

    private String join(Iterable<Long> ids) {
        StringJoiner sj = new StringJoiner(",");
        ids.forEach(id -> sj.add(String.valueOf(id)));
        return sj.toString();
    }
}
