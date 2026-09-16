package com.yuyue.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置：appid / secret 从环境变量或 application.yml 读取
 */
@Data
@Component
@ConfigurationProperties(prefix = "yuyue.wx")
public class WxProperties {

    /** 小程序 appid，为空则只能走 mock 登录 */
    private String appid = "";

    /** 小程序 secret */
    private String secret = "";

    /**
     * 是否允许 mock 登录：开发者工具没有真实 appid 时，
     * 前端传以 mock 开头的 code 也能登录（仅限开发联调，生产务必关闭）
     */
    private boolean mockEnabled = true;
}
