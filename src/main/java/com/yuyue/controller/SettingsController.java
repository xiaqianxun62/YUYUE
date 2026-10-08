package com.yuyue.controller;

import com.yuyue.common.ApiResponse;
import com.yuyue.common.ErrorCode;
import com.yuyue.common.OperationLog;
import com.yuyue.entity.User;
import com.yuyue.exception.BizException;
import com.yuyue.mapper.UserMapper;
import com.yuyue.service.SettingsService;
import com.yuyue.web.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站点可配置文案。Redis 存，零数据库依赖。
 * GET 公开（小程序首页未登录也能拉），PUT 仅管理员。
 */
@RestController
@RequestMapping("settings")
@RequiredArgsConstructor
@Tag(name = "站点文案", description = "可在 PC 官网设置的站点展示文案")
public class SettingsController {

    private final SettingsService settingsService;
    private final UserMapper userMapper;

    /** 首页诗句签名：公开接口，小程序首页未登录也能拉 */
    @Operation(summary = "首页诗句文案", description = "公开接口；未设置时返回默认诗句")
    @GetMapping("home-poem")
    public ApiResponse<Map<String, String>> getHomePoem() {
        return ApiResponse.ok(settingsService.getHomePoem());
    }

    /** 仅管理员可修改。前端只传 line1 / line2 / source 中需要改的字段 */
    @Operation(summary = "修改首页诗句文案", description = "仅管理员可改；未传的字段保持原值")
    @PutMapping("home-poem")
    @OperationLog("修改首页诗句文案")
    public ApiResponse<Map<String, String>> updateHomePoem(
            @RequestBody(required = false) Map<String, String> patch) {
        User me = userMapper.selectById(UserContext.require());
        if (me == null || me.getIsAdmin() == null || me.getIsAdmin() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可修改站点文案");
        }
        return ApiResponse.ok(settingsService.updateHomePoem(patch));
    }
}
