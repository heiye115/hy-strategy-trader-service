package com.hy.common.utils.str;

import org.jasypt.util.text.AES256TextEncryptor;

/**
 * jasypt加密工具类
 *
 * @date 2024/6/24 10:28
 */
public class JasyptUtil {
    public static void main(String[] args) {
        String encrypt = "*";
        String password = "123456"; // 解密密码
        AES256TextEncryptor textEncryptor = new AES256TextEncryptor();
        // 这是加密密钥，等下会用于解密
        textEncryptor.setPassword(password);
        // 明文密码
        String encrypted = textEncryptor.encrypt(encrypt);
        System.out.println("ENC(" + encrypted + ")");
    }
}
