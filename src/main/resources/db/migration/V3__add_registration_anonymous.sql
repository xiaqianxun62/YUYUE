-- 报名支持实名 / 匿名：默认匿名（对外仍是匿名昵称），实名时报名列表展示真实姓名
ALTER TABLE `registration`
  ADD COLUMN `anonymous` TINYINT NOT NULL DEFAULT 1 COMMENT '1匿名 0实名（对登录用户展示真实姓名）' AFTER `rating`;
