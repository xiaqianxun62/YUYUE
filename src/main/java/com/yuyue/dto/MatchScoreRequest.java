package com.yuyue.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 现场计分：给编排好的一场对阵录入比分（羽毛球不能平局，胜方由比分自动判定）
 */
@Data
public class MatchScoreRequest {

    @NotNull(message = "A队比分不能为空")
    @Min(value = 0, message = "比分不能为负数")
    private Integer scoreA;

    @NotNull(message = "B队比分不能为空")
    @Min(value = 0, message = "比分不能为负数")
    private Integer scoreB;
}
