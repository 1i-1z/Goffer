package com.mi.goffer.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mi.goffer.common.context.UserContext;
import com.mi.goffer.common.convention.exception.ClientException;
import com.mi.goffer.common.util.JwtUtil;
import com.mi.goffer.common.util.MailUtil;
import com.mi.goffer.common.util.S3Util;
import com.mi.goffer.dao.entity.UsersDO;
import com.mi.goffer.dao.mapper.UsersMapper;
import com.mi.goffer.dto.req.UserAuthenticateReqDTO;
import com.mi.goffer.dto.req.UserUpdateEmailReqDTO;
import com.mi.goffer.dto.resp.UserAuthenticateRespDTO;
import com.mi.goffer.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.mi.goffer.common.constant.RedisCacheConstant.*;
import static com.mi.goffer.common.constant.UserProfileConstant.DEFAULT_AVATAR;
import static com.mi.goffer.common.constant.UserProfileConstant.DEFAULT_USER_NAME_PREFIX;
import static com.mi.goffer.common.convention.errorcode.BaseErrorCode.*;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/16 13:03
 * @Description: 用户接口实现层
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UsersMapper, UsersDO> implements UserService {

    private final StringRedisTemplate redisTemplate;
    private final MailUtil mailUtil;
    private final JwtUtil jwtUtil;
    private final S3Util s3Util;

    /**
     * 发送邮箱验证码
     */
    @Override
    public void sendEmailCode(String email) {

        // 生成并发送验证码
        String code = mailUtil.sendVerificationCode(email);
        // 防止重复发送
        String sendLockKey = EMAIL_SEND_LOCK_KEY_PREFIX + email;
        Boolean hasLock = redisTemplate.opsForValue().setIfAbsent(sendLockKey, "1", 60, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(hasLock)) {
            throw new ClientException(EMAIL_CODE_TOO_FREQUENT);
        }

        // 存储到 Redis（有效期 5 分钟）
        String cacheKey = EMAIL_CODE_KEY_PREFIX + email;
        redisTemplate.opsForValue().set(cacheKey, code, EMAIL_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        log.info("验证码已发送到邮箱：{}", email);
    }

    /**
     * 用户身份验证
     */
    @Override
    public UserAuthenticateRespDTO authenticate(UserAuthenticateReqDTO reqDTO) {

        // 邮箱验证码校验
        validateEmailCode(reqDTO.getEmail(), reqDTO.getEmailCode());

        // 根据邮箱查询用户
        LambdaQueryWrapper<UsersDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UsersDO::getEmail, reqDTO.getEmail());
        UsersDO usersDO = baseMapper.selectOne(queryWrapper);
        if(usersDO == null){
            // 创建用户实体并设置属性
            usersDO = BeanUtil.toBean(reqDTO, UsersDO.class);
            usersDO.setUserName(generateRandomUsername());
            usersDO.setEmail(reqDTO.getEmail());
            usersDO.setAvatar(DEFAULT_AVATAR);
            baseMapper.insert(usersDO);
        }
        // 生成 token
        String token = jwtUtil.generateUserToken(usersDO.getUserId());
        // 将 token 缓存到 Redis
        cacheToken(usersDO.getUserId(), token);
        // 设置当前用户ID
        UserContext.setCurrentUserId(usersDO.getUserId());

        UserAuthenticateRespDTO respDTO = new UserAuthenticateRespDTO();
        respDTO.setUsername(usersDO.getUserName());
        respDTO.setEmail(usersDO.getEmail());
        respDTO.setAvatar(usersDO.getAvatar());
        respDTO.setToken(token);

        return respDTO;
    }

    /**
     * 修改用户名
     */
    @Override
    public String updateUserName(String username) {
        // 获取当前用户ID（由拦截器设置）
        String userId = UserContext.getCurrentUserId();

        if (userId == null) {
            throw new ClientException(USER_NOT_LOGIN);
        }
        if (username == null || username.length() < 2 || username.length() > 20) {
            throw new ClientException(USERNAME_LENGTH_ERROR);
        }
        LambdaUpdateWrapper<UsersDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UsersDO::getUserId, userId)
                .set(UsersDO::getUserName, username);
        baseMapper.update(null, updateWrapper);
        return username;
    }

    /**
     * 修改邮箱
     */
    @Override
    public String updateEmail(UserUpdateEmailReqDTO reqDTO) {
        // 获取当前用户ID（由拦截器设置）
        String userId = UserContext.getCurrentUserId();

        if (userId == null) {
            throw new ClientException(USER_NOT_LOGIN);
        }
        // 验证邮箱格式
        if (reqDTO.getEmail() == null || !reqDTO.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new ClientException(EMAIL_FORMAT_ERROR);
        }
        // 邮箱验证码校验
        validateEmailCode(reqDTO.getEmail(), reqDTO.getEmailCode());

        // 检查邮箱是否已被使用
        LambdaQueryWrapper<UsersDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UsersDO::getEmail, reqDTO.getEmail());
        UsersDO existsUser = baseMapper.selectOne(queryWrapper);
        if (existsUser != null) {
            throw new ClientException(EMAIL_EXIST);
        }
        LambdaUpdateWrapper<UsersDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UsersDO::getUserId, userId)
                .set(UsersDO::getEmail, reqDTO.getEmail());
        baseMapper.update(null, updateWrapper);
        return reqDTO.getEmail();
    }

    /**
     * 上传头像
     */
    @Override
    public String uploadAvatar(MultipartFile file) {
        // 获取当前用户ID（由拦截器设置）
        String userId = UserContext.getCurrentUserId();

        if (userId == null) {
            throw new ClientException(USER_NOT_LOGIN);
        }
        // 上传文件到S3
        String avatarUrl = s3Util.uploadAvatar(file, userId);
        if (avatarUrl == null) {
            throw new ClientException(AVATAR_UPLOAD_FAILED);
        }
        LambdaUpdateWrapper<UsersDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UsersDO::getUserId, userId)
                .set(UsersDO::getAvatar, avatarUrl);
        baseMapper.update(null, updateWrapper);
        return avatarUrl;
    }

    /**
     * 退出登录 - 删除 Redis 中的 Token
     */
    @Override
    public void logout() {
        String userId = UserContext.getCurrentUserId();
        jwtUtil.logout(userId);
    }


    /**
     *  邮箱验证码校验
     */
    private void validateEmailCode(String email, String inputCode) {


        String cacheKey = EMAIL_CODE_KEY_PREFIX + email;
        String cachedCode = redisTemplate.opsForValue().get(cacheKey);

        // 1. 邮箱验证码过期/不存在
        if (StrUtil.isBlank(cachedCode)) {
            throw new ClientException(EMAIL_CODE_EXPIRED);
        }
        // 2. 邮箱验证码错误
        if (!cachedCode.equals(inputCode)) {
            throw new ClientException(EMAIL_CODE_ERROR);
        }
        // 防止重复使用
        redisTemplate.delete(cacheKey);
        log.info("邮箱验证码校验通过，邮箱：{}", email);
    }

    /**
     * 缓存 token 到 Redis
     */
    private void cacheToken(String userId, String token) {
        String tokenKey = USER_TOKEN_KEY + userId;
        // 使用jwtConfig中的过期时间，确保与JWT token过期时间一致
        long expireSeconds = jwtUtil.getJwtConfig().getExpiration().getSeconds();
        redisTemplate.opsForValue().set(tokenKey, token, expireSeconds, TimeUnit.SECONDS);
        log.info("Token 已缓存到 Redis，userId: {}, token: {}, 有效期：{}秒", userId, token, expireSeconds);
    }

    /**
     * 生成随机用户名：用户 + 8 位随机数字
     */
    private String generateRandomUsername() {
        Random random = new Random();
        int randomNumber = random.nextInt(100000000);
        return DEFAULT_USER_NAME_PREFIX + String.format("%08d", randomNumber);
    }
}
