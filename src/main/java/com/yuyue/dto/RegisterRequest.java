package com.yuyue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "账号不能为空")
    @Size(max = 32, message = "账号过长")
    private String account;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 32, message = "姓名过长")
    private String name;

    /** 1男 2女 */
    @NotNull(message = "性别不能为空")
    @Min(value = 1, message = "性别取值不合法")
    @Max(value = 2, message = "性别取值不合法")
    private Integer gender;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需为 6-64 位")
    private String password;

    @NotBlank(message = "请先获取图形验证码")
    private String captchaUuid;

    @NotBlank(message = "请填写图形验证码")
    private String captchaCode;
}
