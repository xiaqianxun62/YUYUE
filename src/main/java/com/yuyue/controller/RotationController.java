package com.yuyue.controller;

import com.yuyue.common.ApiResponse;
import com.yuyue.common.OperationLog;
import com.yuyue.dto.RotationRequest;
import com.yuyue.dto.RotationResponse;
import com.yuyue.service.RotationService;
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

/**
 * 轮排：报名人数 > 同时上场人数时，按轮安排谁上场、谁休息
 */
@RestController
@RequestMapping("rotation")
@RequiredArgsConstructor
@Tag(name = "轮排", description = "场地轮转上场：按轮生成各场地对阵与休息名单，机会均等、实力均衡。轮排表展示真实姓名与个人头像")
public class RotationController {

    private final RotationService rotationService;

    @Operation(summary = "轮排试算", description = "传入球员名单、场地数、轮数，返回逐轮对阵与统计（纯计算，不落库）")
    @PostMapping("plan")
    public ApiResponse<RotationResponse> plan(@Valid @RequestBody RotationRequest req) {
        return ApiResponse.ok(rotationService.plan(req));
    }

    @Operation(summary = "按球局报名名单轮排", description = "直接读取该球局的报名人员，生成展示真实姓名与个人头像的轮排表，结果落库可计分。仅发起人可调用")
    @PostMapping("game/{gameId}")
    @OperationLog("生成球局轮排")
    public ApiResponse<RotationResponse> gamePlan(@PathVariable Long gameId,
                                                  @Valid @RequestBody RotationRequest req) {
        return ApiResponse.ok(rotationService.planForGame(UserContext.require(), gameId, req));
    }

    @Operation(summary = "查看球局已生成的轮排表", description = "重新进入轮排页面时用它还原（含已录比分，展示真实姓名与个人头像）；还没生成过时返回 data 为 null")
    @GetMapping("game/{gameId}")
    public ApiResponse<RotationResponse> gameRotation(@PathVariable Long gameId) {
        return ApiResponse.ok(rotationService.getRotationForGame(gameId));
    }
}
