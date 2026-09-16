package com.yuyue.controller;

import com.yuyue.common.ApiResponse;
import com.yuyue.dto.ArrangeRequest;
import com.yuyue.dto.ArrangeResponse;
import com.yuyue.dto.GameCreateRequest;
import com.yuyue.dto.GameResponse;
import com.yuyue.service.GameService;
import com.yuyue.web.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 球局：发布 / 列表 / 详情 / 匿名报名 / 自动编排
 */
@RestController
@RequestMapping("games")
@RequiredArgsConstructor
@Tag(name = "球局", description = "发布 / 列表 / 详情 / 匿名报名 / 自动编排")
public class GameController {

    private final GameService gameService;

    @Operation(summary = "发布球局", description = "发布人取自当前登录用户")
    @PostMapping
    public ApiResponse<GameResponse> create(@Valid @RequestBody GameCreateRequest req) {
        return ApiResponse.ok(gameService.create(UserContext.require(), req));
    }

    @Operation(summary = "球局列表")
    @GetMapping
    public ApiResponse<List<GameResponse>> list() {
        return ApiResponse.ok(gameService.list());
    }

    @Operation(summary = "球局详情", description = "返回报名列表，对外匿名")
    @GetMapping("{id}")
    public ApiResponse<GameResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(gameService.detail(id));
    }

    /** 匿名报名（群外球友官网报名 → clawbot 同步微信群接龙） */
    @Operation(summary = "匿名报名", description = "写入报名并投递 Kafka，由 clawbot 同步微信群接龙")
    @PostMapping("{id}/register")
    public ApiResponse<GameResponse> register(@PathVariable Long id) {
        return ApiResponse.ok(gameService.register(UserContext.require(), id));
    }

    /** 自动编排：引擎按报名构成过滤 8 套方案生成对阵；可传 schemeId 强制指定（如 2 = 全混双） */
    @Operation(summary = "自动编排",
            description = "默认按报名人员性别构成过滤方案；传 schemeId 强制指定方案（1单打 2混双 3男双 4女双 5混搭混双优先 6混搭同性别 7混双纯随机 8全随机），传 roundRobin=true 生成循环赛")
    @PostMapping("{id}/arrange")
    public ApiResponse<ArrangeResponse> arrange(@PathVariable Long id,
                                                @RequestBody(required = false) @Valid ArrangeRequest req) {
        return ApiResponse.ok(gameService.arrange(UserContext.require(), id, req));
    }
}
