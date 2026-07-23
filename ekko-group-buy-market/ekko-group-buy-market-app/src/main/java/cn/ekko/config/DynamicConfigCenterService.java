package cn.ekko.config;

import cn.ekko.types.dynamic.config.center.domain.model.valobj.AttributeVO;
import cn.ekko.types.dynamic.config.center.types.annotations.DCCValue;
import cn.ekko.types.dynamic.config.center.types.common.Constants;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扫描并维护 @DCCValue 标记的动态配置字段。
 */
public class DynamicConfigCenterService {

    private final Logger log = LoggerFactory.getLogger(DynamicConfigCenterService.class);

    private final DynamicConfigCenterProperties properties;

    private final RedissonClient redissonClient;

    private final Map<String, Object> dccBeanGroup = new ConcurrentHashMap<>();

    public DynamicConfigCenterService(DynamicConfigCenterProperties properties, RedissonClient redissonClient) {
        this.properties = properties;
        this.redissonClient = redissonClient;
    }

    public Object proxyObject(Object bean) {
        Class<?> targetBeanClass = bean.getClass();
        Object targetBeanObject = bean;
        if (AopUtils.isAopProxy(bean)) {
            targetBeanClass = AopUtils.getTargetClass(bean);
            Object singletonTarget = AopProxyUtils.getSingletonTarget(bean);
            if (singletonTarget != null) {
                targetBeanObject = singletonTarget;
            }
        }

        for (Field field : targetBeanClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(DCCValue.class)) {
                continue;
            }

            DCCValue dccValue = field.getAnnotation(DCCValue.class);
            String value = dccValue.value();
            if (StringUtils.isBlank(value)) {
                throw new IllegalArgumentException(field.getName() + " @DCCValue must be configured like attribute:defaultValue");
            }

            String[] splits = value.split(Constants.SYMBOL_COLON, 2);
            String key = properties.getKey(splits[0].trim());
            String defaultValue = splits.length == 2 ? splits[1] : null;
            if (StringUtils.isBlank(defaultValue)) {
                throw new IllegalArgumentException("dcc config error " + key + " requires a default value");
            }

            try {
                RBucket<String> bucket = redissonClient.getBucket(key);
                String setValue = bucket.isExists() ? bucket.get() : defaultValue;
                if (!bucket.isExists()) {
                    bucket.set(defaultValue);
                }

                field.setAccessible(true);
                field.set(targetBeanObject, setValue);
                field.setAccessible(false);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize dynamic config " + key, e);
            }

            dccBeanGroup.put(key, targetBeanObject);
        }

        return bean;
    }

    public void adjustAttributeValue(AttributeVO attributeVO) {
        String key = properties.getKey(attributeVO.getAttribute());
        String value = attributeVO.getValue();

        RBucket<String> bucket = redissonClient.getBucket(key);
        if (!bucket.isExists()) {
            return;
        }
        bucket.set(value);

        Object objBean = dccBeanGroup.get(key);
        if (objBean == null) {
            return;
        }

        Class<?> objBeanClass = AopUtils.isAopProxy(objBean) ? AopUtils.getTargetClass(objBean) : objBean.getClass();
        try {
            Field field = objBeanClass.getDeclaredField(attributeVO.getAttribute());
            field.setAccessible(true);
            field.set(objBean, value);
            field.setAccessible(false);

            log.info("DCC 节点监听，动态设置值 {} {}", key, value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to adjust dynamic config " + key, e);
        }
    }

}
