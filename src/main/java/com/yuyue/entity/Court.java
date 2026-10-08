package com.yuyue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 球场场地字典（球局发布时下拉选择）
 */
@Data
@TableName("court")
public class Court {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 球场名称（唯一） */
    private String name;

    /** 详细地址（可选） */
    private String address;

    /** 纬度（可选） */
    private BigDecimal lat;

    /** 经度（可选） */
    private BigDecimal lng;

    /** 排序，数字越小越靠前 */
    private Integer sort;

    /** 1 启用 0 停用 */
    private Integer enabled;

    /** 逻辑删除标记：0 正常 1 已删除 */
    @TableLogic
    private Integer deleteFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
