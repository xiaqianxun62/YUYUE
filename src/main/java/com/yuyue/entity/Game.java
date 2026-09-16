package com.yuyue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 球局
 */
@Data
@TableName("game")
public class Game {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 球局标题 */
    private String title;

    /** 场地 */
    private String location;

    /** 打球日期 */
    private LocalDate playDate;

    private LocalTime startTime;

    private LocalTime endTime;

    /** 人数上限 */
    private Integer maxPlayers;

    /** 0报名中 1已编排 2进行中 3已结束 4已取消 */
    private Integer status;

    /** 发布者 */
    private Long creatorId;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
