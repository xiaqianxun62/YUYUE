package com.yuyue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuyue.entity.Game;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GameMapper extends BaseMapper<Game> {

    /**
     * 按 id 加行锁读取球局（SELECT ... FOR UPDATE）。
     * 报名接口用它串行化同一球局的并发报名，关闭「检查人数-再插入」竞态导致的超员窗口。
     * 事务结束（提交 / 回滚）时锁自动释放。
     */
    @Select("SELECT * FROM game WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Game selectByIdForUpdate(@Param("id") Long id);
}
