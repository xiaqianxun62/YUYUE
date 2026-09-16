package com.yuyue.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ArrangeResponse {

    private Long gameId;

    private Integer schemeId;

    private String schemeName;

    /** true = 本次为循环赛编排 */
    private Boolean roundRobin;

    private List<MatchItem> matches;

    @Data
    @Builder
    public static class MatchItem {
        private Integer format;
        private List<Long> teamA;
        private List<Long> teamB;
    }
}
