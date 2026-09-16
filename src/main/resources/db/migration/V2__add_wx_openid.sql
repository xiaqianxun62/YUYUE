-- 微信登录支持：新增 wx_openid，并放宽学号 / 密码的必填约束
-- 说明：微信用户首次登录时只有 openid，学号与密码在完成校园认证后才有值。

ALTER TABLE `user`
    MODIFY `student_no` VARCHAR(32) NULL COMMENT '学号，微信用户未绑定时为空';

ALTER TABLE `user`
    MODIFY `password_hash` VARCHAR(128) NULL COMMENT '密码哈希(SHA-256+盐)，微信用户未设置密码时为空';

ALTER TABLE `user`
    ADD COLUMN `wx_openid` VARCHAR(64) NULL COMMENT '微信小程序 openid' AFTER `student_no`;

ALTER TABLE `user`
    ADD UNIQUE KEY `uk_wx_openid` (`wx_openid`);
