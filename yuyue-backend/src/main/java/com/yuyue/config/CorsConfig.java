package com.yuyue.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域处理：统一交给 CorsFilter，不再依赖 WebMvcConfigurer#addCorsMappings。
 * <p>
 * 背景：只配 addCorsMappings 时，带 Authorization 的跨域请求（登录后才会出现）会先发 OPTIONS 预检，
 * 而部分路径的预检没能落到 RequestMappingHandlerMapping 上，被静态资源 handler 兜住返回 500。
 * 浏览器收到非 2xx 的预检响应就判定跨域失败，前端表现为「登录后球局 / 积分榜全部加载失败」，
 * 未登录时因为是无自定义请求头的简单请求（不触发预检）反而一切正常。
 * <p>
 * CorsFilter 工作在 DispatcherServlet 之前，预检在这里直接短路返回 200，不经过任何 handler 匹配。
 */
@Configuration
public class CorsConfig {

    /**
     * 放行前端来源：开发期放行所有来源。
     * vite dev server 配了 host: true，除了 localhost 还可能通过局域网 IP / 机器名访问，
     * 只放行 localhost 会让这些来源的预检直接 403。
     * 注意：allowCredentials(true) 时不能用 setAllowedOrigins("*")，但 allowedOriginPatterns("*") 合法，
     * Spring 会把请求的 Origin 原样回显到 Access-Control-Allow-Origin。
     * 生产环境请改成实际域名，不要保留通配。
     */
    private static final List<String> ALLOWED_ORIGINS = List.of("*");

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(ALLOWED_ORIGINS);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
