package com.hy.common.utils.str;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;

public class SecureTextEncryptor {

    private static StandardPBEStringEncryptor build(String password) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setAlgorithm("PBEWithHMACSHA512AndAES_256");
        encryptor.setIvGenerator(new RandomIvGenerator());
        encryptor.setKeyObtentionIterations(100_000);
        encryptor.setPassword(password);
        return encryptor;
    }

    /**
     * 加密
     **/
    public static String encrypt(String password, String plainText) {
        return build(password).encrypt(plainText);
    }

    /**
     * 解密
     **/
    public static String decrypt(String password, String cipherText) {
        return build(password).decrypt(cipherText);
    }
}
