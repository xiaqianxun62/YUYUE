package com.yuyue.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 轮排请求
 */
@Data
public class RotationRequest {

    /** 参与轮排的人；userId 可为空（试算时会用序号占位） */
    @Valid
    @NotEmpty(message = "请至少添加一名球员")
    @Size(max = 40, message = "最多支持 40 人")
    private List<RotationPlayer> players;

    /** 场地数：每轮同时开打的场数 */
    @Min(value = 1, message = "场地数至少 1 片")
    @Max(value = 20, message = "场地数最多 20 片")
    private int courts = 2;

    /** 轮数 */
    @Min(value = 1, message = "轮数至少 1 轮")
    @Max(value = 30, message = "轮数最多 30 轮")
    private int rounds = 6;

    /** 1 单打（2 人/场） 2 双打（4 人/场），默认双打 */
    @Min(value = 1, message = "赛制参数错误")
    @Max(value = 2, message = "赛制仅支持 1 单打 / 2 双打")
    private int format = 2;

    /** 是否按积分均衡组队（每场两队实力接近） */
    private Boolean balanceStrength = true;

    @Data
    public static class RotationPlayer {

        /** 真实球局里传报名用户 id；试算时可不传 */
        private Long userId;

        /** 展示名，未填时自动生成 */
        private String label;

        /** 1男 2女 0未知 */
        private Integer gender;

        /** 积分，未填时按 1200 处理 */
        private Integer rating;
    }
}
