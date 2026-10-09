package com.yuyue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 用户提交身份校验答案 */
@Data
public class AnswerSubmitRequest {

    @NotNull(message = "问题 ID 不能为空")
    private Long questionId;

    @NotBlank(message = "答案不能为空")
    @Size(max = 200, message = "答案长度不能超过 200")
    private String answer;
}
