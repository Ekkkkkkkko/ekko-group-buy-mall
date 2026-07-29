package cn.ekko.infrastructure.adapter.port;

import cn.ekko.domain.trade.adapter.port.ITradePort;
import cn.ekko.domain.trade.model.entity.NotifyTaskEntity;
import cn.ekko.domain.trade.model.valobj.NotifyTypeEnumVO;
import cn.ekko.infrastructure.event.EventPublisher;
import cn.ekko.infrastructure.gateway.GroupBuyNotifyService;
import cn.ekko.infrastructure.redis.IRedisService;
import cn.ekko.types.enums.NotifyTaskHTTPEnumVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @author Ekko
 * @description 交易接口服务
 */
@Service
@Slf4j
public class TradePort implements ITradePort {

    @Resource
    private GroupBuyNotifyService groupBuyNotifyService;
    @Resource
    private IRedisService redisService;
    @Resource
    private EventPublisher publisher;

    @Override
    public String groupBuyNotify(NotifyTaskEntity notifyTask) throws Exception {
        RLock lock = redisService.getLock(notifyTask.lockKey());
        try {
            // ekko-group-buy-market 拼团服务端会被部署到多台应用服务器上，那么就会有很多任务一起执行。这个时候要进行抢占，避免被多次执行
            if (lock.tryLock(3, 0, TimeUnit.SECONDS)) {
                try {
                    // 回调方式 HTTP
                    if (NotifyTypeEnumVO.HTTP.getCode().equals(notifyTask.getNotifyType())) {
                        // 无效的 notifyUrl 必须失败，不能将未发送的任务标记为成功
                        if (StringUtils.isBlank(notifyTask.getNotifyUrl()) || "暂无".equals(notifyTask.getNotifyUrl())) {
                            return NotifyTaskHTTPEnumVO.ERROR.getCode();
                        }
                        return groupBuyNotifyService.groupBuyNotify(notifyTask.getNotifyUrl(), notifyTask.getParameterJson());
                    }

                    // 回调方式 MQ
                    if (NotifyTypeEnumVO.MQ.getCode().equals(notifyTask.getNotifyType())) {
                        publisher.publish(notifyTask.getNotifyMQ(), notifyTask.getParameterJson());
                        return NotifyTaskHTTPEnumVO.SUCCESS.getCode();
                    }

                    return NotifyTaskHTTPEnumVO.ERROR.getCode();
                } finally {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
            return NotifyTaskHTTPEnumVO.NULL.getCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("拼团通知任务获取锁被中断 teamId:{} uuid:{}", notifyTask.getTeamId(), notifyTask.getUuid(), e);
            throw e;
        } catch (Exception e) {
            log.error("拼团通知任务发送失败 teamId:{} uuid:{}", notifyTask.getTeamId(), notifyTask.getUuid(), e);
            throw e;
        }
    }

}
