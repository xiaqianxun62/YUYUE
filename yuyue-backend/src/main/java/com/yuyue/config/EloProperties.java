package com.yuyue.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "yuyue.elo")
public class EloProperties {

    /** 新用户默认积分 */
    private int defaultRating = 1200;

    /** ≤20 场 K 值 */
    private int kNew = 40;

    /** 21-60 场 K 值 */
    private int kMid = 24;

    /** >60 场 K 值 */
    private int kPro = 16;
}
