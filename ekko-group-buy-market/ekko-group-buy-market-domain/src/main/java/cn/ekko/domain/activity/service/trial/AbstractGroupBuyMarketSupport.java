package cn.ekko.domain.activity.service.trial;

import cn.ekko.domain.activity.adapter.repository.IActivityRepository;
import cn.ekko.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.ekko.types.design.framework.tree.AbstractMultiThreadStrategyRouter;

import jakarta.annotation.Resource;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * @author Ekko
 * @description 抽象的拼团营销支撑类
 */
public abstract class AbstractGroupBuyMarketSupport<MarketProductEntity, DynamicContext, TrialBalanceEntity> extends AbstractMultiThreadStrategyRouter<cn.ekko.domain.activity.model.entity.MarketProductEntity, DefaultActivityStrategyFactory.DynamicContext, cn.ekko.domain.activity.model.entity.TrialBalanceEntity> {

    protected long timeout = 5000;

    @Resource
    protected IActivityRepository repository;

    @Override
    protected void multiThread(cn.ekko.domain.activity.model.entity.MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // 缺省的方法
    }

}
