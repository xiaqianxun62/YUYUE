package com.yuyue.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 全量 HTTP 访问日志：记录每个进入 Controller 的请求的方法 / 路径 / 查询串 /
 * 操作人 / HTTP 状态 / 耗时。
 *
 * <p>注册顺序在 {@link AuthInterceptor} 之后：preHandle 时 UserContext 已填充，
 * afterCompletion 逆序执行（本拦截器先于鉴权拦截器清理上下文），因此能读到 userId；
 * 公开接口未登录时 userId 记为 "-"。鉴权失败（401）的请求由 AuthInterceptor 自己记日志。
 */
@Slf4j
@Component
public class AccessLogInterceptor implements HandlerInterceptor {

    private static final String START_NANOS = "yuyue.access.startNanos";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_NANOS, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object startAttr = request.getAttribute(START_NANOS);
        long costMs = startAttr instanceof Long start
                ? (System.nanoTime() - start) / 1_000_000
                : -1;

        Long userId = UserContext.get();
        String query = request.getQueryString();
        String uri = request.getRequestURI() + (query == null || query.isBlank() ? "" : "?" + query);

        if (ex != null || response.getStatus() >= 500) {
            log.warn("[访问日志] {} {} status={} userId={} 耗时={}ms 异常={}",
                    request.getMethod(), uri, response.getStatus(),
                    userId == null ? "-" : userId, costMs,
                    ex == null ? "-" : ex.getClass().getSimpleName());
        } else {
            log.info("[访问日志] {} {} status={} userId={} 耗时={}ms",
                    request.getMethod(), uri, response.getStatus(),
                    userId == null ? "-" : userId, costMs);
        }
    }
}
