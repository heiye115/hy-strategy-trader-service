package com.hy.common.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bitget配置类
 *
 * @author hy
 * @date 2025-09-13
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "bitget")
public class BitgetProperties {

    private String baseUrl;

    private String wsPublicUrl;

    private String wsPrivateUrl;
    
    private List<Account> accounts;

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class Account {
        /**
         * 账号别名
         **/
        private String name;
        private String apiKey;
        private String secretKey;
        private String passphrase;
    }
}
