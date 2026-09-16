package com.yuyue.controller;

import com.yuyue.common.ApiResponse;
import com.yuyue.dto.RankingItem;
import com.yuyue.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 积分榜：官网与小程序同源（Redis ZSet 热点读）
 */
@RestController
@RequestMapping("ranking")
@RequiredArgsConstructor
@Tag(name = "积分榜", description = "Redis ZSet 热点读，官网与小程序同源")
public class RankingController {

    private final RankingService rankingService;

    @Operation(summary = "积分榜 Top N", description = "默认取前 10 名")
    @GetMapping
    public ApiResponse<List<RankingItem>> top(@RequestParam(defaultValue = "10") int n) {
        return ApiResponse.ok(rankingService.topN(n));
    }
}
