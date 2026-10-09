package com.yuyue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 球局
 */
@Data
@TableName("game")
public class Game {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 球局标题 */
    private String title;

    /** 发布模式：0 预报名（定时间地点） 1 现场报名（只约人数与场地） */
    private Integer mode;

    /** 场地 */
    private String location;

    /** 关联球场字典（court 表），可空兼容历史数据 */
    private Long courtId;

    /** 球局备注 / 说明（可选，发起人给的补充信息，如带球拍、场地费 AA 等） */
    private String remark;

    /** 打球日期 */
    private LocalDate playDate;

    private LocalTime startTime;

    private LocalTime endTime;

    /** 人数上限 */
    private Integer maxPlayers;

    /** 封面图 URL（/uploads/xxx，可空；为空时前端用默认渐变） */
    private String cover;

    /** 场地数量（同时开几块场地） */
    private Integer courtCount;

    /** 编排方案 1-8，null = 尚未编排 */
    private Integer schemeId;

    /** 0报名中 1已编排 2已结束 3已取消 */
    private Integer status;

    /** 发布者 */
    private Long creatorId;

    /** 1=已隐藏（不在首页列表显示） */
    private Integer hidden;

    /** 1=仅认证用户可见，0=所有人可见 */
    private Integer isVerified;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
