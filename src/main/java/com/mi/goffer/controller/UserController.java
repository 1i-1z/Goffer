package com.mi.goffer.controller;

import com.mi.goffer.common.convention.result.Result;
import com.mi.goffer.common.convention.result.Results;
import com.mi.goffer.dto.req.UserAuthenticateReqDTO;
import com.mi.goffer.dto.req.UserUpdateEmailReqDTO;
import com.mi.goffer.dto.resp.UserAuthenticateRespDTO;
import com.mi.goffer.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/14 16:06
 * @Description: 用户控制层
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/goffer/user")
public class UserController {

    private final UserService userService;


    /**
     * 发送邮箱验证码
     *
     * @param email
     * @return
     */
    @GetMapping("send-code")
    public Result<Void> sendCode(@RequestParam String email) {
        userService.sendEmailCode(email);
        return Results.success();
    }

    /**
     * 用户身份验证
     *
     * @param reqDTO
     * @return
     */
    @PostMapping("authenticate")
    public Result<UserAuthenticateRespDTO> authenticate(@RequestBody UserAuthenticateReqDTO reqDTO) {
        return Results.success(userService.authenticate(reqDTO));
    }

    /**
     * 修改用户名
     *
     * @param username
     * @return
     */
    @PutMapping("username")
    public Result<String> updateUserName(@RequestParam String username) {
        return Results.success(userService.updateUserName(username));
    }

    /**
     * 修改邮箱
     *
     * @param reqDTO
     * @return
     */
    @PutMapping("email")
    public Result<String> updateEmail(@RequestBody UserUpdateEmailReqDTO reqDTO) {
        return Results.success(userService.updateEmail(reqDTO));
    }

    /**
     * 上传头像
     *
     * @param file
     * @return
     */
    @PostMapping("avatar")
    public Result<String> uploadAvatar(@RequestParam("avatar") MultipartFile file) {
        return Results.success(userService.uploadAvatar(file));
    }

    /**
     * 退出登录
     *
     * @return
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Results.success();
    }
}
