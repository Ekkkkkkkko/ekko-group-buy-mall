package cn.ekko.groupchat.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** Redisson 仅承担文档阶段分布式锁；父片正文仍通过 Spring Data Redis 缓存。 */
@Configuration
public class RedissonConfiguration {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(GroupChatProperties properties) {
        GroupChatProperties.Redis redis = properties.getRedis();
        Config config = new Config();
        var server = config.useSingleServer()
                .setAddress(redis.getAddress())
                .setDatabase(redis.getDatabase());
        if (StringUtils.hasText(redis.getPassword())) {
            server.setPassword(redis.getPassword());
        }
        return Redisson.create(config);
    }
}
