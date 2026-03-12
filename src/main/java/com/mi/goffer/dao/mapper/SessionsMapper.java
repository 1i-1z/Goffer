package com.mi.goffer.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mi.goffer.dao.entity.SessionsDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/12 17:21
 * @Description: 会话数据库访问层接口
 */
@Mapper
public interface SessionsMapper extends BaseMapper<SessionsDO> {
}
