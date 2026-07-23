package cn.ekko.api;

import cn.ekko.api.dto.LockMarketPayOrderRequestDTO;
import cn.ekko.api.dto.LockMarketPayOrderResponseDTO;
import cn.ekko.api.dto.RefundMarketPayOrderRequestDTO;
import cn.ekko.api.dto.RefundMarketPayOrderResponseDTO;
import cn.ekko.api.dto.SettlementMarketPayOrderRequestDTO;
import cn.ekko.api.dto.SettlementMarketPayOrderResponseDTO;
import cn.ekko.api.response.Response;

/**
 * @author Ekko
 * @description 营销交易服务接口
 */
public interface IMarketTradeService {

    /**
     * 营销锁单
     *
     * @param requestDTO 锁单商品信息
     * @return 锁单结果信息
     */
    Response<LockMarketPayOrderResponseDTO> lockMarketPayOrder(LockMarketPayOrderRequestDTO requestDTO);

    /**
     * 营销结算
     *
     * @param requestDTO 结算商品信息
     * @return 结算结果信息
     */
    Response<SettlementMarketPayOrderResponseDTO> settlementMarketPayOrder(SettlementMarketPayOrderRequestDTO requestDTO);

    /**
     * 营销拼团退单
     *
     * @param requestDTO 退单请求信息
     * @return 退单结果信息
     */
    Response<RefundMarketPayOrderResponseDTO> refundMarketPayOrder(RefundMarketPayOrderRequestDTO requestDTO);

}
