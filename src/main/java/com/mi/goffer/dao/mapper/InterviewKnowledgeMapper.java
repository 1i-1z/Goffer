package com.mi.goffer.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mi.goffer.dao.entity.InterviewKnowledgeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/1 11:48
 * @Description: 面试知识点访问层接口
 */
@Mapper
public interface InterviewKnowledgeMapper extends BaseMapper<InterviewKnowledgeDO> {
}
