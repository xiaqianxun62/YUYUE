-- 「学号」概念改为通用「账号」，同时移除学院字段。
-- student_no(NOT NULL) 改名为 account；微信注册的用户原本没有账号，
-- 故新列允许 NULL（登录方式为微信 openid；账号密码登录需先绑定账号）。

-- 唯一索引先删：CHANGE 列后旧索引名 uk_student_no 会保留，统一重建为 uk_account
ALTER TABLE `user` DROP INDEX `uk_student_no`;

ALTER TABLE `user`
  CHANGE COLUMN `student_no` `account` VARCHAR(32) DEFAULT NULL COMMENT '登录账号';

-- 历史空串（早期非严格模式下微信用户可能写入 ''）归一为 NULL，
-- 否则多条 '' 会在下面的唯一键上冲突；NULL 在唯一索引中可重复
UPDATE `user` SET `account` = NULL WHERE `account` = '';

ALTER TABLE `user` ADD UNIQUE KEY `uk_account` (`account`);

ALTER TABLE `user` DROP COLUMN `college`;
