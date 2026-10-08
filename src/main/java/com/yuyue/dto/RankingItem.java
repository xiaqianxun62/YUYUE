package com.yuyue.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RankingItem {

    private Long userId;

    private String name;

    private Integer rating;

    private Integer win;

    private Integer loss;

    private Integer rank;

    /** 用户头像相对路径（/uploads/xxx），为空时前端显示首字母占位 */
    private String avatar;
}
