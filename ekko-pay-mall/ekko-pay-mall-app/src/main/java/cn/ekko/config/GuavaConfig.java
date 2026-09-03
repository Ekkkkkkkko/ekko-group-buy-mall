package cn.ekko.config;

import cn.ekko.trigger.listener.OrderPaySuccessListener;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.eventbus.EventBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GuavaConfig {

    @Bean(name = "cache")
    public Cache<String, String> cache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .build();
    }

    @Bean
    public EventBus eventBusListener(OrderPaySuccessListener listener) {
        EventBus eventBus = new EventBus();
        eventBus.register(listener);
        return eventBus;
    }

    @Bean(name = "weixinAccessToken")
    public Cache<String, String> weixinAccessToken() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)
                .build();
    }

    @Bean(name = "openidToken")
    public Cache<String, String> openidToken() {
        return CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    /** 浏览器场景与本次微信二维码 ticket 的绑定关系。 */
    @Bean(name = "sceneTicket")
    public Cache<String, String> sceneTicket() {
        return CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    /** sceneTicket 的反向索引，用于阻止新 ticket 绕过场景接口调用旧轮询接口。 */
    @Bean(name = "ticketScene")
    public Cache<String, String> ticketScene() {
        return CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

}
