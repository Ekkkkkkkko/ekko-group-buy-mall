package cn.ekko.domain.order.service;

import cn.ekko.domain.order.adapter.port.IGroupBuyMarketPort;
import cn.ekko.domain.order.adapter.port.IProductPort;
import cn.ekko.domain.order.adapter.repository.IOrderRepository;
import cn.ekko.domain.order.model.aggregate.CreateOrderAggregate;
import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import cn.ekko.domain.order.model.valobj.MarketTypeVO;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Date;

@Slf4j
@Service
public class OrderService extends AbstractOrderService {

    @Value("${alipay.notify_url}")
    private String notifyUrl;
    @Value("${alipay.return_url}")
    private String returnUrl;
    @Resource
    private AlipayClient alipayClient;

    public OrderService(IOrderRepository repository,
                        IProductPort productPort,
                        IGroupBuyMarketPort groupBuyMarketPort) {
        super(repository, productPort, groupBuyMarketPort);
    }

    @Override
    protected void doSaveOrder(CreateOrderAggregate orderAggregate) {
        repository.doSaveOrder(orderAggregate);
    }

    @Override
    protected PayOrderEntity doPrepayOrder(String userId, String productId, String productName, String orderId, BigDecimal payAmount) throws AlipayApiException {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(notifyUrl);
        request.setReturnUrl(returnUrl);

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderId);
        bizContent.put("total_amount", payAmount.toString());
        bizContent.put("subject", productName);
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        request.setBizContent(bizContent.toString());

        String form = alipayClient.pageExecute(request).getBody();

        PayOrderEntity payOrderEntity = new PayOrderEntity();
        payOrderEntity.setOrderId(orderId);
        payOrderEntity.setPayUrl(form);
        payOrderEntity.setOrderStatus(OrderStatusVO.PAY_WAIT);

        // 更新订单支付信息
        repository.updateOrderPayInfo(payOrderEntity);

        return payOrderEntity;
    }

    @Override
    public boolean changeOrderPaySuccess(String orderId, Date payTime) {
        if (null == orderId || orderId.isBlank() || null == payTime) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "订单号和支付时间不能为空");
        }

        PayOrderEntity payOrder = repository.queryOrderByOrderId(orderId);
        if (null == payOrder) {
            log.warn("支付成功处理停止，本地订单不存在 orderId:{} payTime:{}", orderId, payTime);
            return false;
        }

        int updateCount = repository.changeOrderPaySuccess(orderId, payTime);
        boolean firstPaySuccess = 1 == updateCount;
        if (0 == updateCount) {
            payOrder = repository.queryOrderByOrderId(orderId);
            if (null == payOrder || !isPaidStatus(payOrder.getOrderStatus())) {
                log.warn("支付成功条件更新为0且订单状态异常，停止后续副作用 orderId:{} status:{}",
                        orderId, null == payOrder ? null : payOrder.getOrderStatus());
                return false;
            }
            log.info("重复支付成功处理，按幂等成功继续 orderId:{} status:{} marketType:{}",
                    orderId, payOrder.getOrderStatus(), payOrder.getMarketType());
        } else if (1 != updateCount) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "支付成功更新影响行数异常：" + updateCount);
        }

        if (MarketTypeVO.GROUP_BUY_MARKET.equals(payOrder.getMarketType())) {
            Date settlementTime = null == payOrder.getPayTime() ? payTime : payOrder.getPayTime();
            groupBuyMarketPort.settlementMarketPayOrder(payOrder.getUserId(), orderId, settlementTime);
            repository.markMarketSettlementCompleted(orderId);
            return true;
        }

        if (firstPaySuccess) {
            repository.publishPaySuccessEvent(payOrder.getUserId(), orderId);
        }
        return true;
    }

    @Override
    public List<PayOrderEntity> queryPendingMarketSettlementOrders() {
        return repository.queryPendingMarketSettlementOrders();
    }

    private boolean isPaidStatus(OrderStatusVO orderStatus) {
        return OrderStatusVO.PAY_SUCCESS.equals(orderStatus)
                || OrderStatusVO.MARKET.equals(orderStatus)
                || OrderStatusVO.DEAL_DONE.equals(orderStatus);
    }

    @Override
    public List<String> queryNoPayNotifyOrder() {
        return repository.queryNoPayNotifyOrder();
    }

    @Override
    public List<String> queryTimeoutCloseOrderList() {
        return repository.queryTimeoutCloseOrderList();
    }

    @Override
    public boolean changeOrderClose(String orderId) {
        return repository.changeOrderClose(orderId);
    }

}
