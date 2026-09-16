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

    /** 报名列表（匿名昵称，不含真实姓名学号） */
    private List<RegistrationItem> registrations;

    @Data
    @Builder
    public static class RegistrationItem {
        private Long userId;
        private String anonymousName;
        private Integer gender;
        private Integer rating;
    }
}
