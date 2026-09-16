package com.yuyue.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuyue.common.ErrorCode;
import com.yuyue.config.WxProperties;
import com.yuyue.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 微信小程序登录：拿 code 换 openid（code2Session）
 * <p>
 * 开发联调：code 以 mock 开头且开关打开时，直接返回稳定伪 openid，
 * 免去了「没有真实 appid 就完全登录不了」的窘境。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatService {

    private static final String JSCODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String MOCK_PREFIX = "mock";

    private final WxProperties wxProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * @return openid
     * @throws BizException 参数错误 / 微信接口返回错误
     */
    public String code2Openid(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少微信登录 code");
        }
        String trimmed = code.trim();

        if (trimmed.startsWith(MOCK_PREFIX)) {
            if (!wxProperties.isMockEnabled()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "未开启 mock 登录");
            }
            log.warn("微信 mock 登录: code={}", trimmed);
            return "mock_" + trimmed;
        }

        String appid = wxProperties.getAppid();
        String secret = wxProperties.getSecret();
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未配置小程序 appid / secret，无法微信登录");
        }

        String url = JSCODE2SESSION
                + "?appid=" + encode(appid)
                + "&secret=" + encode(secret)
                + "&js_code=" + encode(trimmed)
                + "&grant_type=authorization_code";

        String body;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            body = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        } catch (Exception e) {
            log.error("调用微信 code2Session 失败", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信登录失败，请稍后重试");
        }

        try {
            JsonNode json = objectMapper.readTree(body);
            String openid = json.path("openid").asText(null);
            int errcode = json.path("errcode").asInt(0);
            if (errcode != 0 || openid == null || openid.isBlank()) {
                log.error("微信 code2Session 返回错误: {}", body);
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "微信登录失败：" + json.path("errmsg").asText("未知错误"));
            }
            return openid;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析微信返回失败: {}", body, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信登录失败");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
