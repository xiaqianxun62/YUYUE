package com.yuyue.config;

import com.yuyue.web.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 只放行这三个"换 token"的入口；/auth/me、/auth/profile、/auth/logout
                        // 必须走拦截器，否则 UserContext 不会被填充，必然报未登录
                        "/auth/register",
                        "/auth/login",
                        "/auth/wxlogin",
                        "/error",
                        "/actuator/**",
                        // ELO 试算：纯计算工具，无需登录（不读库不写库）
                        "/elo/**",
                        // 轮排：纯计算，且球局报名名单本身对外匿名
                        "/rotation/**",
                        // Swagger / OpenAPI 文档资源必须放行，否则会被登录拦截
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/webjars/**"
                );
    }

    // 跨域统一由 CorsConfig 的 CorsFilter 处理（含预检 OPTIONS），
    // 这里不再配 addCorsMappings，避免同一条响应上出现重复的 CORS 响应头。
}
