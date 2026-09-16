package com.yuyue.controller;

import com.yuyue.common.ApiResponse;
import com.yuyue.dto.MatchReportRequest;
import com.yuyue.service.MatchService;
import com.yuyue.web.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 对局：上报结果（结算异步走 Kafka）
 */
@RestController
@RequestMapping("matches")
@RequiredArgsConstructor
@Tag(name = "对局", description = "上报对局结果，ELO 结算异步走 Kafka")
public class MatchController {

    private final MatchService matchService;

    @Operation(summary = "上报对局结果", description = "返回 matchId，积分结算由 Kafka 消费者异步完成")
    @PostMapping
    public ApiResponse<Map<String, Long>> report(@Valid @RequestBody MatchReportRequest req) {
        UserContext.require();
        Long matchId = matchService.report(req);
        return ApiResponse.ok(Map.of("matchId", matchId));
    }
}
