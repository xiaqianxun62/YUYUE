-- 用户表新增管理员标记：0 普通用户，1 管理员
ALTER TABLE `user`
    ADD COLUMN `is_admin` TINYINT NOT NULL DEFAULT 0
        COMMENT '是否管理员（1 是 0 否）'
        AFTER `avatar`;
