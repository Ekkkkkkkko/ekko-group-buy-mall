package cn.ekko.config;

import cn.ekko.types.dynamic.config.center.types.annotations.DCCValue;
import cn.ekko.types.rate.limiter.types.annotations.RateLimiterAccessInterceptor;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于注解的接口限流切面。
 */
@Aspect
public class RateLimiterAOP {

    private final Logger log = LoggerFactory.getLogger(RateLimiterAOP.class);

    @DCCValue("rateLimiterSwitch:open")
    private String rateLimiterSwitch;

    private final Cache<String, RateLimiter> loginRecord = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    private final Cache<String, Long> blacklist = CacheBuilder.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    @Pointcut("@annotation(cn.ekko.types.rate.limiter.types.annotations.RateLimiterAccessInterceptor)")
    public void aopPoint() {
    }

    @Around("aopPoint() && @annotation(rateLimiterAccessInterceptor)")
    public Object doRouter(ProceedingJoinPoint jp, RateLimiterAccessInterceptor rateLimiterAccessInterceptor) throws Throwable {
        if (StringUtils.isBlank(rateLimiterSwitch) || "close".equals(rateLimiterSwitch)) {
            return jp.proceed();
        }

        String key = rateLimiterAccessInterceptor.key();
        if (StringUtils.isBlank(key)) {
            throw new IllegalArgumentException("RateLimiter key is blank");
        }

        String keyAttr = getAttrValue(key, jp.getArgs());
        log.info("rate limiter attr {}", keyAttr);

        Long blacklistCount = blacklist.getIfPresent(keyAttr);
        if (!"all".equals(keyAttr) && rateLimiterAccessInterceptor.blacklistCount() != 0 && blacklistCount != null && blacklistCount > rateLimiterAccessInterceptor.blacklistCount()) {
            log.info("限流-黑名单拦截(24h)：{}", keyAttr);
            return fallbackMethodResult(jp, rateLimiterAccessInterceptor.fallbackMethod());
        }

        RateLimiter rateLimiter = loginRecord.getIfPresent(keyAttr);
        if (rateLimiter == null) {
            rateLimiter = RateLimiter.create(rateLimiterAccessInterceptor.permitsPerSecond());
            loginRecord.put(keyAttr, rateLimiter);
        }

        if (!rateLimiter.tryAcquire()) {
            if (rateLimiterAccessInterceptor.blacklistCount() != 0) {
                blacklist.put(keyAttr, blacklistCount == null ? 1L : blacklistCount + 1L);
            }
            log.info("限流-超频次拦截：{}", keyAttr);
            return fallbackMethodResult(jp, rateLimiterAccessInterceptor.fallbackMethod());
        }

        return jp.proceed();
    }

    private Object fallbackMethodResult(JoinPoint jp, String fallbackMethod) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Signature sig = jp.getSignature();
        MethodSignature methodSignature = (MethodSignature) sig;
        Method method = jp.getTarget().getClass().getMethod(fallbackMethod, methodSignature.getParameterTypes());
        return method.invoke(jp.getThis(), jp.getArgs());
    }

    public String getAttrValue(String attr, Object[] args) {
        String[] attributes = StringUtils.split(attr, ',');
        if (null != attributes && attributes.length > 1) {
            return Arrays.stream(attributes)
                    .map(String::trim)
                    .map(attribute -> getAttrValue(attribute, args))
                    .collect(Collectors.joining("|"));
        }

        if (args == null || args.length == 0) {
            return "all";
        }
        if (args[0] instanceof String) {
            return args[0].toString();
        }
        if ("all".equals(attr)) {
            return "all";
        }

        String fieldValue = null;
        for (Object arg : args) {
            try {
                if (StringUtils.isNotBlank(fieldValue)) {
                    break;
                }
                Object value = this.getValueByName(arg, attr);
                fieldValue = value == null ? null : String.valueOf(value);
            } catch (Exception e) {
                log.error("获取路由属性值失败 attr：{}", attr, e);
            }
        }
        return StringUtils.defaultIfBlank(fieldValue, "all");
    }

    private Object getValueByName(Object item, String name) {
        if (item == null) {
            return null;
        }
        try {
            Field field = getFieldByName(item, name);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            Object value = field.get(item);
            field.setAccessible(false);
            return value;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private Field getFieldByName(Object item, String name) {
        Class<?> type = item.getClass();
        while (type != null) {
            try {
                return type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

}
