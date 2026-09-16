package com.yuyue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuyue.common.Constants;
import com.yuyue.common.ErrorCode;
import com.yuyue.dto.ArrangeRequest;
import com.yuyue.dto.ArrangeResponse;
import com.yuyue.dto.GameCreateRequest;
import com.yuyue.dto.GameResponse;
import com.yuyue.engine.ArrangeEngine;
import com.yuyue.engine.ArrangeEngine.ArrangeResult;
import com.yuyue.engine.ArrangeEngine.Player;
import com.yuyue.entity.Game;
import com.yuyue.entity.Registration;
import com.yuyue.entity.User;
import com.yuyue.event.RegistrationEvent;
import com.yuyue.exception.BizException;
import com.yuyue.mapper.GameMapper;
import com.yuyue.mapper.RegistrationMapper;
import com.yuyue.kafka.EventProducer;
import com.yuyue.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameMapper gameMapper;
    private final RegistrationMapper registrationMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final ArrangeEngine arrangeEngine;
    private final EventProducer eventProducer;

    /** 发布球局 */
    public GameResponse create(Long creatorId, GameCreateRequest req) {
        Game game = new Game();
        game.setTitle(req.getTitle());
        game.setLocation(req.getLocation());
        game.setPlayDate(req.getPlayDate());
        game.setStartTime(req.getStartTime());
        game.setEndTime(req.getEndTime());
        game.setMaxPlayers(req.getMaxPlayers());
        game.setStatus(Constants.GAME_STATUS_OPEN);
        game.setCreatorId(creatorId);
        gameMapper.insert(game);
        log.info("球局发布: id={}, title={}, creator={}", game.getId(), game.getTitle(), creatorId);
        return buildGameResponse(game);
    }

    /** 球局列表（按日期倒序） */
    public List<GameResponse> list() {
        return gameMapper.selectList(
                        new LambdaQueryWrapper<Game>().orderByDesc(Game::getPlayDate))
                .stream().map(this::buildGameResponse).toList();
    }

    /** 球局详情 */
    public GameResponse detail(Long gameId) {
        return buildGameResponse(requireGame(gameId));
    }

    /**
     * 匿名报名：校验满员/重复，落库 + Redis 计数 + 发 Kafka 报名事件（机器人消费后同步微信群接龙）
     */
    @Transactional
    public GameResponse register(Long userId, Long gameId) {
        Game game = requireGame(gameId);
        if (game.getStatus() != Constants.GAME_STATUS_OPEN) {
            throw new BizException(ErrorCode.GAME_STATUS_ERROR);
        }
        if (registrationMapper.selectCount(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getGameId, gameId)
                .eq(Registration::getUserId, userId)) > 0) {
            throw new BizException(ErrorCode.ALREADY_REGISTERED);
        }

        long count = registrationMapper.selectCount(
                new LambdaQueryWrapper<Registration>().eq(Registration::getGameId, gameId));
        if (count >= game.getMaxPlayers()) {
            throw new BizException(ErrorCode.GAME_FULL);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        Registration reg = new Registration();
        reg.setGameId(gameId);
        reg.setUserId(userId);
        reg.setGender(user.getGender());
        reg.setRating(user.getRating());
        registrationMapper.insert(reg);

        // Redis 计数（计数器作热点读缓存）
        redisTemplate.opsForHash().increment(Constants.GAME_REG_COUNT,
                String.valueOf(gameId), 1);

        // Kafka 报名事件 → 机器人同步微信群接龙
        eventProducer.sendRegistration(RegistrationEvent.of(
                gameId, userId, UserService.anonymousName(userId), user.getGender()));

        log.info("报名成功: game={}, user={}", gameId, userId);
        return buildGameResponse(game);
    }

    /**
     * 自动编排：读取报名人员，默认引擎按性别构成过滤方案；
     * 传 schemeId 时强制使用指定方案（如 2 = 全混双），roundRobin = 循环赛
     */
    @Transactional
    public ArrangeResponse arrange(Long operatorId, Long gameId, ArrangeRequest req) {
        Game game = requireGame(gameId);
        if (game.getStatus() != Constants.GAME_STATUS_OPEN) {
            throw new BizException(ErrorCode.GAME_STATUS_ERROR);
        }
        List<Registration> regs = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>().eq(Registration::getGameId, gameId));
        if (regs.size() < 4) {
            throw new BizException(ErrorCode.PARAM_ERROR, "报名人数不足 4 人，无法编排");
        }

        List<Player> players = regs.stream()
                .map(r -> new Player(r.getUserId(), r.getGender(), r.getRating()))
                .toList();
        boolean roundRobin = req != null && Boolean.TRUE.equals(req.getRoundRobin());
        ArrangeResult result = req != null && req.getSchemeId() != null
                ? arrangeEngine.arrangeByScheme(players, req.getSchemeId(), roundRobin)
                : arrangeEngine.arrange(players, roundRobin);

        game.setStatus(Constants.GAME_STATUS_ARRANGED);
        gameMapper.updateById(game);

        return ArrangeResponse.builder()
                .gameId(gameId)
                .schemeId(result.schemeId())
                .schemeName(result.schemeName())
                .roundRobin(roundRobin)
                .matches(result.matches().stream()
                        .map(m -> ArrangeResponse.MatchItem.builder()
                                .format(m.format())
                                .teamA(m.teamA())
                                .teamB(m.teamB())
                                .build())
                        .toList())
                .build();
    }

    private Game requireGame(Long gameId) {
        Game game = gameMapper.selectById(gameId);
        if (game == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "球局不存在");
        }
        return game;
    }

    private GameResponse buildGameResponse(Game game) {
        List<Registration> regs = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>().eq(Registration::getGameId, game.getId()));
        return GameResponse.builder()
                .id(game.getId())
                .title(game.getTitle())
                .location(game.getLocation())
                .playDate(game.getPlayDate())
                .startTime(game.getStartTime())
                .endTime(game.getEndTime())
                .maxPlayers(game.getMaxPlayers())
                .status(game.getStatus())
                .creatorId(game.getCreatorId())
                .registeredCount(regs.size())
                .registrations(regs.stream()
                        .map(r -> GameResponse.RegistrationItem.builder()
                                .userId(r.getUserId())
                                .anonymousName(UserService.anonymousName(r.getUserId()))
                                .gender(r.getGender())
                                .rating(r.getRating())
                                .build())
                        .toList())
                .build();
    }
}
