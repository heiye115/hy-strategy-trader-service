package com.hy;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.bitget.custom.entity.BitgetWSMarketResp;
import com.bitget.openapi.dto.request.ws.SubscribeReq;
import com.bitget.openapi.ws.BitgetWsClient;
import com.bitget.openapi.ws.SubscriptionListener;
import com.hy.common.service.BitgetOldCustomService;
import com.hy.common.service.MailService;
import com.hy.common.utils.json.JsonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HyStratTraderServiceApplicationTests {

    @Autowired
    BitgetOldCustomService bitgetCustomService;

    @Autowired
    MailService mailService;

    @Test
    void contextLoads() throws IOException, InterruptedException {

        List<SubscribeReq> list = new ArrayList<>() {{
            add(SubscribeReq.builder().instType("USDT-FUTURES").channel("ticker").instId("ETHUSDT").build());
            //add(SubscribeReq.builder().instType("USDT-FUTURES").channel("ticker").instId("SOLUSDT").build());
            //add(SubscribeReq.builder().instType("USDT-FUTURES").channel("ticker").instId("BTCUSDT").build());
        }};
        BitgetWsClient bitgetWsClient = bitgetCustomService.subscribeWsClientContractPublic(list, new SubscriptionListener() {
            @Override
            public void onReceive(String data) {
                if (data != null) {
                    BitgetWSMarketResp marketResp = JsonUtil.toBean(data, BitgetWSMarketResp.class);
                    System.out.println("marketResp:" + marketResp);
                }
            }
        });
        new CountDownLatch(1).await();
    }


    @Test
    public void sendMail() {
        String content = """
                币种: BTCUSDT
                最高均价: 121198.6400 最低均价: 107970.6300
                最高价: 123240.0000 时间: 2025-07-14T15:00
                均价: 115320.0000
                最低价: 107400.0000 时间: 2025-07-08T10:00
                -----------------------------------
                币种: ETHUSDT
                最高均价: 3171.7620 最低均价: 2530.5110
                最高价: 3266.2500 时间: 2025-07-16T22:00
                均价: 2889.1900
                最低价: 2512.1300 时间: 2025-07-08T00:00
                -----------------------------------
                币种: SOLUSDT
                最高均价: 167.9862 最低均价: 148.9355
                最高价: 168.5880 时间: 2025-07-14T22:00
                均价: 158.1200
                最低价: 147.6520 时间: 2025-07-08T06:00
                -----------------------------------
                """;
        mailService.sendSimpleMail("ht4431@163.com", "通知标题2", content);
    }

    @Test
    public void sendMail2() {
        String to = "ht4431@163.com";
        String subject = "HTML格式测试邮件";
        String content = """
                <html>
                    <body>
                        <h2 style="color: blue;">你好，这是一个HTML格式的通知邮件</h2>
                        <p>这是内容部分，可以支持 <b>加粗</b>、<i>斜体</i>、<a href="https://example.com">超链接</a> 等</p>
                    </body>
                </html>
                """;
        mailService.sendHtmlMail(to, subject, content);
    }


    public static void main(String[] args) throws IOException {
        BigDecimal val1 = new BigDecimal("0.5");
        BigDecimal val2 = new BigDecimal("0.5");
        System.out.println(val1.compareTo(val2) > 0);

        System.out.println(StrUtil.endWith("GROKGIRLUSDT", "USDT"));

        System.out.println(DateUtil.offsetHour(DateUtil.parseDateTime("2024-02-24 20:12:22"), 24));
        System.out.println(DateUtil.compare(new Date(), DateUtil.offsetHour(DateUtil.parseDateTime("2024-02-24 20:15:40"), 24)) > 0);
    }


}
