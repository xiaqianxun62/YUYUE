-- 系统匿名头像库：匿名报名对外只展示系统预置头像，用户不能自行上传
-- emoji 为 4 字节字符，表统一使用 utf8mb4
CREATE TABLE IF NOT EXISTS `anonymous_avatar` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(50)  NOT NULL COMMENT '头像名称',
  `emoji`       VARCHAR(16)  DEFAULT NULL COMMENT '头像表情（image_url 为空时前端按 emoji + 底色渲染）',
  `bg_color`    VARCHAR(32)  DEFAULT NULL COMMENT '头像底色',
  `image_url`   VARCHAR(500) DEFAULT NULL COMMENT '头像图片地址；为空时前端用 emoji 渲染',
  `sort_no`     INT          NOT NULL DEFAULT 0 COMMENT '展示排序，值小在前',
  `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1可用 0下线',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_enabled_sort` (`enabled`, `sort_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统匿名头像表';

-- 用户当前选中的匿名头像：只能引用 anonymous_avatar.id，不允许自定义图片
ALTER TABLE `user`
  ADD COLUMN `anonymous_avatar_id` BIGINT NULL COMMENT '选中的系统匿名头像id' AFTER `avatar`;

-- 预置 9 个羽球伙伴匿名头像（emoji + 底色，无外链依赖；后续运营可更新 image_url 换成真实图片）
INSERT INTO `anonymous_avatar` (`name`, `emoji`, `bg_color`, `sort_no`) VALUES
  ('狮子', '🦁', '#FF8C42', 1),
  ('熊猫', '🐼', '#2D2D2D', 2),
  ('狐狸', '🦊', '#E67E22', 3),
  ('猫咪', '🐱', '#9B59B6', 4),
  ('兔子', '🐰', '#F4D03F', 5),
  ('老虎', '🐯', '#E74C3C', 6),
  ('青蛙', '🐸', '#27AE60', 7),
  ('企鹅', '🐧', '#3498DB', 8),
  ('猫头鹰', '🦉', '#8E5B3A', 9);
