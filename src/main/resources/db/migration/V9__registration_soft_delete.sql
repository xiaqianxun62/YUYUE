-- 报名记录改软删除：取消报名不再物理删除，保留记录用于审计与报名历史。
-- 唯一键 uk_game_user 会导致「取消后再次报名」主键冲突，改为普通索引；
-- 活动报名（deleted=0）的唯一性由应用层 selectCount 校验保证（MyBatis-Plus 逻辑删除自动过滤）。

ALTER TABLE `registration`
  ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0有效 1已取消' AFTER `anonymous`;

ALTER TABLE `registration` DROP INDEX `uk_game_user`;

ALTER TABLE `registration` ADD KEY `idx_game_user` (`game_id`, `user_id`);
