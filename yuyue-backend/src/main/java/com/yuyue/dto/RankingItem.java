package com.yuyue.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RankingItem {

    private Long userId;

    /** 匿名展示名（对外不含真实姓名） */
    private String anonymousName;

    private String name;

    private String college;

    private Integer rating;

    private Integer win;

    private Integer loss;

    private Integer rank;
}
