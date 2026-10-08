-- 报名防超员第二道防线：恢复软删除兼容的 (game_id, user_id) 唯一约束。
-- V9 为支持「取消后再次报名」删掉了 uk_game_user，唯一性只剩应用层校验，
-- 并发报名会绕过 selectCount 检查导致超员/重复报名。
--
-- 生成列方案：active_marker 在 deleted=0（有效报名）时恒为 1，取消后为 NULL。
-- 唯一键 (game_id, user_id, active_marker) 因此保证：
--   - 同一 (game_id, user_id) 只允许一条有效报名（并发重复插入报 DuplicateKey）；
--   - 取消后再次报名不受影响（NULL 不参与唯一比较，可插入多行）。
--
-- 注意：不能用「deleted=0 时等于 id」的写法——MariaDB 禁止生成列引用
-- AUTO_INCREMENT 列（Error 1901）。IF NOT EXISTS 写法仅 MariaDB 支持（本项目即 MariaDB）。

ALTER TABLE `registration`
  ADD COLUMN IF NOT EXISTS `active_marker` BIGINT
      GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN 1 ELSE NULL END) STORED;

ALTER TABLE `registration`
  ADD UNIQUE KEY IF NOT EXISTS `uk_game_user_active` (`game_id`, `user_id`, `active_marker`);
