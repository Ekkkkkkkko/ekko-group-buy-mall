package cn.ekko.infrastructure.adapter.port;

import cn.ekko.domain.order.adapter.port.IGroupBuyMarketPort;
import cn.ekko.domain.order.model.entity.MarketPayDiscountEntity;
import cn.ekko.infrastructure.gateway.IGroupBuyMarketService;
import cn.ekko.infrastructure.gateway.dto.GroupBuyMarketResponse;
import cn.ekko.infrastructure.gateway.dto.LockMarketPayOrderRequestDTO;
import cn.ekko.infrastructure.gateway.dto.LockMarketPayOrderResponseDTO;
import cn.ekko.infrastructure.gateway.dto.SettlementMarketPayOrderRequestDTO;
import cn.ekko.infrastructure.gateway.dto.SettlementMarketPayOrderResponseDTO;
import cn.ekko.infrastructure.gateway.dto.RefundMarketPayOrderRequestDTO;
import cn.ekko.infrastructure.gateway.dto.RefundMarketPayOrderResponseDTO;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import retrofit2.Call;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Locale;

@Slf4j
@Component
public class GroupBuyMarketPort implements IGroupBuyMarketPort {

    private static final String SUCCESS_CODE = "0000";
    private static final String NOTIFY_TYPE_HTTP = "HTTP";
    private static final String NOTIFY_TYPE_MQ = "MQ";

    private final IGroupBuyMarketService groupBuyMarketService;
    private final String source;
    private final String channel;
    private final String notifyType;
    private final String notifyUrl;
    private final String notifyMQ;


    public GroupBuyMarketPort(
            IGroupBuyMarketService groupBuyMarketService,
            @Value("${group-buy-market.source}") String source,
            @Value("${group-buy-market.channel}") String channel,
            @Value("${group-buy-market.notify-type}") String notifyType,
            @Value("${group-buy-market.notify-url:}") String notifyUrl,
            @Value("${group-buy-market.notify-mq:}") String notifyMQ) {

        this.groupBuyMarketService = groupBuyMarketService;
        this.source = source;
        this.channel = channel;
        this.notifyType = notifyType;
        this.notifyUrl = notifyUrl;
        this.notifyMQ = notifyMQ;
    }

    @Override
    public void settlementMarketPayOrder(String userId, String outTradeNo, Date outTradeTime) {
        if (isBlank(userId) || isBlank(outTradeNo) || null == outTradeTime) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "拼团结算请求参数不完整"
            );
        }
        if (isBlank(source) || isBlank(channel)) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "拼团营销source或channel未配置"
            );
        }

        SettlementMarketPayOrderRequestDTO request = SettlementMarketPayOrderRequestDTO.builder()
                .source(source)
                .channel(channel)
                .userId(userId)
                .outTradeNo(outTradeNo)
                .outTradeTime(outTradeTime)
                .build();

        log.info("调用拼团营销结算接口开始 userId:{} outTradeNo:{} outTradeTime:{}",
                userId, outTradeNo, outTradeTime);

        try {
            Call<GroupBuyMarketResponse<SettlementMarketPayOrderResponseDTO>> call =
                    groupBuyMarketService.settlementMarketPayOrder(request);
            retrofit2.Response<GroupBuyMarketResponse<SettlementMarketPayOrderResponseDTO>> httpResponse =
                    call.execute();

            if (!httpResponse.isSuccessful()) {
                throw new AppException(
                        ResponseCode.GROUP_BUY_HTTP_ERROR.getCode(),
                        "拼团营销结算HTTP请求失败，HTTP状态码：" + httpResponse.code()
                );
            }

            GroupBuyMarketResponse<SettlementMarketPayOrderResponseDTO> responseBody = httpResponse.body();
            if (null == responseBody) {
                throw new AppException(
                        ResponseCode.GROUP_BUY_EMPTY_RESPONSE.getCode(),
                        "拼团营销结算返回空响应"
                );
            }
            if (!SUCCESS_CODE.equals(responseBody.getCode())) {
                throw new AppException(
                        ResponseCode.GROUP_BUY_BUSINESS_ERROR.getCode(),
                        "拼团营销结算失败，远端业务码：" + responseBody.getCode()
                                + "，原因：" + responseBody.getInfo()
                );
            }

            SettlementMarketPayOrderResponseDTO responseData = responseBody.getData();
            validateSettlementResponseData(userId, outTradeNo, responseData);

            log.info("调用拼团营销结算接口成功 userId:{} outTradeNo:{} teamId:{} activityId:{}",
                    userId, outTradeNo, responseData.getTeamId(), responseData.getActivityId());
        } catch (IOException e) {
            log.error("调用拼团营销结算接口网络异常 userId:{} outTradeNo:{}", userId, outTradeNo, e);
            throw new AppException(
                    ResponseCode.GROUP_BUY_HTTP_ERROR.getCode(),
                    "调用拼团营销结算接口网络异常",
                    e
            );
        }
    }

    @Override
    public void refundMarketPayOrder(String userId, String outTradeNo) {
        if (isBlank(userId) || isBlank(outTradeNo)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "拼团退单请求参数不完整");
        }
        if (isBlank(source) || isBlank(channel)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "拼团营销source或channel未配置");
        }

        RefundMarketPayOrderRequestDTO request = RefundMarketPayOrderRequestDTO.builder()
                .userId(userId)
                .outTradeNo(outTradeNo)
                .source(source)
                .channel(channel)
                .build();

        log.info("调用拼团营销退单接口开始 userId:{} outTradeNo:{}", userId, outTradeNo);
        try {
            Call<GroupBuyMarketResponse<RefundMarketPayOrderResponseDTO>> call =
                    groupBuyMarketService.refundMarketPayOrder(request);
            retrofit2.Response<GroupBuyMarketResponse<RefundMarketPayOrderResponseDTO>> httpResponse =
                    call.execute();
            if (!httpResponse.isSuccessful()) {
                throw new AppException(
                        ResponseCode.GROUP_BUY_HTTP_ERROR.getCode(),
                        "拼团营销退单HTTP请求失败，HTTP状态码：" + httpResponse.code()
                );
            }

            GroupBuyMarketResponse<RefundMarketPayOrderResponseDTO> responseBody = httpResponse.body();
            if (null == responseBody) {
                throw new AppException(ResponseCode.GROUP_BUY_EMPTY_RESPONSE.getCode(), "拼团营销退单返回空响应");
            }
            if (!SUCCESS_CODE.equals(responseBody.getCode())) {
                throw new AppException(
                        ResponseCode.GROUP_BUY_BUSINESS_ERROR.getCode(),
                        "拼团营销退单失败，远端业务码：" + responseBody.getCode()
                                + "，原因：" + responseBody.getInfo()
                );
            }

            RefundMarketPayOrderResponseDTO responseData = responseBody.getData();
            if (null == responseData
                    || !userId.equals(responseData.getUserId())
                    || isBlank(responseData.getOrderId())
                    || isBlank(responseData.getTeamId())
                    || !("success".equals(responseData.getCode()) || "repeat".equals(responseData.getCode()))) {
                throw invalidResponse("拼团营销退单返回数据不完整或与请求不一致");
            }
            log.info("调用拼团营销退单接口成功 userId:{} outTradeNo:{} teamId:{} behavior:{}",
                    userId, outTradeNo, responseData.getTeamId(), responseData.getCode());
        } catch (IOException e) {
            log.error("调用拼团营销退单接口网络异常 userId:{} outTradeNo:{}", userId, outTradeNo, e);
            throw new AppException(
                    ResponseCode.GROUP_BUY_HTTP_ERROR.getCode(),
                    "调用拼团营销退单接口网络异常",
                    e
            );
        }
    }

    @Override
    public MarketPayDiscountEntity lockMarketPayOrder(
            String userId,
            String teamId,
            Long activityId,
            String goodsId,
            String outTradeNo) {

        // 1. 校验领域层传入的必要参数
        validateRequestParameters(
                userId,
                activityId,
                goodsId,
                outTradeNo
        );

        // 2. 构建回调配置
        LockMarketPayOrderRequestDTO.NotifyConfigVO notifyConfig =
                buildNotifyConfig();

        // 3. 构建拼团锁单HTTP请求
        LockMarketPayOrderRequestDTO request =
                LockMarketPayOrderRequestDTO.builder()
                        .userId(userId)
                        .teamId(teamId)
                        .activityId(activityId)
                        .goodsId(goodsId)
                        .source(source)
                        .channel(channel)
                        .outTradeNo(outTradeNo)
                        .notifyConfigVO(notifyConfig)
                        .build();

        log.info(
                "调用拼团营销锁单接口开始 userId:{} teamId:{} activityId:{} goodsId:{} outTradeNo:{}",
                userId,
                teamId,
                activityId,
                goodsId,
                outTradeNo
        );

        try {
            // 4. 创建Retrofit请求
            Call<GroupBuyMarketResponse<LockMarketPayOrderResponseDTO>> call =
                    groupBuyMarketService.lockMarketPayOrder(request);

            // 5. 真正同步发送HTTP请求
            retrofit2.Response<GroupBuyMarketResponse<LockMarketPayOrderResponseDTO>> httpResponse = call.execute();

            // 6. 判断HTTP状态码是否为2xx
            if (!httpResponse.isSuccessful()) {
                throw new AppException(
                        ResponseCode.GROUP_BUY_HTTP_ERROR.getCode(),
                        "拼团营销锁单HTTP请求失败，HTTP状态码："
                                + httpResponse.code()
                );
            }

            // 7. 判断响应体是否为空
            GroupBuyMarketResponse<LockMarketPayOrderResponseDTO>
                    responseBody = httpResponse.body();

            if (responseBody == null) {
                throw new AppException(
                        ResponseCode.GROUP_BUY_EMPTY_RESPONSE.getCode(),
                        ResponseCode.GROUP_BUY_EMPTY_RESPONSE.getInfo()
                );
            }

            // 8. 判断拼团业务是否成功
            if (!SUCCESS_CODE.equals(responseBody.getCode())) {
                throw new AppException(
                        ResponseCode.GROUP_BUY_BUSINESS_ERROR.getCode(),
                        "拼团营销锁单失败，远端业务码："
                                + responseBody.getCode()
                                + "，原因："
                                + responseBody.getInfo()
                );
            }

            // 9. 判断业务数据是否为空
            LockMarketPayOrderResponseDTO responseData = responseBody.getData();

            if (responseData == null) {
                throw new AppException(
                        ResponseCode.GROUP_BUY_EMPTY_RESPONSE.getCode(),
                        "拼团营销锁单成功，但data为空"
                );
            }

            // 10. 校验关键返回字段
            validateResponseData(responseData);

            // 11. HTTP DTO转换为领域实体
            MarketPayDiscountEntity discountEntity = MarketPayDiscountEntity.builder()
                            .marketOrderId(responseData.getOrderId())
                            .teamId(responseData.getTeamId())
                            .originalPrice(responseData.getOriginalPrice())
                            .deductionPrice(responseData.getDeductionPrice())
                            .payPrice(responseData.getPayPrice())
                            .tradeOrderStatus(
                                    responseData.getTradeOrderStatus()
                            )
                            .build();

            log.info(
                    "调用拼团营销锁单接口成功 outTradeNo:{} marketOrderId:{} teamId:{} payPrice:{}",
                    outTradeNo,
                    discountEntity.getMarketOrderId(),
                    discountEntity.getTeamId(),
                    discountEntity.getPayPrice()
            );

            return discountEntity;
        } catch (IOException e) {
            // 连接拒绝、连接超时、读取超时等进入这里
            log.error(
                    "调用拼团营销锁单接口网络异常 outTradeNo:{}",
                    outTradeNo,
                    e
            );

            throw new AppException(
                    ResponseCode.GROUP_BUY_HTTP_ERROR.getCode(),
                    "调用拼团营销锁单接口网络异常",
                    e
            );
        }
    }

    /**
     * 构建拼团回调配置。
     *
     * 所有配置来自商城服务端配置文件，
     * 不能从前端请求中读取notifyUrl。
     */
    private LockMarketPayOrderRequestDTO.NotifyConfigVO buildNotifyConfig() {
        if (isBlank(notifyType)) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "拼团通知类型不能为空"
            );
        }
        String normalizedNotifyType = notifyType.toUpperCase(Locale.ROOT);

        if (NOTIFY_TYPE_HTTP.equals(normalizedNotifyType)) {
            if (isBlank(notifyUrl)) {
                throw new AppException(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "拼团HTTP通知地址不能为空"
                );
            }

            return LockMarketPayOrderRequestDTO.NotifyConfigVO.builder()
                    .notifyType(NOTIFY_TYPE_HTTP)
                    .notifyUrl(notifyUrl)
                    .notifyMQ(null)
                    .build();
        }

        if (NOTIFY_TYPE_MQ.equals(normalizedNotifyType)) {
            if (isBlank(notifyMQ)) {
                throw new AppException(
                        ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "拼团MQ通知主题不能为空"
                );
            }

            return LockMarketPayOrderRequestDTO.NotifyConfigVO.builder()
                    .notifyType(NOTIFY_TYPE_MQ)
                    .notifyMQ(notifyMQ)
                    .notifyUrl(null)
                    .build();
        }

        throw new AppException(
                ResponseCode.ILLEGAL_PARAMETER.getCode(),
                "不支持的拼团通知类型：" + notifyType
        );
    }

    private void validateRequestParameters(
            String userId,
            Long activityId,
            String goodsId,
            String outTradeNo) {

        if (isBlank(userId)
                || activityId == null
                || isBlank(goodsId)
                || isBlank(outTradeNo)) {

            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "拼团锁单请求参数不完整"
            );
        }

        if (isBlank(source) || isBlank(channel)) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "拼团营销source或channel未配置"
            );
        }
    }

    private void validateResponseData(
            LockMarketPayOrderResponseDTO responseData) {

        if (isBlank(responseData.getOrderId())) {
            throw invalidResponse("拼团内部订单号orderId为空");
        }

        if (isBlank(responseData.getTeamId())) {
            throw invalidResponse("拼团队伍teamId为空");
        }

        if (responseData.getPayPrice() == null) {
            throw invalidResponse("拼团支付金额payPrice为空");
        }

        if (responseData.getPayPrice()
                .compareTo(BigDecimal.ZERO) < 0) {
            throw invalidResponse("拼团支付金额payPrice不能小于0");
        }

        if (responseData.getOriginalPrice() == null) {
            throw invalidResponse("拼团原价originalPrice为空");
        }

        if (responseData.getDeductionPrice() == null) {
            throw invalidResponse("拼团优惠金额deductionPrice为空");
        }

        if (responseData.getTradeOrderStatus() == null) {
            throw invalidResponse("拼团订单状态tradeOrderStatus为空");
        }
    }

    private void validateSettlementResponseData(
            String expectedUserId,
            String expectedOutTradeNo,
            SettlementMarketPayOrderResponseDTO responseData) {

        if (null == responseData) {
            throw new AppException(
                    ResponseCode.GROUP_BUY_EMPTY_RESPONSE.getCode(),
                    "拼团营销结算成功，但data为空"
            );
        }
        if (!expectedUserId.equals(responseData.getUserId())) {
            throw invalidResponse("拼团结算返回的userId与请求不一致");
        }
        if (!expectedOutTradeNo.equals(responseData.getOutTradeNo())) {
            throw invalidResponse("拼团结算返回的outTradeNo与请求不一致");
        }
        if (isBlank(responseData.getTeamId())) {
            throw invalidResponse("拼团结算返回的teamId为空");
        }
        if (null == responseData.getActivityId()) {
            throw invalidResponse("拼团结算返回的activityId为空");
        }
    }

    private AppException invalidResponse(String message) {
        return new AppException(
                ResponseCode.GROUP_BUY_INVALID_RESPONSE.getCode(),
                message
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
