package com.yuyue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ELO 积分变动流水
 */
@Data
@TableName("rating_history")
public class RatingHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long matchId;

    private Integer ratingBefore;

    private Integer ratingAfter;

    private Integer delta;

    private Integer kFactor;

    /** 期望胜率 E */
    private BigDecimal expected;

    private LocalDateTime createTime;
}
