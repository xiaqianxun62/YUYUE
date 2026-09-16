package com.yuyue.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 轮排结果：逐轮给出每片场地的对阵与本轮休息人员，并附带每人的上场 / 休息统计
 */
@Data
@Builder
public class RotationResponse {

    private int courts;

    private int rounds;

    private int format;

    private String formatName;

    /** 参与人员（含展示名与积分），前端据此把 userId 渲染成昵称 */
    private List<PlayerBrief> players;

    private List<RoundItem> rotation;

    private List<StatItem> stats;

    @Data
    @Builder
    public static class PlayerBrief {
        private Long userId;
        private String label;
        private int gender;
        private int rating;
    }

    @Data
    @Builder
    public static class RoundItem {
        private int round;
        private List<MatchItem> matches;
        /** 本轮轮空 / 休息的人 */
        private List<PlayerBrief> resting;
    }

    @Data
    @Builder
    public static class MatchItem {
        /** 场地号，从 1 开始 */
        private int court;
        private int format;
        private List<PlayerBrief> teamA;
        private List<PlayerBrief> teamB;
        /** 两队积分和，用于判断本场是否势均力敌 */
        private int sumA;
        private int sumB;
    }

    @Data
    @Builder
    public static class StatItem {
        private Long userId;
        private String label;
        private int gender;
        private int rating;
        /** 上场轮数 */
        private int playCount;
        /** 休息轮数 */
        private int restCount;
        /** 与谁搭档过、各几次 */
        private List<PartnerItem> partners;
    }

    @Data
    @Builder
    public static class PartnerItem {
        private Long userId;
        private String label;
        private int times;
    }
}
