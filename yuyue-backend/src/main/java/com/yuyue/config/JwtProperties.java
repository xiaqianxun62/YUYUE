package com.yuyue.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "yuyue.jwt")
public class JwtProperties {

    /** 签名密钥 */
    private String secret;

    /** 过期时间（小时） */
    private int expireHours = 72;
}
