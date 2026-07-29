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
import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService extends AbstractOrderService {

    private static final String REFUND_SUCCESS = "REFUND_SUCCESS";
    private static final String FUND_CHANGE_YES = "Y";
    private static final String ALIPAY_SUCCESS_CODE = "10000";
    private static final int MAX_REFUND_REQUEST_NO_LENGTH = 64;

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
            if (null != payOrder && OrderStatusVO.WAIT_REFUND.equals(payOrder.getOrderStatus())) {
                log.info("待退款订单收到重复支付成功回调，仅确认回调成功，不重新结算或履约 orderId:{}", orderId);
                return true;
            }
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
    public boolean groupBuyNotify(String teamId, List<String> outTradeNoList) {
        if (null == teamId || teamId.isBlank() || null == outTradeNoList || outTradeNoList.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "队伍ID和商城订单号列表不能为空");
        }

        List<String> normalizedOrderIds = outTradeNoList.stream()
                .filter(orderId -> null != orderId && !orderId.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (normalizedOrderIds.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "商城订单号列表不能为空");
        }

        List<PayOrderEntity> matchedOrders = repository.changeOrderMarketSettlement(teamId, normalizedOrderIds);
        Set<String> matchedOrderIds = matchedOrders.stream()
                .map(PayOrderEntity::getOrderId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> missingOrderIds = normalizedOrderIds.stream()
                .filter(orderId -> !matchedOrderIds.contains(orderId))
                .collect(Collectors.toList());

        List<PayOrderEntity> firstMarketOrders = matchedOrders.stream()
                .filter(order -> OrderStatusVO.PAY_SUCCESS.equals(order.getOrderStatus()))
                .collect(Collectors.toList());
        for (PayOrderEntity order : firstMarketOrders) {
            repository.publishMarketSettlementEvent(order.getUserId(), order.getOrderId());
        }

        if (!missingOrderIds.isEmpty()) {
            log.warn("拼团成团通知存在未匹配订单，保留任务重试并等待人工补偿 teamId:{} missingOrderIds:{}",
                    teamId, missingOrderIds);
            return false;
        }

        log.info("拼团成团通知幂等处理完成 teamId:{} requestCount:{} firstMarketCount:{}",
                teamId, normalizedOrderIds.size(), firstMarketOrders.size());
        return true;
    }

    @Override
    public boolean changeOrderDealDone(String orderId) {
        if (null == orderId || orderId.isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "订单号不能为空");
        }

        int updateCount = repository.changeOrderDealDone(orderId);
        if (0 == updateCount) {
            log.info("订单未从MARKET更新为DEAL_DONE，可能已完成或不满足履约条件 orderId:{}", orderId);
            return false;
        }
        if (1 != updateCount) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "履约完成更新影响行数异常：" + updateCount);
        }
        return true;
    }

    @Override
    public List<PayOrderEntity> queryUserOrderList(String userId, Long lastId, int limit) {
        if (null == userId || userId.isBlank() || limit < 1 || limit > 51
                || (null != lastId && lastId <= 0)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "订单列表分页参数不合法");
        }
        return repository.queryUserOrderList(userId, lastId, limit);
    }

    @Override
    public PayOrderEntity refundOrder(String userId, String orderId) {
        if (null == userId || userId.isBlank() || null == orderId || orderId.isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "用户ID和订单号不能为空");
        }

        PayOrderEntity order = repository.queryOrderByUserIdAndOrderId(userId, orderId);
        if (null == order) {
            throw new AppException(ResponseCode.ORDER_NOT_FOUND.getCode(), ResponseCode.ORDER_NOT_FOUND.getInfo());
        }

        OrderStatusVO originalStatus = order.getOrderStatus();
        if (OrderStatusVO.CLOSE.equals(originalStatus)
                || OrderStatusVO.WAIT_REFUND.equals(originalStatus)) {
            return order;
        }

        boolean unpaid = OrderStatusVO.CREATE.equals(originalStatus)
                || OrderStatusVO.PAY_WAIT.equals(originalStatus);
        boolean paid = OrderStatusVO.PAY_SUCCESS.equals(originalStatus)
                || OrderStatusVO.MARKET.equals(originalStatus)
                || OrderStatusVO.DEAL_DONE.equals(originalStatus);
        if (!unpaid && !paid) {
            throw new AppException(ResponseCode.ORDER_STATUS_ERROR.getCode(), ResponseCode.ORDER_STATUS_ERROR.getInfo());
        }

        // 先释放拼团侧占用；远端失败时商城状态保持不变，允许用户安全重试。
        if (MarketTypeVO.GROUP_BUY_MARKET.equals(order.getMarketType())) {
            groupBuyMarketPort.refundMarketPayOrder(userId, orderId);
        }

        int updateCount = unpaid
                ? repository.refundOrder(userId, orderId, originalStatus)
                : repository.refundMarketOrder(userId, orderId, originalStatus);
        if (1 == updateCount) {
            order.setOrderStatus(unpaid ? OrderStatusVO.CLOSE : OrderStatusVO.WAIT_REFUND);
            return order;
        }
        if (0 != updateCount) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "商城退单更新影响行数异常：" + updateCount);
        }

        // 并发重复请求可能已经由另一个线程完成状态更新，回查后按幂等成功处理。
        PayOrderEntity latestOrder = repository.queryOrderByUserIdAndOrderId(userId, orderId);
        OrderStatusVO expectedTarget = unpaid ? OrderStatusVO.CLOSE : OrderStatusVO.WAIT_REFUND;
        if (null != latestOrder && expectedTarget.equals(latestOrder.getOrderStatus())) {
            return latestOrder;
        }
        throw new AppException(ResponseCode.ORDER_STATUS_ERROR.getCode(), "订单状态已变化，请刷新后重试");
    }

    @Override
    public boolean confirmUnpaidRefundOrderClosed(String userId, String outTradeNo) {
        validateRefundMessageKey(userId, outTradeNo);
        PayOrderEntity order = repository.queryOrderByUserIdAndOrderId(userId, outTradeNo);
        if (null == order) {
            throw new AppException(ResponseCode.ORDER_NOT_FOUND.getCode(), ResponseCode.ORDER_NOT_FOUND.getInfo());
        }
        if (!OrderStatusVO.CLOSE.equals(order.getOrderStatus())) {
            throw new AppException(
                    ResponseCode.ORDER_STATUS_ERROR.getCode(),
                    "unpaid_unlock对应商城订单尚未关闭，当前状态：" + order.getOrderStatus()
            );
        }
        return true;
    }

    @Override
    public boolean refundPayOrder(String userId, String outTradeNo) throws Exception {
        validateRefundMessageKey(userId, outTradeNo);
        PayOrderEntity order = repository.queryOrderByUserIdAndOrderId(userId, outTradeNo);
        if (null == order) {
            throw new AppException(ResponseCode.ORDER_NOT_FOUND.getCode(), ResponseCode.ORDER_NOT_FOUND.getInfo());
        }

        if (OrderStatusVO.CLOSE.equals(order.getOrderStatus())) {
            log.info("支付宝退款消息重复消费，本地订单已关闭 userId:{} outTradeNo:{}", userId, outTradeNo);
            return true;
        }
        if (!OrderStatusVO.WAIT_REFUND.equals(order.getOrderStatus())) {
            throw new AppException(
                    ResponseCode.ORDER_STATUS_ERROR.getCode(),
                    "退款订单必须处于WAIT_REFUND，当前状态：" + order.getOrderStatus()
            );
        }
        if (null == order.getPayAmount() || order.getPayAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "本地pay_amount为空或不合法，禁止退款");
        }

        String refundRequestNo = buildRefundRequestNo(outTradeNo);

        // 每次发起退款前先查相同退款请求号。上一次请求超时/结果未知时，不会盲目生成新退款号。
        if (queryAlipayRefundSuccess(order, refundRequestNo)) {
            return closeRefundOrderIdempotently(userId, outTradeNo);
        }

        AlipayTradeRefundRequest refundRequest = new AlipayTradeRefundRequest();
        AlipayTradeRefundModel refundModel = new AlipayTradeRefundModel();
        refundModel.setOutTradeNo(outTradeNo);
        refundModel.setOutRequestNo(refundRequestNo);
        refundModel.setRefundAmount(order.getPayAmount().toPlainString());
        refundModel.setRefundReason("拼团退单");
        refundRequest.setBizModel(refundModel);

        AlipayTradeRefundResponse refundResponse = alipayClient.execute(refundRequest);
        if (null == refundResponse
                || !ALIPAY_SUCCESS_CODE.equals(refundResponse.getCode())
                || !refundResponse.isSuccess()) {
            String code = null == refundResponse ? null : refundResponse.getCode();
            String subCode = null == refundResponse ? null : refundResponse.getSubCode();
            throw new AppException(
                    ResponseCode.UN_ERROR.getCode(),
                    "支付宝退款未明确成功，code=" + code + ", subCode=" + subCode
            );
        }

        // fund_change=N 可能是相同退款号的重复请求，不能仅凭接口code把本地订单关闭，必须再查退款结果。
        if (!FUND_CHANGE_YES.equals(refundResponse.getFundChange())) {
            if (queryAlipayRefundSuccess(order, refundRequestNo)) {
                return closeRefundOrderIdempotently(userId, outTradeNo);
            }
            throw new AppException(
                    ResponseCode.UN_ERROR.getCode(),
                    "支付宝退款未确认发生资金变化，且退款查询未返回REFUND_SUCCESS，保留WAIT_REFUND"
            );
        }

        log.info("支付宝退款明确成功 userId:{} outTradeNo:{} refundRequestNo:{} payAmount:{} tradeNo:{} fundChange:{}",
                userId, outTradeNo, refundRequestNo, order.getPayAmount(),
                refundResponse.getTradeNo(), refundResponse.getFundChange());
        return closeRefundOrderIdempotently(userId, outTradeNo);
    }

    @Override
    public List<PayOrderEntity> queryTimeoutWaitRefundOrders() {
        return repository.queryTimeoutWaitRefundOrders();
    }

    private boolean queryAlipayRefundSuccess(PayOrderEntity order, String refundRequestNo) throws AlipayApiException {
        AlipayTradeFastpayRefundQueryRequest queryRequest = new AlipayTradeFastpayRefundQueryRequest();
        AlipayTradeFastpayRefundQueryModel queryModel = new AlipayTradeFastpayRefundQueryModel();
        queryModel.setOutTradeNo(order.getOrderId());
        queryModel.setOutRequestNo(refundRequestNo);
        queryRequest.setBizModel(queryModel);

        AlipayTradeFastpayRefundQueryResponse queryResponse = alipayClient.execute(queryRequest);
        if (null == queryResponse) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "支付宝退款查询无响应，保留WAIT_REFUND");
        }

        if (ALIPAY_SUCCESS_CODE.equals(queryResponse.getCode())
                && queryResponse.isSuccess()
                && REFUND_SUCCESS.equals(queryResponse.getRefundStatus())) {
            verifyRefundAmount(order.getPayAmount(), queryResponse.getRefundAmount());
            log.info("支付宝退款查询确认成功 outTradeNo:{} refundRequestNo:{} refundAmount:{}",
                    order.getOrderId(), refundRequestNo, queryResponse.getRefundAmount());
            return true;
        }

        // 查询接口业务成功但没有REFUND_SUCCESS，表示当前尚未查到成功退款，可使用同一请求号发起/重试。
        if (ALIPAY_SUCCESS_CODE.equals(queryResponse.getCode()) && queryResponse.isSuccess()) {
            return false;
        }

        throw new AppException(
                ResponseCode.UN_ERROR.getCode(),
                "支付宝退款查询结果不明确，禁止直接重发，code=" + queryResponse.getCode()
                        + ", subCode=" + queryResponse.getSubCode()
        );
    }

    private boolean closeRefundOrderIdempotently(String userId, String outTradeNo) {
        int updateCount = repository.closeRefundOrder(userId, outTradeNo);
        if (1 == updateCount) return true;
        if (0 != updateCount) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "退款关单影响行数异常：" + updateCount);
        }

        PayOrderEntity latestOrder = repository.queryOrderByUserIdAndOrderId(userId, outTradeNo);
        if (null != latestOrder && OrderStatusVO.CLOSE.equals(latestOrder.getOrderStatus())) {
            return true;
        }
        throw new AppException(ResponseCode.ORDER_STATUS_ERROR.getCode(), "支付宝退款成功但本地关单失败，等待补偿");
    }

    private void verifyRefundAmount(BigDecimal localPayAmount, String alipayRefundAmount) {
        if (null == alipayRefundAmount || alipayRefundAmount.isBlank()) return;
        try {
            if (localPayAmount.compareTo(new BigDecimal(alipayRefundAmount)) != 0) {
                throw new AppException(ResponseCode.UN_ERROR.getCode(), "支付宝退款金额与本地pay_amount不一致，转人工核对");
            }
        } catch (NumberFormatException e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "支付宝退款金额格式异常，转人工核对");
        }
    }

    private String buildRefundRequestNo(String outTradeNo) {
        String requestNo = "REFUND_" + outTradeNo;
        if (requestNo.length() <= MAX_REFUND_REQUEST_NO_LENGTH) return requestNo;
        return "REFUND_" + DigestUtils.sha256Hex(outTradeNo).substring(0, 40);
    }

    private void validateRefundMessageKey(String userId, String outTradeNo) {
        if (null == userId || userId.isBlank() || null == outTradeNo || outTradeNo.isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "退款消息userId和outTradeNo不能为空");
        }
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
