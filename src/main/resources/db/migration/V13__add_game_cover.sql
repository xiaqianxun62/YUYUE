-- 球局封面图：发起者可上传自定义封面，首页列表卡片用它替代原深色渐变
ALTER TABLE game ADD COLUMN cover VARCHAR(255) NULL COMMENT '球局封面图 URL（/uploads/xxx）';
