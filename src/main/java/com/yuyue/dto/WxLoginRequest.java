package com.yuyue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信小程序登录：前端 uni.login() 拿到的 code
 */
@Data
public class WxLoginRequest {

    @NotBlank(message = "缺少微信登录 code")
    private String code;

    /** 微信昵称（可选，首次登录时用作展示名） */
    private String nickName;

    /** 微信头像（可选） */
    private String avatarUrl;
}
