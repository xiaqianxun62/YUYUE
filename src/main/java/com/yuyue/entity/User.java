package com.yuyue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户（学号 + 姓名校园认证）
 */
@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学号 */
    private String studentNo;

    /** 微信小程序 openid，未绑定微信时为空 */
    private String wxOpenid;

    /** 姓名 */
    private String name;

    /** 0未知 1男 2女 */
    private Integer gender;

    /** 学院 */
    private String college;

    private String passwordHash;

    /** ELO 积分 */
    private Integer rating;

    /** 历史场次 */
    private Integer gamesPlayed;

    private Integer winCount;

    private Integer lossCount;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
