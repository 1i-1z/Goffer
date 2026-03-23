package com.mi.goffer.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/14 16:14
 * @Description: 用户身份验证参数DTO
 */
@Data
public class UserAuthenticateReqDTO {


    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    private String email;

    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String emailCode;
}

