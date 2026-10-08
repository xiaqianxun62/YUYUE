ALTER TABLE game ADD COLUMN court_id BIGINT NULL COMMENT '关联球场字典（court 表），可空兼容历史数据';
CREATE INDEX idx_game_court ON game(court_id);
