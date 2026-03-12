package com.mi.goffer.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mi.goffer.dao.entity.ContextsDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/12 17:27
 * @Description: 上下文数据库访问层接口
 */
@Mapper
public interface ContextsMapper extends BaseMapper<ContextsDO> {
}
