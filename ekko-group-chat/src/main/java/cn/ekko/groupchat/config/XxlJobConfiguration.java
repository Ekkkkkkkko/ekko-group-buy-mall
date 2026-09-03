package cn.ekko.groupchat.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 可选 XXL-Job 执行器；本地默认关闭，避免开发环境依赖调度中心。 */
@Configuration
@ConditionalOnProperty(prefix = "group-chat.xxl-job", name = "enabled", havingValue = "true")
public class XxlJobConfiguration {

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(GroupChatProperties properties) {
        GroupChatProperties.XxlJob xxl = properties.getXxlJob();
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(xxl.getAdminAddresses());
        executor.setAppname(xxl.getAppName());
        executor.setAddress(xxl.getAddress());
        executor.setAccessToken(xxl.getAccessToken());
        return executor;
    }
}
