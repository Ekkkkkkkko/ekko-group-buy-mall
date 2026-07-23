package cn.ekko.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口限流切面配置。
 */
@Configuration(proxyBeanMethods = false)
public class RateLimiterConfig {

    @Bean
    public RateLimiterAOP rateLimiterAOP() {
        return new RateLimiterAOP();
    }

}
