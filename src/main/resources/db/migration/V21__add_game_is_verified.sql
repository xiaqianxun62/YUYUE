-- 球局表加「仅认证可见」标记
-- 默认 0 = 所有人可见；1 = 仅 is_verified=1 的用户可见
ALTER TABLE `game`
  ADD COLUMN is_verified TINYINT NOT NULL DEFAULT 0
  COMMENT '1=仅认证用户可见，0=所有人可见'
  AFTER hidden;
