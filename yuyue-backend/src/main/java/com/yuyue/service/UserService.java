package com.yuyue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuyue.common.ErrorCode;
import com.yuyue.config.EloProperties;
import com.yuyue.dto.AuthResponse;
import com.yuyue.dto.LoginRequest;
import com.yuyue.dto.ProfileUpdateRequest;
import com.yuyue.dto.RegisterRequest;
import com.yuyue.dto.WxLoginRequest;
import com.yuyue.entity.User;
import com.yuyue.exception.BizException;
import com.yuyue.common.Constants;
import com.yuyue.config.JwtProperties;
import com.yuyue.mapper.UserMapper;
import com.yuyue.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String SALT_CHARS = "0123456789abcdef";
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Authorization 头前缀，与 AuthInterceptor 保持一致 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final EloProperties eloProperties;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;
    private final WeChatService weChatService;

    public AuthResponse register(RegisterRequest req) {
        Long existing = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStudentNo, req.getStudentNo()));
        if (existing > 0) {
            throw new BizException(ErrorCode.USER_EXISTS);
        }

        String salt = randomSalt();
        User user = new User();
        user.setStudentNo(req.getStudentNo());
        user.setName(req.getName());
        user.setGender(req.getGender());
        user.setCollege(req.getCollege());
        user.setPasswordHash(hash(req.getPassword(), salt) + ":" + salt);
        user.setRating(eloProperties.getDefaultRating());
        user.setGamesPlayed(0);
        user.setWinCount(0);
        user.setLossCount(0);
        userMapper.insert(user);

        return buildAuthResponse(user, jwtUtil.issue(user.getId()));
    }

    public AuthResponse login(LoginRequest req) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getStudentNo, req.getStudentNo()));
        if (user == null) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }
        // 微信用户没有密码，直接拒绝学号密码登录，提示走微信登录或先设置密码
        if (user.getPasswordHash() == null || !user.getPasswordHash().contains(":")) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }
        String[] parts = user.getPasswordHash().split(":", 2);
        if (parts.length != 2 || !hash(req.getPassword(), parts[1]).equals(parts[0])) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }
        return buildAuthResponse(user, jwtUtil.issue(user.getId()));
    }

    /**
     * 微信小程序登录：code → openid，已存在则直接登录，否则自动注册并返回 JWT。
     * 首次注册的用户没有姓名学号，需后续调用 profile 接口完善。
     */
    public AuthResponse wxLogin(WxLoginRequest req) {
        String openid = weChatService.code2Openid(req.getCode());
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getWxOpenid, openid));
        boolean isNew = user == null;
        if (isNew) {
            user = new User();
            user.setWxOpenid(openid);
            user.setName(defaultName(req.getNickName(), openid));
            user.setGender(0);
            user.setRating(eloProperties.getDefaultRating());
            user.setGamesPlayed(0);
            user.setWinCount(0);
            user.setLossCount(0);
            userMapper.insert(user);
            log.info("微信用户首次登录: openid={}, userId={}", openid, user.getId());
        }
        AuthResponse resp = buildAuthResponse(user, jwtUtil.issue(user.getId()));
        resp.setNewUser(isNew);
        return resp;
    }

    /**
     * 完善资料：微信用户补充姓名 / 性别 / 学院 / 学号（学号非空时做唯一校验）
     */
    public AuthResponse updateProfile(Long userId, ProfileUpdateRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (req.getName() != null && !req.getName().isBlank()) {
            user.setName(req.getName().trim());
        }
        if (req.getGender() != null) {
            user.setGender(req.getGender());
        }
        if (req.getCollege() != null) {
            user.setCollege(req.getCollege().trim());
        }
        if (req.getStudentNo() != null && !req.getStudentNo().isBlank()) {
            String studentNo = req.getStudentNo().trim();
            Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getStudentNo, studentNo)
                    .ne(User::getId, userId));
            if (exists > 0) {
                throw new BizException(ErrorCode.USER_EXISTS);
            }
            user.setStudentNo(studentNo);
        }
        userMapper.updateById(user);
        return buildAuthResponse(user, null);
    }

    private String defaultName(String nickName, String openid) {
        if (nickName != null && !nickName.isBlank()) {
            return nickName.trim();
        }
        return "微信球友#" + Math.abs(openid.hashCode() % 9000 + 1000);
    }

    /** 当前登录用户信息 */
    public AuthResponse me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return buildAuthResponse(user, null);
    }

    /**
     * 登出：把 token 加入 Redis 黑名单，之后 AuthInterceptor 会直接拒绝该 token。
     * 黑名单是 Set，TTL 设在 key 上，取 token 的最大存活时间，到期自动清理。
     */
    public void logout(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return;
        }
        redisTemplate.opsForSet().add(Constants.JWT_BLACKLIST, token);
        redisTemplate.expire(Constants.JWT_BLACKLIST, Duration.ofHours(jwtProperties.getExpireHours()));
    }

    /**
     * 匿名展示名：对外隐藏真实姓名，如「球友#1024」
     */
    public static String anonymousName(Long userId) {
        return "球友#" + (1000 + Math.floorMod(userId * 31, 9000));
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .gender(user.getGender())
                .college(user.getCollege())
                .rating(user.getRating())
                .gamesPlayed(user.getGamesPlayed())
                .build();
    }

    private String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String randomSalt() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(SALT_CHARS.charAt(RANDOM.nextInt(SALT_CHARS.length())));
        }
        return sb.toString();
    }
}
