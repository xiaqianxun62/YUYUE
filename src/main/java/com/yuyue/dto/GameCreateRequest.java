package com.yuyue.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class GameCreateRequest {

    @NotBlank(message = "球局标题不能为空")
    private String title;

    private String location;

    @NotNull(message = "打球日期不能为空")
    @FutureOrPresent(message = "打球日期不能早于今天")
    private LocalDate playDate;

    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    private LocalTime endTime;

    @Min(value = 4, message = "人数上限至少 4 人")
    @Max(value = 40, message = "人数上限最多 40 人")
    private Integer maxPlayers = 12;
}
