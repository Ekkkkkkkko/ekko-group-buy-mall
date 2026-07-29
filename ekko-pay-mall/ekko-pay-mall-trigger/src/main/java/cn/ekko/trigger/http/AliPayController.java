package cn.ekko.trigger.http;

import cn.ekko.api.IPayService;
import cn.ekko.api.dto.CreatePayRequestDTO;
import cn.ekko.api.dto.NotifyRequestDTO;
import cn.ekko.api.dto.QueryOrderListRequestDTO;
import cn.ekko.api.dto.QueryOrderListResponseDTO;
import cn.ekko.api.dto.RefundOrderRequestDTO;
import cn.ekko.api.dto.RefundOrderResponseDTO;
import cn.ekko.api.response.Response;
import cn.ekko.domain.auth.service.ILoginService;
import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.model.entity.ShopCartEntity;
import cn.ekko.domain.order.model.valobj.MarketTypeVO;
import cn.ekko.domain.order.model.valobj.OrderStatusVO;
import cn.ekko.domain.order.service.IOrderService;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import com.alipay.api.internal.util.AlipaySignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/alipay/")
public class AliPayController implements IPayService {

    @Value("${alipay.alipay_public_key}")
    private String alipayPublicKey;
    @Value("${group-buy-market.notify-token:}")
    private String groupBuyNotifyToken;
    @Resource
    private IOrderService orderService;
    @Resource
    private ILoginService loginService;

    /**
     * http://localhost:8091/api/v1/alipay/create_pay_order?userId=1001&productId=100001
     */
    @RequestMapping(value = "create_pay_order", method = RequestMethod.POST)
    public Response<String> createPayOrder(@RequestBody CreatePayRequestDTO createPayRequestDTO) {
        try {
            validateCreatePayRequest(createPayRequestDTO);

            String userId = createPayRequestDTO.getUserId();
            String productId = createPayRequestDTO.getProductId();
            MarketTypeVO marketType = MarketTypeVO.valueOf(createPayRequestDTO.getMarketType());

            log.info("商品下单，根据商品ID创建支付单开始 userId:{} productId:{} marketType:{}",
                    userId, productId, marketType);

            // 下单
            PayOrderEntity payOrderEntity = orderService.createOrder(ShopCartEntity.builder()
                    .userId(userId)
                    .productId(productId)
                    .teamId(createPayRequestDTO.getTeamId())
                    .activityId(createPayRequestDTO.getActivityId())
                    .marketType(marketType)
                    .build());

            log.info("商品下单，根据商品ID创建支付单完成 userId:{} productId:{} orderId:{}", userId, productId, payOrderEntity.getOrderId());
            return Response.<String>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(payOrderEntity.getPayUrl())
                    .build();
        } catch (AppException e) {
            log.error("商品下单业务失败 userId:{} productId:{}",
                    null == createPayRequestDTO ? null : createPayRequestDTO.getUserId(),
                    null == createPayRequestDTO ? null : createPayRequestDTO.getProductId(), e);
            return Response.<String>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("商品下单，根据商品ID创建支付单失败 userId:{} productId:{}",
                    null == createPayRequestDTO ? null : createPayRequestDTO.getUserId(),
                    null == createPayRequestDTO ? null : createPayRequestDTO.getProductId(), e);
            return Response.<String>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    private void validateCreatePayRequest(CreatePayRequestDTO requestDTO) {
        if (null == requestDTO
                || null == requestDTO.getUserId()
                || requestDTO.getUserId().isBlank()
                || null == requestDTO.getProductId()
                || requestDTO.getProductId().isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "用户ID和商品ID不能为空");
        }

        final MarketTypeVO marketType;
        try {
            marketType = MarketTypeVO.valueOf(requestDTO.getMarketType());
        } catch (IllegalArgumentException e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), e.getMessage(), e);
        }

        if (MarketTypeVO.GROUP_BUY_MARKET.equals(marketType) && null == requestDTO.getActivityId()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "拼团订单的活动ID不能为空");
        }
    }

    @Override
    @RequestMapping(value = "query_user_order_list", method = RequestMethod.POST)
    public Response<QueryOrderListResponseDTO> queryUserOrderList(
            @RequestBody QueryOrderListRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            if (null == requestDTO) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "订单列表请求不能为空");
            }
            String userId = resolveAuthenticatedUserId(authorization, requestDTO.getUserId());
            int pageSize = null == requestDTO.getPageSize() ? 10 : requestDTO.getPageSize();
            if (pageSize < 1 || pageSize > 50
                    || (null != requestDTO.getLastId() && requestDTO.getLastId() <= 0)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "pageSize范围为1到50，lastId必须大于0");
            }

            List<PayOrderEntity> queriedOrders = new ArrayList<>(orderService.queryUserOrderList(
                    userId,
                    requestDTO.getLastId(),
                    pageSize + 1
            ));
            boolean hasMore = queriedOrders.size() > pageSize;
            if (hasMore) {
                queriedOrders.remove(queriedOrders.size() - 1);
            }
            List<QueryOrderListResponseDTO.OrderItemDTO> orderList = queriedOrders.stream()
                    .map(this::toOrderItemDTO)
                    .collect(Collectors.toList());
            Long nextLastId = orderList.isEmpty() ? null : orderList.get(orderList.size() - 1).getId();

            return Response.<QueryOrderListResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(QueryOrderListResponseDTO.builder()
                            .orderList(orderList)
                            .hasMore(hasMore)
                            .lastId(nextLastId)
                            .build())
                    .build();
        } catch (AppException e) {
            log.warn("查询用户订单列表失败 userId:{} lastId:{} code:{}",
                    null == requestDTO ? null : requestDTO.getUserId(),
                    null == requestDTO ? null : requestDTO.getLastId(), e.getCode());
            return Response.<QueryOrderListResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("查询用户订单列表异常 userId:{} lastId:{}",
                    null == requestDTO ? null : requestDTO.getUserId(),
                    null == requestDTO ? null : requestDTO.getLastId(), e);
            return Response.<QueryOrderListResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @Override
    @RequestMapping(value = "refund_order", method = RequestMethod.POST)
    public Response<RefundOrderResponseDTO> refundOrder(
            @RequestBody RefundOrderRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            if (null == requestDTO || null == requestDTO.getOrderId() || requestDTO.getOrderId().isBlank()) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "退单请求和订单号不能为空");
            }
            String userId = resolveAuthenticatedUserId(authorization, requestDTO.getUserId());
            PayOrderEntity refundedOrder = orderService.refundOrder(userId, requestDTO.getOrderId().trim());
            boolean refundRequired = OrderStatusVO.WAIT_REFUND.equals(refundedOrder.getOrderStatus());
            String info = refundRequired ? "退单申请已受理，等待退款" : "退单成功，订单已关闭";

            return Response.<RefundOrderResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(RefundOrderResponseDTO.builder()
                            .userId(userId)
                            .orderId(refundedOrder.getOrderId())
                            .status(refundedOrder.getOrderStatus().getCode())
                            .refundRequired(refundRequired)
                            .info(info)
                            .build())
                    .build();
        } catch (AppException e) {
            log.warn("商城退单失败 userId:{} orderId:{} code:{}",
                    null == requestDTO ? null : requestDTO.getUserId(),
                    null == requestDTO ? null : requestDTO.getOrderId(), e.getCode());
            return Response.<RefundOrderResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("商城退单异常 userId:{} orderId:{}",
                    null == requestDTO ? null : requestDTO.getUserId(),
                    null == requestDTO ? null : requestDTO.getOrderId(), e);
            return Response.<RefundOrderResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    private String resolveAuthenticatedUserId(String authorization, String requestedUserId) {
        String authenticatedUserId = loginService.resolveUserId(authorization);
        if (null == requestedUserId || requestedUserId.isBlank()
                || !authenticatedUserId.equals(requestedUserId.trim())) {
            throw new AppException(ResponseCode.ORDER_NOT_FOUND.getCode(), "请求用户与登录用户不一致");
        }
        return authenticatedUserId;
    }

    private QueryOrderListResponseDTO.OrderItemDTO toOrderItemDTO(PayOrderEntity order) {
        return QueryOrderListResponseDTO.OrderItemDTO.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .productId(order.getProductId())
                .productName(order.getProductName())
                .orderId(order.getOrderId())
                .orderTime(order.getOrderTime())
                .totalAmount(order.getTotalAmount())
                .status(order.getOrderStatus().getCode())
                .payUrl(order.getPayUrl())
                .marketType(order.getMarketType().getCode())
                .marketDeductionAmount(order.getMarketDeductionAmount())
                .payAmount(order.getPayAmount())
                .payTime(order.getPayTime())
                .build();
    }

    @RequestMapping(value = "pay_notify", method = RequestMethod.POST)
    public String payNotify(HttpServletRequest request) {
        try {
            Map<String, String> params = new HashMap<>();
            Map<String, String[]> requestParams = request.getParameterMap();
            for (String name : requestParams.keySet()) {
                params.put(name, request.getParameter(name));
            }

            String sign = params.get("sign");
            String content = AlipaySignature.getSignCheckContentV1(params);
            boolean checkSignature = AlipaySignature.rsa256CheckContent(content, sign, alipayPublicKey, "UTF-8");
            if (!checkSignature) {
                log.warn("支付回调验签失败 outTradeNo:{}", params.get("out_trade_no"));
                return "failure";
            }

            String tradeStatus = params.get("trade_status");
            if (!"TRADE_SUCCESS".equals(tradeStatus)) {
                log.info("支付回调非成功状态，不执行支付副作用 outTradeNo:{} tradeStatus:{}",
                        params.get("out_trade_no"), tradeStatus);
                return "success";
            }

            String orderId = params.get("out_trade_no");
            Date payTime = parsePayTime(params.get("gmt_payment"));
            log.info("支付回调验签成功，处理支付事实 orderId:{} alipayTradeNo:{} payTime:{}",
                    orderId, params.get("trade_no"), payTime);

            boolean handled = orderService.changeOrderPaySuccess(orderId, payTime);
            return handled ? "success" : "failure";
        } catch (Exception e) {
            log.error("支付回调，处理失败", e);
            return "failure";
        }
    }

    private Date parsePayTime(String gmtPayment) throws ParseException {
        if (null == gmtPayment || gmtPayment.isBlank()) {
            throw new ParseException("支付宝支付时间gmt_payment为空", 0);
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setLenient(false);
        return dateFormat.parse(gmtPayment);
    }

    @Override
    @RequestMapping(value = "group_buy_notify", method = RequestMethod.POST)
    public String groupBuyNotify(
            @RequestBody NotifyRequestDTO notifyRequestDTO,
            @RequestHeader(value = "X-Group-Buy-Token", required = false) String notifyToken) {
        try {
            if (!isTrustedGroupBuyNotify(notifyToken)) {
                log.warn("拒绝不可信的拼团成团通知 teamId:{}",
                        null == notifyRequestDTO ? null : notifyRequestDTO.getTeamId());
                return "error";
            }
            if (null == notifyRequestDTO) {
                return "error";
            }

            log.info("接收拼团成团通知 teamId:{} outTradeNoList:{}",
                    notifyRequestDTO.getTeamId(), notifyRequestDTO.getOutTradeNoList());
            boolean handled = orderService.groupBuyNotify(
                    notifyRequestDTO.getTeamId(),
                    notifyRequestDTO.getOutTradeNoList()
            );
            return handled ? "success" : "error";
        } catch (Exception e) {
            log.error("拼团成团通知处理失败 teamId:{} outTradeNoList:{}",
                    null == notifyRequestDTO ? null : notifyRequestDTO.getTeamId(),
                    null == notifyRequestDTO ? null : notifyRequestDTO.getOutTradeNoList(), e);
            return "error";
        }
    }

    private boolean isTrustedGroupBuyNotify(String notifyToken) {
        if (null == groupBuyNotifyToken || groupBuyNotifyToken.isBlank()
                || null == notifyToken || notifyToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                groupBuyNotifyToken.getBytes(StandardCharsets.UTF_8),
                notifyToken.getBytes(StandardCharsets.UTF_8)
        );
    }

}
