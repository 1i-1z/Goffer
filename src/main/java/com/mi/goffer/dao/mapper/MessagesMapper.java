package com.mi.goffer.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mi.goffer.dao.entity.MessagesDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/12 17:26
 * @Description: 消息数据库访问层接口
 */
@Mapper
public interface MessagesMapper extends BaseMapper<MessagesDO> {
}
