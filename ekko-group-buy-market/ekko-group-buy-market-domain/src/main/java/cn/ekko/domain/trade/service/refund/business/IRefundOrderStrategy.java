package cn.ekko.domain.trade.service.refund.business;

import cn.ekko.domain.trade.model.entity.TradeRefundOrderEntity;
import cn.ekko.domain.trade.model.valobj.TeamRefundSuccess;

/**
 * 退单策略接口
 * 未支付，Unpaid
 * 未成团，UnformedTeam
 * 已成团，AlreadyFormedTeam
 *
 * @author Ekko
 */
public interface IRefundOrderStrategy {

    void refundOrder(TradeRefundOrderEntity tradeRefundOrderEntity) throws Exception;

    void reverseStock(TeamRefundSuccess teamRefundSuccess) throws Exception;

}
