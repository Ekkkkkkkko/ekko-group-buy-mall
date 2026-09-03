package cn.ekko.infrastructure.gateway;

import cn.ekko.infrastructure.gateway.dto.GroupBuyMarketResponse;
import cn.ekko.infrastructure.gateway.dto.LockMarketPayOrderRequestDTO;
import cn.ekko.infrastructure.gateway.dto.LockMarketPayOrderResponseDTO;
import cn.ekko.infrastructure.gateway.dto.SettlementMarketPayOrderRequestDTO;
import cn.ekko.infrastructure.gateway.dto.SettlementMarketPayOrderResponseDTO;
import cn.ekko.infrastructure.gateway.dto.RefundMarketPayOrderRequestDTO;
import cn.ekko.infrastructure.gateway.dto.RefundMarketPayOrderResponseDTO;
import cn.ekko.infrastructure.gateway.dto.QueryGroupBuyMarketRequestDTO;
import cn.ekko.infrastructure.gateway.dto.QueryGroupBuyMarketResponseDTO;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;


public interface IGroupBuyMarketService {

    /**
     * 查询商品详情页使用的拼团优惠和进行中队伍。
     */
    @POST("api/v1/gbm/index/query_ekko_group_buy_market_config")
    Call<GroupBuyMarketResponse<QueryGroupBuyMarketResponseDTO>>
    queryGroupBuyMarketConfig(@Body QueryGroupBuyMarketRequestDTO request);

    /**
     * 拼团营销锁单
     */
    @POST("api/v1/gbm/trade/lock_market_pay_order")
    Call<GroupBuyMarketResponse<LockMarketPayOrderResponseDTO>>
    lockMarketPayOrder(@Body LockMarketPayOrderRequestDTO request);

    /**
     * 拼团营销支付结算
     */
    @POST("api/v1/gbm/trade/settlement_market_pay_order")
    Call<GroupBuyMarketResponse<SettlementMarketPayOrderResponseDTO>>
    settlementMarketPayOrder(@Body SettlementMarketPayOrderRequestDTO request);

    /** 拼团营销退单，不执行支付宝退款。 */
    @POST("api/v1/gbm/trade/refund_market_pay_order")
    Call<GroupBuyMarketResponse<RefundMarketPayOrderResponseDTO>>
    refundMarketPayOrder(@Body RefundMarketPayOrderRequestDTO request);
}
