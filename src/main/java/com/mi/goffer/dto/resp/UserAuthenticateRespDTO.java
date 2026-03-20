package com.mi.goffer.dto.resp;

import lombok.Data;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/16 12:56
 * @Description:
 */
@Data
public class UserAuthenticateRespDTO {
    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 用户令牌
     */
    private String token;

    /**
     * 用户头像
     */
    private String avatar;
}
