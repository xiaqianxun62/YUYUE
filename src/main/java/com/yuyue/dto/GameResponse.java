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

    private String location;

    private LocalDate playDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer maxPlayers;

    private Integer status;

    private Long creatorId;

    /** 当前报名人数 */
    private Integer registeredCount;

    /**
     * 报名列表。实名报名且查看者已登录时 displayName 为真实姓名，
     * 否则（匿名报名 / 未登录访客）为匿名昵称，不泄露真实姓名学号。
     */
    private List<RegistrationItem> registrations;

    @Data
    @Builder
    public static class RegistrationItem {
        private Long userId;

        /** 匿名昵称，始终有值（未登录访客只看到这个） */
        private String anonymousName;

        /** 展示名：实名+已登录=真实姓名，其余=匿名昵称 */
        private String displayName;

        /** 1 匿名 / 0 实名 */
        private Integer anonymous;

        private Integer gender;

        private Integer rating;
    }
}
