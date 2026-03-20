package com.mi.goffer.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/10 21:43
 * @Description: 用户实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UsersDO {

    /**
     * 用户id
     */
    private String usersId;

    /**
     * 用户名字
     */
    private String usersName;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 用户头像
     */
    private String avatar;
}