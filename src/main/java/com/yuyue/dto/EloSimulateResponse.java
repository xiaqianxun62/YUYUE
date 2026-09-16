package com.yuyue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * ELO 试算结果：命中的编排方案 + 每场对阵的积分变化
 */
@Data
@Builder
public class EloSimulateResponse {

    private Integer schemeId;

    private String schemeName;

    /** 参与试算的球员（含各自 K 因子） */
    private List<SimPlayer> players;

    private List<SimMatch> matches;

    @Data
    @Builder
    public static class SimPlayer {
        private Long id;
        /** 1男 2女 */
        private Integer gender;
        private String label;
        /** 初始积分 */
        private Integer rating;
        /** 打完全部场次后的积分（循环赛下与初始值不同） */
        private Integer finalRating;
        private Integer gamesPlayed;
        /** 不加注解时 Jackson 会把 getKFactor() 序列化成 kfactor（连续大写字母的特殊处理） */
        @JsonProperty("kFactor")
        private Integer kFactor;
    }

    @Data
    @Builder
    public static class SimMatch {
        private Integer format;
        /** 单打 / 男双 / 女双 / 混双 */
        private String formatName;
        /** 1=A队 2=B队 */
        private Integer winner;
        private List<SimSide> teamA;
        private List<SimSide> teamB;
        /** A 队组合期望胜率 */
        private Double expectedA;
        private Double expectedB;
    }

    @Data
    @Builder
    public static class SimSide {
        private Long id;
        private String label;
        private Integer ratingBefore;
        private Integer delta;
        private Integer ratingAfter;
        @JsonProperty("kFactor")
        private Integer kFactor;
        private Double expected;
    }
}
