package com.yuyue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 发布球局。两种模式：
 * <ul>
 *   <li>预报名 mode=0：标题 + 地点 + 日期 + 时间 + 人数 + 场地数（提前约人）</li>
 *   <li>现场报名 mode=1：标题 + 人数 + 场地数（时间地点到现场再说，人齐后由发起人编排）</li>
 * </ul>
 * 日期 / 时间只在预报名模式下必填，因此不在 Bean Validation 里写死，由 GameService 按模式校验。
 */
@Data
public class GameCreateRequest {

    @NotBlank(message = "球局标题不能为空")
    private String title;

    /** 0 预报名（默认） 1 现场报名 */
    private Integer mode;

    private String location;

    /** 关联球场字典（court 表，可空兼容旧前端） */
    private Long courtId;

    private LocalDate playDate;

    private LocalTime startTime;

    private LocalTime endTime;

    @Min(value = 4, message = "人数上限至少 4 人")
    @Max(value = 40, message = "人数上限最多 40 人")
    private Integer maxPlayers = 12;

    /** 封面图 URL（/uploads/xxx，可空；发起者上传封面后把 URL 回传） */
    private String cover;

    /** 场地数量：同时开几块场地 */
    @Min(value = 1, message = "场地数量至少 1 片")
    @Max(value = 20, message = "场地数量最多 20 片")
    private Integer courtCount = 2;

    /** 球局备注 / 说明（可选，给球友的补充信息，最多 500 字） */
    @jakarta.validation.constraints.Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
}
