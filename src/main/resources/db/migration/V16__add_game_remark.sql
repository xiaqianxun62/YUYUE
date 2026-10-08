-- 球局备注字段
ALTER TABLE game
    ADD COLUMN remark VARCHAR(500) NULL COMMENT '球局备注 / 说明' AFTER location;
