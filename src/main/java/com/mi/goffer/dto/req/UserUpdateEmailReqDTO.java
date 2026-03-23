package com.mi.goffer.dto.req;

import lombok.Data;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/19 22:19
 * @Description: 修改邮箱参数DTO
 */
@Data
public class UserUpdateEmailReqDTO {
    /**
     * 邮箱
     */
    private String email;
    /**
     * 邮箱验证码
     */
    private String emailCode;
}
