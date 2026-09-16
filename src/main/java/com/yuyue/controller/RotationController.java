package com.yuyue.controller;

import com.yuyue.common.ApiResponse;
import com.yuyue.dto.RotationRequest;
import com.yuyue.dto.RotationResponse;
import com.yuyue.service.RotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 轮排：报名人数 > 同时上场人数时，按轮安排谁上场、谁休息
 */
@RestController
@RequestMapping("rotation")
@RequiredArgsConstructor
@Tag(name = "轮排", description = "场地轮转上场：按轮生成各场地对阵与休息名单，机会均等、实力均衡")
public class RotationController {

    private final RotationService rotationService;

    @Operation(summary = "轮排试算", description = "传入球员名单、场地数、轮数，返回逐轮对阵与统计（纯计算，不落库）")
    @PostMapping("plan")
    public ApiResponse<RotationResponse> plan(@Valid @RequestBody RotationRequest req) {
        return ApiResponse.ok(rotationService.plan(req));
    }

    @Operation(summary = "按球局报名名单轮排", description = "直接读取该球局的报名人员（对外匿名）生成轮排表")
    @PostMapping("game/{gameId}")
    public ApiResponse<RotationResponse> gamePlan(@PathVariable Long gameId,
                                                  @Valid @RequestBody RotationRequest req) {
        return ApiResponse.ok(rotationService.planForGame(gameId, req));
    }
}
