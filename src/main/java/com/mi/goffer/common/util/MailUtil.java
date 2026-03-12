package com.mi.goffer.common.util;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Random;

import static com.mi.goffer.common.constant.MailConstant.FROM_EMAIL;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/12 22:40
 * @Description: 邮件工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailUtil {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final JavaMailSender javaMailSender;

    /**
     * 生成六位数字验证码
     */
    public String generateCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }

    /**
     * 发送验证码邮件
     * @param to 收件人邮箱
     * @return 发送的验证码
     */
    public String sendVerificationCode(String to) {
        String code = generateCode();

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(FROM_EMAIL);
            helper.setTo(to);
            helper.setSubject("验证码");
            helper.setText("您的验证码是：" + code + "，有效期5分钟，请勿泄露给他人。", false);

            javaMailSender.send(message);
            log.info("验证码邮件发送成功: {}", to);
        } catch (MessagingException e) {
            log.error("验证码邮件发送失败: {}", e.getMessage());
            throw new RuntimeException("邮件发送失败", e);
        }

        return code;
    }
}
