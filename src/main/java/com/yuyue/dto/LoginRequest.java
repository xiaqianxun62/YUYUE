package com.yuyue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "请先获取图形验证码")
    private String captchaUuid;

    @NotBlank(message = "请填写图形验证码")
    private String captchaCode;
}
