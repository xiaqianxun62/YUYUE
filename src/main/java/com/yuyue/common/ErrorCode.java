package com.yuyue.common;

import lombok.Getter;

/**
 * 业务错误码
 */
@Getter
public enum ErrorCode {

    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    USER_EXISTS(1001, "学号已注册"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PASSWORD_ERROR(1003, "学号或密码错误"),
    GAME_FULL(2001, "球局人数已满"),
    GAME_STATUS_ERROR(2002, "球局状态不允许该操作"),
    ALREADY_REGISTERED(2003, "已报名该球局"),
    NOT_REGISTERED(2004, "尚未报名该球局"),
    GAME_CANCELLED(2005, "球局已取消"),
    MATCH_SETTLED(3001, "对局已结算，不可重复上报"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
