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

/**
 * @Author: 1i-1z
 * @Date: 2026/3/16 13:03
 * @Description: 用户接口实现层
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UsersMapper, UsersDO> implements UserService {

    private final MailUtil mailUtil;
    private final StringRedisTemplate redisTemplate;
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
        String sendLockKey = "email:send:lock:" + email;
        Boolean hasLock = redisTemplate.opsForValue().setIfAbsent(sendLockKey, "1", 60, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(hasLock)) {
            throw new ClientException("验证码发送过于频繁，请60秒后重试");
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
            usersDO.setUsersName(generateRandomUsername());
            usersDO.setEmail(reqDTO.getEmail());
            usersDO.setAvatar("https://goffer-oss.oss-cn-guangzhou.aliyuncs.com/%E5%BE%AE%E4%BF%A1%E5%9B%BE%E7%89%87_20260319181500_415_36.jpg");
            baseMapper.insert(usersDO);
        }
        // 生成 token
        String token = jwtUtil.generateUserToken(usersDO.getUsersId());
        // 将 token 缓存到 Redis
        cacheToken(usersDO.getUsersId(), token);
        // 设置当前用户ID
        UserContext.setCurrentUserId(usersDO.getUsersId());

        UserAuthenticateRespDTO respDTO = new UserAuthenticateRespDTO();
        respDTO.setUsername(usersDO.getUsersName());
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
            throw new ClientException("用户未登录");
        }
        if (username == null || username.length() < 2 || username.length() > 20) {
            throw new ClientException("用户名长度必须在 2-20 位之间");
        }
        LambdaUpdateWrapper<UsersDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UsersDO::getUsersId, userId)
                .set(UsersDO::getUsersName, username);
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
            throw new ClientException("用户未登录");
        }
        // 验证邮箱格式
        if (reqDTO.getEmail() == null || !reqDTO.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new ClientException("邮箱格式不正确");
        }
        // 邮箱验证码校验
        validateEmailCode(reqDTO.getEmail(), reqDTO.getEmailCode());

        // 检查邮箱是否已被使用
        LambdaQueryWrapper<UsersDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UsersDO::getEmail, reqDTO.getEmail());
        UsersDO existsUser = baseMapper.selectOne(queryWrapper);
        if (existsUser != null) {
            throw new ClientException("该邮箱已被注册");
        }
        LambdaUpdateWrapper<UsersDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UsersDO::getUsersId, userId)
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
            throw new ClientException("用户未登录");
        }
        // 上传文件到S3
        String avatarUrl = s3Util.uploadAvatar(file, userId);
        if (avatarUrl == null) {
            throw new ClientException("头像上传失败");
        }
        LambdaUpdateWrapper<UsersDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UsersDO::getUsersId, userId)
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
            throw new ClientException("验证码已过期，请重新获取");
        }
        // 2. 邮箱验证码错误
        if (!cachedCode.equals(inputCode)) {
            throw new ClientException("验证码错误，请重新输入");
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
        return "用户" + String.format("%08d", randomNumber);
    }
}
