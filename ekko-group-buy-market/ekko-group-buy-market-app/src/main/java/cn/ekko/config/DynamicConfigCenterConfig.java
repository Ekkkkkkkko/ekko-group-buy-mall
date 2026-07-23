package cn.ekko.config;

import cn.ekko.types.dynamic.config.center.domain.model.valobj.AttributeVO;
import cn.ekko.types.dynamic.config.center.types.common.Constants;
import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Boot 3 原生 DCC 配置，替代外部 wrench starter 的 spring.factories 自动配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        DynamicConfigCenterProperties.class,
        DynamicConfigCenterRegisterProperties.class
})
public class DynamicConfigCenterConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamicConfigCenterConfig.class);

    @Bean("dynamicConfigRedissonClient")
    public RedissonClient dynamicConfigRedissonClient(DynamicConfigCenterRegisterProperties properties) {
        Config config = new Config();
        config.setCodec(JsonJacksonCodec.INSTANCE);

        org.redisson.config.SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
                .setConnectionPoolSize(properties.getPoolSize())
                .setConnectionMinimumIdleSize(properties.getMinIdleSize())
                .setIdleConnectionTimeout(properties.getIdleTimeout())
                .setConnectTimeout(properties.getConnectTimeout())
                .setRetryAttempts(properties.getRetryAttempts())
                .setRetryInterval(properties.getRetryInterval())
                .setPingConnectionInterval(properties.getPingInterval())
                .setKeepAlive(properties.isKeepAlive());

        if (StringUtils.hasText(properties.getPassword())) {
            singleServerConfig.setPassword(properties.getPassword());
        }

        RedissonClient redissonClient = Redisson.create(config);
        log.info("DCC Redis client initialized. host={} poolSize={} active={}", properties.getHost(), properties.getPoolSize(), !redissonClient.isShutdown());
        return redissonClient;
    }

    @Bean
    public DynamicConfigCenterService dynamicConfigCenterService(DynamicConfigCenterProperties properties,
                                                                 @Qualifier("dynamicConfigRedissonClient") RedissonClient dynamicConfigRedissonClient) {
        return new DynamicConfigCenterService(properties, dynamicConfigRedissonClient);
    }

    @Bean
    public static BeanPostProcessor dynamicConfigCenterBeanPostProcessor(DynamicConfigCenterService dynamicConfigCenterService) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                return dynamicConfigCenterService.proxyObject(bean);
            }
        };
    }

    @Bean
    public MessageListener<AttributeVO> dynamicConfigCenterAdjustListener(DynamicConfigCenterService dynamicConfigCenterService) {
        return (charSequence, attributeVO) -> {
            try {
                log.info("DCC config attribute:{} value:{}", attributeVO.getAttribute(), attributeVO.getValue());
                dynamicConfigCenterService.adjustAttributeValue(attributeVO);
            } catch (Exception e) {
                log.error("DCC config attribute:{} value:{}", attributeVO.getAttribute(), attributeVO.getValue(), e);
            }
        };
    }

    @Bean(name = "dynamicConfigCenterRedisTopic")
    public RTopic dynamicConfigCenterRedisTopic(DynamicConfigCenterProperties properties,
                                                @Qualifier("dynamicConfigRedissonClient") RedissonClient dynamicConfigRedissonClient,
                                                MessageListener<AttributeVO> dynamicConfigCenterAdjustListener) {
        RTopic topic = dynamicConfigRedissonClient.getTopic(Constants.getTopic(properties.getSystem()));
        topic.addListener(AttributeVO.class, dynamicConfigCenterAdjustListener);
        return topic;
    }

}
