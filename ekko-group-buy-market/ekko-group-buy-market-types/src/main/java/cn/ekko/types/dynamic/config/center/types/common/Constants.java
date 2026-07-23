package cn.ekko.types.dynamic.config.center.types.common;

/**
 * 动态配置中心常量。
 */
public final class Constants {

    private Constants() {
    }

    public static final String DYNAMIC_CONFIG_CENTER_REDIS_TOPIC = "DYNAMIC_CONFIG_CENTER_REDIS_TOPIC_";

    public static final String SYMBOL_COLON = ":";

    public static String getTopic(String application) {
        return DYNAMIC_CONFIG_CENTER_REDIS_TOPIC + application;
    }

}
