package com.yuyue.controller;

import com.yuyue.common.ApiResponse;
import com.yuyue.dto.AuthResponse;
import com.yuyue.dto.LoginRequest;
import com.yuyue.dto.ProfileUpdateRequest;
import com.yuyue.dto.RegisterRequest;
import com.yuyue.dto.WxLoginRequest;
import com.yuyue.service.UserService;
import com.yuyue.web.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证：注册 / 登录 / 我的信息
 */
@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
@Tag(name = "认证", description = "学号 + 姓名校园认证注册 / 登录 / 我的信息")
public class AuthController {

    private final UserService userService;

    @Operation(summary = "注册", description = "学号 + 姓名校园认证注册，成功后直接返回 JWT")
    @SecurityRequirements
    @PostMapping("register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(userService.register(req));
    }

    @Operation(summary = "登录", description = "学号 + 密码登录，返回 JWT")
    @SecurityRequirements
    @PostMapping("login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(userService.login(req));
    }

    @Operation(summary = "微信小程序登录", description = "uni.login() 拿到 code 后换 openid 登录，首次登录自动注册")
    @SecurityRequirements
    @PostMapping("wxlogin")
    public ApiResponse<AuthResponse> wxLogin(@Valid @RequestBody WxLoginRequest req) {
        return ApiResponse.ok(userService.wxLogin(req));
    }

    @Operation(summary = "完善资料", description = "微信用户补充姓名 / 性别 / 学院 / 学号")
    @PostMapping("profile")
    public ApiResponse<AuthResponse> profile(@Valid @RequestBody ProfileUpdateRequest req) {
        return ApiResponse.ok(userService.updateProfile(UserContext.require(), req));
    }

    @Operation(summary = "我的信息", description = "需携带 Authorization: Bearer <token>")
    @GetMapping("me")
    public ApiResponse<AuthResponse> me() {
        return ApiResponse.ok(userService.me(UserContext.require()));
    }

    @Operation(summary = "登出", description = "把当前 token 加入黑名单，使其立即失效")
    @PostMapping("logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.logout(authorization);
        return ApiResponse.ok();
    }
}
