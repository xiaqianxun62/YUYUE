package com.yuyue.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对局结算事件：发布到 Kafka，由积分消费者完成 ELO 落库与榜单更新
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchSettleEvent {

    private Long matchId;

    private Long gameId;

    /** 1单打 2男双 3女双 4混双 */
    private Integer format;

    private List<Long> teamA;

    private List<Long> teamB;

    /** 1=A队胜 2=B队胜 */
    private Integer winner;
}
