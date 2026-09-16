package com.yuyue.controller;

import com.yuyue.common.ApiResponse;
import com.yuyue.dto.EloRulesResponse;
import com.yuyue.dto.EloSimulateRequest;
import com.yuyue.dto.EloSimulateResponse;
import com.yuyue.service.EloRulesService;
import com.yuyue.service.EloSimulateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ELO 试算：给男女人数与积分，返回编排方案与每场结算（纯计算，不落库）
 */
@RestController
@RequestMapping("elo")
@RequiredArgsConstructor
@Tag(name = "ELO 试算", description = "输入男女人数与积分，模拟编排与积分结算，不写库")
public class EloController {

    private final EloSimulateService eloSimulateService;
    private final EloRulesService eloRulesService;

    @Operation(summary = "ELO 试算", description = "复用真实编排引擎与 ELO 计算器，输出方案与积分变化")
    @PostMapping("simulate")
    public ApiResponse<EloSimulateResponse> simulate(@Valid @RequestBody EloSimulateRequest req) {
        return ApiResponse.ok(eloSimulateService.simulate(req));
    }

    @Operation(summary = "ELO 规则", description = "用真实 EloCalculator 算出 K 因子档位与典型场景加减分，供前端规则说明展示")
    @GetMapping("rules")
    public ApiResponse<EloRulesResponse> rules() {
        return ApiResponse.ok(eloRulesService.rules());
    }
}
