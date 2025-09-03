package com.hy.common.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String myEmail;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendSimpleMail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        try {
            message.setFrom(myEmail); // 发件人邮箱（必须和配置一致）
            message.setTo(to);                  // 收件人邮箱
            message.setSubject(subject);        // 邮件主题
            message.setText(content);           // 邮件内容
            mailSender.send(message);
        } catch (Exception e) {
            log.error("发送邮件失败，to:{}, subject:{}, content:{}", to, subject, content, e);
        }
    }

    /**
     * 发送 HTML 邮件
     */
    public void sendHtmlMail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // 第二个参数true表示这是一个HTML邮件
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(myEmail);  // 发件人邮箱（必须与配置一致）
            helper.setTo(to);                  // 收件人邮箱
            helper.setSubject(subject);        // 邮件主题
            helper.setText(htmlContent, true); // 第二个参数true表示HTML格式
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("发送HTML邮件失败，to:{}, subject:{}, content:{}", to, subject, htmlContent, e);
        }
    }
}
