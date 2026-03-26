package com.mi.goffer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mi.goffer.dao.entity.UsersDO;
import com.mi.goffer.dto.req.UserAuthenticateReqDTO;
import com.mi.goffer.dto.req.UserUpdateEmailReqDTO;
import com.mi.goffer.dto.resp.UserAuthenticateRespDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/16 13:02
 * @Description: 用户接口层
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

    /**
     * 修改用户名
     * @param username 用户名
     */
    String updateUserName(String username);

    /**
     * 修改邮箱
     * @param reqDTO 修改邮箱参数
     */
    String updateEmail(UserUpdateEmailReqDTO reqDTO);

    /**
     * 上传头像
     * @param avatar 头像
     */
    String uploadAvatar(MultipartFile avatar);

    /**
     * 退出登录
     */
    void logout();
}
