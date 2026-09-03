package cn.ekko.groupchat;

import cn.ekko.groupchat.config.GroupChatProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智能客服模块启动类。
 *
 * <p>提供产品知识 RAG 问答能力：文档上传解析入库、向量化检索、AI 问答。
 * 启用配置属性绑定（{@link GroupChatProperties}）与定时调度（MinerU 任务轮询），
 * 并扫描 document 与 auth 两个包下的 MyBatis-Plus Mapper。
 */
@SpringBootApplication
@EnableConfigurationProperties(GroupChatProperties.class)
@EnableScheduling
@MapperScan({
        "cn.ekko.groupchat.document.mapper",
        "cn.ekko.groupchat.auth.mapper",
        "cn.ekko.groupchat.chat.persistence.mapper"
})
public class GroupChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroupChatApplication.class, args);
    }
}
