package com.yuyue.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
public class GameResponse {

    private Long id;

    private String title;

    /** 0 预报名 1 现场报名 */
    private Integer mode;

    private String location;

    /** 关联 court 表 ID */
    private Long courtId;

    /** 关联球场名称（来自 court 表，可空兼容历史数据） */
    private String courtName;

    /** 球场纬度（WGS84，可空） */
    private java.math.BigDecimal courtLat;

    /** 球场经度（WGS84，可空） */
    private java.math.BigDecimal courtLng;

    /** 球局备注 / 说明 */
    private String remark;

    private LocalDate playDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer maxPlayers;

    /** 场地数量 */
    private Integer courtCount;

    /** 编排方案 1-8（null = 尚未编排） */
    private Integer schemeId;

    private Integer status;

    private Long creatorId;

    /** 1=已隐藏（不在首页列表显示） */
    private Integer hidden;

    /** 发起人昵称（从 user 表直接查，不依赖 registrations） */
    private String creatorName;

    /** 发起人头像 URL */
    private String creatorAvatar;

    /** 发起人性别 1男 2女 */
    private Integer creatorGender;

    /** 发起人当前 ELO */
    private Integer creatorRating;

    /** 封面图 URL（可空；为空时前端用默认渐变） */
    private String cover;

    /** 当前报名人数 */
    private Integer registeredCount;

    /**
     * 报名列表：所有用户看到真实姓名。
     */
    private List<RegistrationItem> registrations;

    @Data
    @Builder
    public static class RegistrationItem {
        private Long userId;

        /** 展示名：真实姓名 */
        private String displayName;

        /** 个人头像 URL */
        private String avatar;

        private Integer gender;

        private Integer rating;
    }
}
