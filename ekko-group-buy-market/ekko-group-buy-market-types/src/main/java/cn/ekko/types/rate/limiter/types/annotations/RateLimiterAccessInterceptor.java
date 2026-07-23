package cn.ekko.types.rate.limiter.types.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口访问限流标记。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface RateLimiterAccessInterceptor {

    String key() default "all";

    double permitsPerSecond();

    double blacklistCount() default 0;

    String fallbackMethod();

}
