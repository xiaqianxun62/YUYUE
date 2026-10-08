package com.yuyue.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 站点可配置文案。用 Redis 存 key → value map，避免为每个文案字段建表。
 * 首次读取时若 Redis 为空返回默认值（前端可用这些默认值初始化页面）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String REDIS_KEY_HOME_POEM = "yuyue:settings:home_poem";

    /** 默认诗句（首次读取时返回，同时落库当种子） */
    private static final Map<String, String> DEFAULT_HOME_POEM = Map.of(
            "line1", "无言独上西楼，月如钩，",
            "line2", "寂寞梧桐深院锁清秋。",
            "source", "—— 五代 · 李煜《相见欢》"
    );

    private final StringRedisTemplate redis;

    /** GET /settings/home-poem（公开）：返回当前配置，空则返回默认值 */
    public Map<String, String> getHomePoem() {
        Map<Object, Object> stored = redis.opsForHash().entries(REDIS_KEY_HOME_POEM);
        if (stored == null || stored.isEmpty()) {
            return new LinkedHashMap<>(DEFAULT_HOME_POEM);
        }
        Map<String, String> out = new LinkedHashMap<>();
        DEFAULT_HOME_POEM.keySet().forEach(k -> {
            Object v = stored.get(k);
            out.put(k, v == null ? DEFAULT_HOME_POEM.get(k) : v.toString());
        });
        return out;
    }

    /**
     * PUT /settings/home-poem（登录即可改）：接收 line1 / line2 / source 三个可选字段。
     * 前端只传需要改的字段，后端做 merge（null/空字符串视为不修改）。
     */
    public Map<String, String> updateHomePoem(Map<String, String> patch) {
        // 先拿现有的（或默认的）当 base
        Map<String, String> base = new HashMap<>(getHomePoem());
        // 只覆盖前端传了的、且非空的字段
        if (patch != null) {
            for (String key : DEFAULT_HOME_POEM.keySet()) {
                String v = patch.get(key);
                if (v != null && !v.isBlank()) {
                    base.put(key, v.trim());
                }
            }
        }
        redis.opsForHash().putAll(REDIS_KEY_HOME_POEM, base);
        // 过期 90 天：防止 Redis 重启后永久存在垃圾配置
        redis.expire(REDIS_KEY_HOME_POEM, java.time.Duration.ofDays(90));
        log.info("站点文案 home-poem 更新: {}", base);
        return base;
    }
}
