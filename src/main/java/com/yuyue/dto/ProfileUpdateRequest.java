package com.yuyue.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 完善 / 更新个人资料：微信用户首次登录后补姓名、性别、学院、学号
 */
@Data
public class ProfileUpdateRequest {

    @Size(max = 32, message = "姓名过长")
    private String name;

    /** 1男 2女 0未知 */
    private Integer gender;

    @Size(max = 64, message = "学院名称过长")
    private String college;

    /** 学号：校园认证用，留空表示不绑定 */
    @Size(max = 32, message = "学号过长")
    private String studentNo;
}
