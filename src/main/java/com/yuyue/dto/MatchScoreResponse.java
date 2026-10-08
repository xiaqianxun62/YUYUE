package com.yuyue.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 计分结果：胜方由比分自动判定；全部对阵打完后 gameFinished = true
 */
@Data
@Builder
public class MatchScoreResponse {

    private Long matchId;

    private Integer scoreA;

    private Integer scoreB;

    /** 1 A队胜 2 B队胜 */
    private Integer winner;

    /** 该球局最后一场也打完了 → 球局已结束 */
    private Boolean gameFinished;
}
