package com.mi.goffer.controller;

import com.mi.goffer.common.convention.result.Result;
import com.mi.goffer.common.convention.result.Results;
import com.mi.goffer.dto.req.UserAuthenticateReqDTO;
import com.mi.goffer.dto.resp.UserAuthenticateRespDTO;
import com.mi.goffer.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/14 16:06
 * @Description:
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/goffer/user")
public class UserController {

    private final UserService userService;

    @GetMapping("/send-code")
    public Result<Void> sendCode(@RequestParam String email) {
        userService.sendEmailCode(email);
        return Results.success();
    }

    @PostMapping("authenticate")
    public Result<UserAuthenticateRespDTO> authenticate(@RequestBody UserAuthenticateReqDTO reqDTO) {
        return Results.success(userService.authenticate(reqDTO));
    }
}
