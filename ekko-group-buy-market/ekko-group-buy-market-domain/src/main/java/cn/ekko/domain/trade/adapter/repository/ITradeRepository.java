package cn.ekko.domain.trade.adapter.repository;

import cn.ekko.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.ekko.domain.trade.model.aggregate.GroupBuyOrderAggregate;
import cn.ekko.domain.trade.model.aggregate.GroupBuyRefundAggregate;
import cn.ekko.domain.trade.model.aggregate.GroupBuyTeamSettlementAggregate;
import cn.ekko.domain.trade.model.entity.GroupBuyActivityEntity;
import cn.ekko.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.ekko.domain.trade.model.entity.MarketPayOrderEntity;
import cn.ekko.domain.trade.model.entity.NotifyTaskEntity;
import cn.ekko.domain.trade.model.valobj.GroupBuyProgressVO;

import java.util.List;

/**
 * @author Ekko
 * @description 交易仓储服务接口
 */
public interface ITradeRepository {

    MarketPayOrderEntity queryMarketPayOrderEntityByOutTradeNo(String userId, String outTradeNo);

    /** 退单专用查询，同时校验商城来源和渠道。 */
    MarketPayOrderEntity queryMarketPayOrderEntityByOutTradeNo(
            String userId,
            String outTradeNo,
            String source,
            String channel);

    MarketPayOrderEntity lockMarketPayOrder(GroupBuyOrderAggregate groupBuyOrderAggregate);

    GroupBuyProgressVO queryGroupBuyProgress(String teamId);

    GroupBuyActivityEntity queryGroupBuyActivityEntityByActivityId(Long activityId);

    Integer queryOrderCountByActivityId(Long activityId, String userId);

    GroupBuyTeamEntity queryGroupBuyTeamByTeamId(String teamId);

    NotifyTaskEntity settlementMarketPayOrder(GroupBuyTeamSettlementAggregate groupBuyTeamSettlementAggregate);

    boolean isSCBlackIntercept(String source, String channel);

    List<NotifyTaskEntity> queryUnExecutedNotifyTaskList();

    List<NotifyTaskEntity> queryUnExecutedNotifyTaskList(String teamId);

    int claimNotifyTask(NotifyTaskEntity notifyTaskEntity);

    int updateNotifyTaskStatusSuccess(NotifyTaskEntity notifyTaskEntity);

    int updateNotifyTaskStatusError(NotifyTaskEntity notifyTaskEntity);

    int updateNotifyTaskStatusRetry(NotifyTaskEntity notifyTaskEntity);

    boolean occupyTeamStock(String teamStockKey, String recoveryTeamStockKey, Integer target, Integer validTime);

    void recoveryTeamStock(String recoveryTeamStockKey, Integer validTime);

    NotifyTaskEntity unpaid2Refund(GroupBuyRefundAggregate groupBuyRefundAggregate);

    NotifyTaskEntity paid2Refund(GroupBuyRefundAggregate groupBuyRefundAggregate);

    NotifyTaskEntity paidTeam2Refund(GroupBuyRefundAggregate groupBuyRefundAggregate);

    void refund2AddRecovery(String recoveryTeamStockKey, String orderId);

    List<UserGroupBuyOrderDetailEntity> queryTimeoutUnpaidOrderList();

}
