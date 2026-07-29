package cn.ekko.trigger.http;

import cn.ekko.api.IPayService;
import cn.ekko.api.dto.CreatePayRequestDTO;
import cn.ekko.api.response.Response;
import cn.ekko.domain.order.model.entity.PayOrderEntity;
import cn.ekko.domain.order.model.entity.ShopCartEntity;
import cn.ekko.domain.order.model.valobj.MarketTypeVO;
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
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/alipay/")
public class AliPayController implements IPayService {

    @Value("${alipay.alipay_public_key}")
    private String alipayPublicKey;
    @Resource
    private IOrderService orderService;

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

}
