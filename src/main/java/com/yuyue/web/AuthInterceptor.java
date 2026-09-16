package com.yuyue.web;

import com.yuyue.common.Constants;
import com.yuyue.exception.BizException;
import com.yuyue.common.ErrorCode;
import com.yuyue.util.JwtUtil;
import lombok.RequiredArgsConstructor;
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
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    /** GET /games/{id} */
    private static final Pattern GAME_DETAIL = Pattern.compile(".*/games/\\d+");

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
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String token = auth.substring(PREFIX.length());

        // 登出后的 token 直接拒绝
        Boolean blacklisted = redisTemplate.opsForSet().isMember(Constants.JWT_BLACKLIST, token);
        if (Boolean.TRUE.equals(blacklisted)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        Long userId = jwtUtil.parse(token);
        UserContext.set(userId);
        return true;
    }

    /**
     * 公开只读：GET /ranking、GET /games、GET /games/{id}
     * 这些接口返回的都是匿名数据（不含姓名学号），官网首页未登录时要能直接展示。
     */
    private boolean isPublicRead(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return uri.endsWith("/ranking") || uri.endsWith("/games") || GAME_DETAIL.matcher(uri).matches();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
