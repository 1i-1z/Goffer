package com.mi.goffer.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mi.goffer.dao.entity.UsersDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/12 17:20
 * @Description: 用户数据库访问层接口
 */
@Mapper
public interface UsersMapper extends BaseMapper<UsersDO> {
}
