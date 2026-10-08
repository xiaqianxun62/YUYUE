-- 球场场地字典表（供球局发布时下拉选择）
CREATE TABLE IF NOT EXISTS `court` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `name`        VARCHAR(64)   NOT NULL COMMENT '球场名称（唯一）',
  `address`     VARCHAR(255)  DEFAULT NULL COMMENT '详细地址（可选）',
  `lat`         DECIMAL(10,7) DEFAULT NULL COMMENT '纬度（可选）',
  `lng`         DECIMAL(10,7) DEFAULT NULL COMMENT '经度（可选）',
  `sort`        INT           NOT NULL DEFAULT 0 COMMENT '排序，数字越小越靠前',
  `enabled`     TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  `delete_flag` TINYINT       NOT NULL DEFAULT 0 COMMENT '0 正常 1 已删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_court_name` (`name`),
  KEY `idx_court_enabled` (`enabled`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='球场场地字典';

-- 预置几个常用场地，用户可后续在管理端增删改
INSERT INTO `court` (`name`, `address`, `sort`) VALUES
('健康城羽毛球馆', 'XX市XX区健康城体育中心', 1),
('奥体中心羽毛球馆', 'XX市XX区奥体中心',      2),
('全民健身中心',     'XX市XX区全民健身中心',   3);
