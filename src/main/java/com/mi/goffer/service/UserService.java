package com.mi.goffer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mi.goffer.dao.entity.UsersDO;
import com.mi.goffer.dto.req.UserAuthenticateReqDTO;
import com.mi.goffer.dto.resp.UserAuthenticateRespDTO;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/16 13:02
 * @Description:
 */

public interface UserService extends IService<UsersDO> {

    /**
     * 发送邮箱验证码
     * @param email 邮箱
     */
    void sendEmailCode(String email);
    /**
     * 用户身份验证
     * @param reqDTO 验证参数
     * @return UserAuthenticateRespDTO
     */
    UserAuthenticateRespDTO authenticate(UserAuthenticateReqDTO reqDTO);


}
