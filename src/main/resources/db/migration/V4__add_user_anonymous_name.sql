-- 用户匿名昵称：对外隐藏真实姓名，格式「球友#1111」
ALTER TABLE `user`
  ADD COLUMN `anonymous_name` VARCHAR(32) NULL COMMENT '匿名昵称，如 球友#1111' AFTER `college`;

-- 存量用户按原派生规则回填（球友# + 1000 + id*31 % 9000），保证与上线前展示一致
UPDATE `user`
SET `anonymous_name` = CONCAT('球友#', 1000 + MOD(`id` * 31, 9000))
WHERE `anonymous_name` IS NULL;
