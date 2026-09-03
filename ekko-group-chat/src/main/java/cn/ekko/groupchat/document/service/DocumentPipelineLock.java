package cn.ekko.groupchat.document.service;

import cn.ekko.groupchat.config.GroupChatProperties;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** 防止事件重复投递、定时补偿和多实例同时处理同一文档阶段。 */
@Component
@RequiredArgsConstructor
public class DocumentPipelineLock {

    private final RedissonClient redissonClient;
    private final GroupChatProperties properties;

    public boolean execute(String stage, long documentId, Runnable action) {
        RLock lock = redissonClient.getLock(lockKey(stage, documentId));
        Duration waitTime = properties.getRedis().getLockWaitTime();
        Duration leaseTime = properties.getRedis().getLockLeaseTime();
        boolean acquired = false;
        try {
            acquired = lock.tryLock(
                    waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS
            );
            if (!acquired) {
                return false;
            }
            action.run();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待文档处理锁时被中断", exception);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 补偿扫表只观察占用状态，不抢占正在正常执行的阶段。 */
    public boolean isLocked(String stage, long documentId) {
        return redissonClient.getLock(lockKey(stage, documentId)).isLocked();
    }

    private String lockKey(String stage, long documentId) {
        return "group-chat:document:" + documentId + ":" + stage;
    }
}
