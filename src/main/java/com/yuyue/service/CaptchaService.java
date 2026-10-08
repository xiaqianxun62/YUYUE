package com.yuyue.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

/**
 * 图形验证码：4 位随机字符 → 画在 Canvas 上返回 Base64 PNG，
 * 真值存 Redis（key = captcha:{uuid}）带 5 分钟 TTL，登录/注册时校验后立即删除（一次性）。
 */
@Component
public class CaptchaService {

    private static final String KEY_PREFIX = "captcha:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int WIDTH = 120;
    private static final int HEIGHT = 42;
    /** 去掉 O/0/I/1 等易混字符 */
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final Random RANDOM = new Random();

    private final StringRedisTemplate redis;

    public CaptchaService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 生成一张新验证码：返回 uuid + Base64 PNG（data:image/png;base64,...） */
    public CaptchaIssue issue() {
        String code = randomCode(4);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(KEY_PREFIX + uuid, code, TTL);
        String base64 = generateImage(code);
        return new CaptchaIssue(uuid, base64);
    }

    /** 校验并消费（校验成功立即删 key，防止重放） */
    public boolean verify(String uuid, String userInput) {
        if (uuid == null || uuid.isBlank() || userInput == null || userInput.isBlank()) return false;
        String key = KEY_PREFIX + uuid;
        String expected = redis.opsForValue().getAndDelete(key);
        if (expected == null) return false;
        return expected.equalsIgnoreCase(userInput.trim());
    }

    private String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String generateImage(String code) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            // 背景
            g.setColor(new Color(246, 248, 244));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 干扰线
            g.setColor(new Color(200, 210, 205));
            for (int i = 0; i < 4; i++) {
                int x1 = RANDOM.nextInt(WIDTH);
                int y1 = RANDOM.nextInt(HEIGHT);
                int x2 = RANDOM.nextInt(WIDTH);
                int y2 = RANDOM.nextInt(HEIGHT);
                g.drawLine(x1, y1, x2, y2);
            }

            // 字符
            g.setFont(new Font("Dialog", Font.BOLD, 28));
            char[] chars = code.toCharArray();
            int xStep = WIDTH / (chars.length + 1);
            for (int i = 0; i < chars.length; i++) {
                // 颜色在深绿和棕色之间随机
                g.setColor(RANDOM.nextBoolean() ? new Color(20, 102, 91) : new Color(120, 79, 27));
                int x = xStep * (i + 1) - 10;
                int y = 28 + RANDOM.nextInt(6);
                int tilt = (RANDOM.nextInt(5) - 2); // -2 ~ 2 度轻微倾斜
                Graphics2D g2 = (Graphics2D) g.create();
                g2.rotate(Math.toRadians(tilt), x + 8, y - 4);
                g2.drawString(String.valueOf(chars[i]), x, y);
                g2.dispose();
            }
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("生成验证码图片失败", e);
        }
    }

    public record CaptchaIssue(String uuid, String imageBase64) {
    }
}
