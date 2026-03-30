package com.mi.goffer.common.convention.errorcode;

import lombok.AllArgsConstructor;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/03/11 22:01
 * @Description: 基础错误码
 */
@AllArgsConstructor
public enum BaseErrorCode implements IErrorCode {

    // 系统级响应码
    SUCCESS("0000000", "操作成功"),
    CLIENT_ERROR("0000100", "客户端异常"),
    SERVICE_ERROR("0000200", "服务端异常"),

    // 用户认证与注册（0001xxx）
    USER_REGISTERED("0001300", "用户已注册"),
    EMAIL_EXIST("0001301", "该邮箱已被使用"),
    USERNAME_LENGTH_ERROR("0001302", "用户名长度必须在 2-20 位之间"),
    EMAIL_FORMAT_ERROR("0001303", "邮箱格式不正确"),

    // 用户登录与 Token（0002xxx）
    USER_NOT_LOGIN("0002300", "用户未登录"),
    USER_NOT_FOUND("0002301", "用户不存在"),
    TOKEN_INVALID("0002302", "Token已失效，请重新登录"),
    PASSWORD_ERROR("0002303", "密码错误"),
    ADMIN_PASSWORD_ERROR("0002304", "管理员密码错误"),

    // 邮件验证码（0003xxx）
    EMAIL_CODE_TOO_FREQUENT("0003300", "验证码发送过于频繁，请60秒后重试"),
    EMAIL_CODE_EXPIRED("0003301", "验证码已过期，请重新获取"),
    EMAIL_CODE_ERROR("0003302", "验证码错误"),

    // 文件与媒体（0004xxx）
    AVATAR_UPLOAD_FAILED("0004300", "头像上传失败"),

    // 会话与消息（0005xxx）
    SESSION_NOT_FOUND("0005300", "会话不存在或无权访问"),
    SESSION_COMPRESS_FAILED("0005301", "对话压缩生成失败");

    // 用户注册登陆错误码
    private final String code;
    private final String message;

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
