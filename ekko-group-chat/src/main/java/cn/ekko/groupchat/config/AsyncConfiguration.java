package cn.ekko.groupchat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/** 文档事件异步执行器：事务提交后再推进下一阶段，避免上传请求被切片和向量化阻塞。 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Bean("documentEventExecutor")
    public Executor documentEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("document-pipeline-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /** 查询改写候选、混合检索以及 SSE 会话使用轻量虚拟线程。 */
    @Bean(name = "retrievalVirtualThreadExecutor", destroyMethod = "close")
    public ExecutorService retrievalVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
