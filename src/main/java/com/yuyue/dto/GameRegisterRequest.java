package com.yuyue.dto;

import lombok.Data;

/**
 * 报名请求。
 *
 * anonymous=true（默认，不传 body 也按此处理）：匿名报名，对外只显示「球友#xxxx」
 * anonymous=false：实名报名，报名列表对登录用户显示真实姓名；群接龙同步真名
 */
@Data
public class GameRegisterRequest {

    /** 默认 true = 匿名 */
    private Boolean anonymous = true;

    public boolean isAnonymous() {
        return !Boolean.FALSE.equals(anonymous);
    }
}
