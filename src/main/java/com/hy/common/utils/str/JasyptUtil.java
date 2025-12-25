package com.hy.common.utils.str;

import org.jasypt.util.text.AES256TextEncryptor;

/**
 * jasypt加密工具类
 *
 * @date 2024/6/24 10:28
 */
public class JasyptUtil {
    public static void main(String[] args) {
        String content = "";
        System.out.println(encrypt("", content));
        System.out.println(decrypt("", "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"));
    }


    /**
     * 加密
     **/
    public static String encrypt(String password, String content) {
        AES256TextEncryptor textEncryptor = new AES256TextEncryptor();
        textEncryptor.setPassword(password);
        return textEncryptor.encrypt(content);
    }

    /**
     * 解密
     **/
    public static String decrypt(String password, String content) {
        AES256TextEncryptor textEncryptor = new AES256TextEncryptor();
        textEncryptor.setPassword(password);
        return textEncryptor.decrypt(content);
    }
}
