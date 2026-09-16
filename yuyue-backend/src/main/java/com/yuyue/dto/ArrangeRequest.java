package com.yuyue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 自动编排请求（可选）：不传 = 按性别构成自动过滤方案；
 * 传 schemeId = 强制使用指定方案；roundRobin = 循环赛（每两队各打一场）
 */
@Data
public class ArrangeRequest {

    /**
     * 编排方案：1 全单打 2 全混双 3 全男双 4 全女双 5 混搭·混双优先
     * 6 混搭·同性别优先 7 混双·纯随机 8 全随机（不分性别）；null = 自动选择
     */
    @Min(value = 1, message = "编排方案编号不合法")
    @Max(value = 8, message = "编排方案编号不合法")
    private Integer schemeId;

    /** true = 循环赛（每支队伍与其他所有队伍各打一场），默认 false = 单轮 */
    private Boolean roundRobin;
}
