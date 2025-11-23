package com.hy;

import com.hy.common.utils.json.JsonUtil;
import com.hy.modules.contract.entity.MartingaleStrategyConfig;
import com.hy.modules.contract.service.MartingaleStrategyService;
import org.jasypt.util.text.AES256TextEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;

@SpringBootTest
class BitgetTests {


    @Value("${bitget.base-url}")
    private String baseUrl;

    @Value("${bitget.ws-public-url}")
    private String wsPublicUrl;

    @Value("${bitget.ws-private-url}")
    private String wsPrivateUrl;

    @Value("${spring.mail.password}")
    private String mailPassword;


    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void t1() throws IOException, InterruptedException {
        System.out.println(mailPassword);
        System.out.println("明文apiKey: ");
        System.out.println("明文passphrase: ");
        System.out.println("明文secretKey: ");
        AES256TextEncryptor textEncryptor = new AES256TextEncryptor();
        textEncryptor.setPassword("*"); // 这是加密密钥，等下会用于解密
        String encrypted = textEncryptor.encrypt("passphrase"); // 明文密码
        System.out.println("ENC(" + encrypted + ")");
    }

    @Test
    public void t2() {
        //stringRedisTemplate.opsForHash().put("md", "hy", "123");
        Object o = stringRedisTemplate.opsForHash().values("MartingaleStrategyConfig");
        System.out.println(o);
//        Assertions.assertNotNull(o);
//        MartingaleStrategyConfig bean = JsonUtil.toBean(o.toString(), MartingaleStrategyConfig.class);
//        System.out.println(bean);

        MartingaleStrategyConfig config = MartingaleStrategyService.STRATEGY_CONFIG_MAP.get("ETHUSDT");
        System.out.println(JsonUtil.toJson(config));
    }

    public static void main(String[] args) {

    }

}
