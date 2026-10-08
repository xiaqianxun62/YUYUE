package com.yuyue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 球场场地新增 / 编辑请求（两者字段一致，复用一个 DTO）
 */
@Data
public class CourtRequest {

    @NotBlank(message = "球场名称不能为空")
    @Size(max = 64, message = "球场名称最多 64 字")
    private String name;

    @Size(max = 255, message = "地址最多 255 字")
    private String address;

    private BigDecimal lat;

    private BigDecimal lng;

    private Integer sort;

    private Integer enabled;
}
