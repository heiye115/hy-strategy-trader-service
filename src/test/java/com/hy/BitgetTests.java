package com.hy;

import org.jasypt.util.text.AES256TextEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest
class BitgetTests {


    @Value("${bitget.api-key}")
    private String apiKey;
    @Value("${bitget.secret-key}")
    private String secretKey;
    @Value("${bitget.passphrase}")
    private String passphrase;
    @Value("${bitget.base-url}")
    private String baseUrl;

    @Value("${bitget.ws-public-url}")
    private String wsPublicUrl;

    @Value("${bitget.ws-private-url}")
    private String wsPrivateUrl;


    @Test
    public void t1() throws IOException, InterruptedException {
        System.out.println("明文apiKey: " + apiKey);
        System.out.println("明文passphrase: " + passphrase);
        System.out.println("明文secretKey: " + secretKey);
        AES256TextEncryptor textEncryptor = new AES256TextEncryptor();
        textEncryptor.setPassword("*"); // 这是加密密钥，等下会用于解密
        String encrypted = textEncryptor.encrypt(passphrase); // 明文密码
        System.out.println("ENC(" + encrypted + ")");
    }


    public static void main(String[] args) {

    }

}
