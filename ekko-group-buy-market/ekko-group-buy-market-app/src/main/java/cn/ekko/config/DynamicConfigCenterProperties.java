package cn.ekko.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 动态配置中心基础配置。
 */
@ConfigurationProperties(prefix = "ekko.dynamic.config", ignoreInvalidFields = true)
public class DynamicConfigCenterProperties {

    private String system;

    public String getKey(String attributeName) {
        return this.system + "_" + attributeName;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

}
