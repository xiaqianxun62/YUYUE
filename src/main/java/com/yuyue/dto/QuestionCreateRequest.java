package com.yuyue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员新增/编辑身份校验问题 */
@Data
public class QuestionCreateRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过 500")
    private String question;

    @NotBlank(message = "答案不能为空")
    @Size(max = 500, message = "答案长度不能超过 500")
    private String answer;

    /** 1启用 0停用；不传默认启用 */
    private Integer enabled = 1;
}
