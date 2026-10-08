-- 球局发布模式：预报名（定时间地点） / 现场报名（只约人数和场地）
ALTER TABLE `game`
  ADD COLUMN `mode`        TINYINT NOT NULL DEFAULT 0 COMMENT '0预报名 1现场报名' AFTER `title`,
  ADD COLUMN `court_count` INT     NOT NULL DEFAULT 2 COMMENT '场地数量' AFTER `max_players`,
  ADD COLUMN `scheme_id`   TINYINT DEFAULT NULL COMMENT '编排方案 1-8，null=尚未编排' AFTER `status`;

-- 现场报名的球局没有日期 / 时间（人齐了现场开打），地点也允许留空
ALTER TABLE `game`
  MODIFY `play_date`  DATE DEFAULT NULL COMMENT '打球日期（现场报名球局为空）',
  MODIFY `start_time` TIME DEFAULT NULL COMMENT '开始时间（现场报名球局为空）',
  MODIFY `end_time`   TIME DEFAULT NULL COMMENT '结束时间（现场报名球局为空）';
