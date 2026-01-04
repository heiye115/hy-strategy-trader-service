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
        System.out.println(decrypt("", ""));
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
