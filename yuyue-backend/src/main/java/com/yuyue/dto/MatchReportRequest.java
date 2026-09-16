package com.yuyue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class MatchReportRequest {

    @NotNull(message = "球局不能为空")
    private Long gameId;

    /** 1单打 2男双 3女双 4混双 */
    @NotNull(message = "对局形式不能为空")
    @Min(1)
    @Max(4)
    private Integer format;

    @NotEmpty(message = "A队不能为空")
    private List<Long> teamA;

    @NotEmpty(message = "B队不能为空")
    private List<Long> teamB;

    @Min(0)
    private Integer scoreA = 0;

    @Min(0)
    private Integer scoreB = 0;

    /** 1=A队胜 2=B队胜 */
    @NotNull(message = "胜方不能为空")
    @Min(1)
    @Max(2)
    private Integer winner;
}
