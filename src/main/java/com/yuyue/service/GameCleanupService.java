package com.yuyue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuyue.common.Constants;
import com.yuyue.entity.Game;
import com.yuyue.mapper.GameMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 球局兜底清理：把已过打球时间、但状态还停在「报名中 / 已编排」的球局自动置为「已结束」。
 * 幂等：只处理 status IN (OPEN, ARRANGED)，已结束/已取消的自然不受影响。
 * 每 5 分钟跑一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameCleanupService {

    private final GameMapper gameMapper;

    /** 每 5 分钟整点跑一次（秒=0，分=0/5/10/...） */
    @Scheduled(cron = "0 */5 * * * ?")
    public void expireStaleGames() {
        LocalDateTime now = LocalDateTime.now();
        List<Game> candidates = gameMapper.selectList(
                new LambdaQueryWrapper<Game>()
                        .in(Game::getStatus, Constants.GAME_STATUS_OPEN, Constants.GAME_STATUS_ARRANGED));
        if (candidates.isEmpty()) return;

        int finished = 0;
        for (Game g : candidates) {
            if (isExpired(g, now)) {
                g.setStatus(Constants.GAME_STATUS_FINISHED);
                gameMapper.updateById(g);
                finished++;
            }
        }
        if (finished > 0) {
            log.info("[球局清理] 自动结束过期球局: {} 局", finished);
        }
    }

    /** 判断一局是否"应该已结束" */
    static boolean isExpired(Game g, LocalDateTime now) {
        LocalDate date = g.getPlayDate();
        if (date == null) return false; // 异常数据不动
        LocalTime end = g.getEndTime();
        LocalTime start = g.getStartTime();

        if (end != null) {
            // 有明确结束时间：playDate + endTime 已过
            return LocalDateTime.of(date, end).isBefore(now);
        }
        if (start != null) {
            // 只有开始时间：默认打 3 小时
            return LocalDateTime.of(date, start).plusHours(3).isBefore(now);
        }
        // 两者都没有（一般是现场报名 mode=1，playDate 被 GameService 自动设成今天）：
        // 跳过今天的；非今天的超过 3 天视为过期
        if (date.isEqual(LocalDate.now())) return false;
        return date.plusDays(3).isBefore(now.toLocalDate());
    }
}
