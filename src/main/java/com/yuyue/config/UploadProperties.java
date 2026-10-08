package com.yuyue.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "yuyue.upload")
public class UploadProperties {

    /** 上传文件保存目录（相对工作目录或绝对路径） */
    private String dir = "uploads";

    /** 对外暴露的 URL 前缀 */
    private String urlPrefix = "/uploads";

    /** 单文件大小上限（字节），默认 5MB */
    private long maxSize = 5 * 1024 * 1024;
}
