package com.mi.goffer.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mi.goffer.dao.entity.ScoresDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/12 17:24
 * @Description: 分数数据库访问层接口
 */
@Mapper
public interface ScoresMapper extends BaseMapper<ScoresDO> {
}
