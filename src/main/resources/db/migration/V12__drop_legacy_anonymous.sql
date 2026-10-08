-- 清理已废弃的匿名头像体系（匿名聊天 / 匿名报名 / 匿名名字功能已整体下线）：
-- 1) 删除匿名头像种子表
-- 2) 删除 user 表上的匿名相关列（anonymous_avatar_id、anonymous_name）
-- 代码（实体 / DTO）已不再读写这些列，此迁移仅做库表清理，不影响现有数据。

DROP TABLE IF EXISTS `anonymous_avatar`;

-- MySQL / MariaDB 不支持 DROP COLUMN IF EXISTS，用存储过程做存在性判断
DELIMITER $$
CREATE PROCEDURE yuyue_drop_col_if_exists(IN tbl VARCHAR(64), IN col VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', tbl, '` DROP COLUMN `', col, '`');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL yuyue_drop_col_if_exists('user', 'anonymous_avatar_id');
CALL yuyue_drop_col_if_exists('user', 'anonymous_name');

DROP PROCEDURE IF EXISTS yuyue_drop_col_if_exists;
