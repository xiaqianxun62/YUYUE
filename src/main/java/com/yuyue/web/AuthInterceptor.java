package com.yuyue.web;

import com.yuyue.common.Constants;
import com.yuyue.exception.BizException;
import com.yuyue.common.ErrorCode;
import com.yuyue.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.regex.Pattern;

/**
 * 登录拦截器：校验 Authorization: Bearer <token>，并检查 Redis 黑名单
 * <p>
 * 公开只读接口（积分榜、球局列表 / 详情）无需登录，官网首页未登录也能展示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    /** GET /games/{id} */
    private static final Pattern GAME_DETAIL = Pattern.compile(".*/games/\\d+");

    /** GET /games/{id}/matches：编排结果与比分，未登录也要能看到 */
    private static final Pattern GAME_MATCHES = Pattern.compile(".*/games/\\d+/matches");

    /** GET /settings/home-poem：首页诗句，官网 & 小程序首页未登录也要能拉 */
    private static final Pattern HOME_POEM = Pattern.compile(".*/settings/home-poem");

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 官网首页未登录也要能读：积分榜、球局列表与详情（报名列表本身对外匿名）
        if (isPublicRead(request)) {
            return true;
        }

        String auth = request.getHeader(HEADER);
        if (auth == null || !auth.startsWith(PREFIX)) {
            log.debug("未登录访问: {} {}", request.getMethod(), request.getRequestURI());
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String token = auth.substring(PREFIX.length());

        // 登出后的 token 直接拒绝
        Boolean blacklisted = redisTemplate.opsForSet().isMember(Constants.JWT_BLACKLIST, token);
        if (Boolean.TRUE.equals(blacklisted)) {
            log.debug("已登出 token 访问: {} {}", request.getMethod(), request.getRequestURI());
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        Long userId;
        try {
            userId = jwtUtil.parse(token);
        } catch (BizException e) {
            // token 过期 / 签名不对：JwtUtil 已统一抛 UNAUTHORIZED，这里只补一条可定位的日志
            log.debug("token 无效或已过期: {} {}", request.getMethod(), request.getRequestURI());
            throw e;
        }
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserContext.set(userId);
        return true;
    }

    /**
     * 公开只读：GET /ranking、GET /games、GET /games/{id}
     * 这些接口返回的都是匿名数据（不含真实姓名），官网首页未登录时要能直接展示。
     */
    private boolean isPublicRead(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return uri.contains("/ranking") || uri.endsWith("/games")
                || uri.endsWith("/games/title-check")
                || uri.endsWith("/courts")
                || GAME_DETAIL.matcher(uri).matches()
                || GAME_MATCHES.matcher(uri).matches()
                || HOME_POEM.matcher(uri).matches();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
