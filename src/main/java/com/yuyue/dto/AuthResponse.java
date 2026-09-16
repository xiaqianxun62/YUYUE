package com.yuyue.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {

    private String token;

    private Long userId;

    private String name;

    private Integer gender;

    private String college;

    /** 学号：校园认证后才有，未绑定时为 null */
    private String studentNo;

    private Integer rating;

    private Integer gamesPlayed;

    /** 是否本次微信登录新建的用户（前端据此引导完善资料） */
    private Boolean newUser;
}
