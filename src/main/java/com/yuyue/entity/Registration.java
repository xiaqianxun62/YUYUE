package com.yuyue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报名记录（含报名时性别/积分快照）
 */
@Data
@TableName("registration")
public class Registration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long gameId;

    /** 报名人（对外仅展示匿名昵称） */
    private Long userId;

    /** 1男 2女 快照 */
    private Integer gender;

    /** 报名时积分快照 */
    private Integer rating;

    /** 1 匿名 / 0 实名（Constants.ANONYMOUS_*） */
    private Integer anonymous;

    private LocalDateTime createTime;
}
