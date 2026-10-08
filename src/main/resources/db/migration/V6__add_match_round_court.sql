-- 轮排表持久化：记录每场对局属于第几轮、第几片场地，
-- 这样重新进入轮排页面时能把已经生成过的轮排表完整还原出来（含比分）。
ALTER TABLE `match_game`
  ADD COLUMN `round_no` INT NOT NULL DEFAULT 0 COMMENT '所属轮次，0=非轮排生成的对阵' AFTER `format`,
  ADD COLUMN `court`    INT NOT NULL DEFAULT 0 COMMENT '场地号，从1开始' AFTER `round_no`;

ALTER TABLE `match_game`
  ADD KEY `idx_game_round` (`game_id`, `round_no`, `court`);
