-- 用户表加身份校验标记（默认未校验）
ALTER TABLE `user` ADD COLUMN is_verified TINYINT NOT NULL DEFAULT 0 COMMENT '身份校验通过标记 0未通过 1已通过';

-- 身份校验问题库：管理员在后台维护，用户随机抽一题答题
CREATE TABLE verification_question (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  question VARCHAR(500) NOT NULL COMMENT '问题文本',
  answer   VARCHAR(500) NOT NULL COMMENT '正确答案；多个答案用 | 分隔（如 答案A|答案B），比对时 trim + 忽略大小写',
  enabled  TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 1启用 0停用',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT '身份校验问题库';

-- 预置一道测试题（管理员可在后台修改/停用）
INSERT INTO verification_question (question, answer)
VALUES ('千巽是谁？（格式：非真名，仅花名）', '仙桃|xxt|XXT|张千巽');
