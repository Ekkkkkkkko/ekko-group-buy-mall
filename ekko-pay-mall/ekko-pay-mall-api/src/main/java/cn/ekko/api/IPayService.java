package cn.ekko.api;

import cn.ekko.api.dto.CreatePayRequestDTO;
import cn.ekko.api.dto.NotifyRequestDTO;
import cn.ekko.api.dto.QueryOrderListRequestDTO;
import cn.ekko.api.dto.QueryOrderListResponseDTO;
import cn.ekko.api.dto.RefundOrderRequestDTO;
import cn.ekko.api.dto.RefundOrderResponseDTO;
import cn.ekko.api.response.Response;

public interface IPayService {

    Response<String> createPayOrder(CreatePayRequestDTO createPayRequestDTO);

    Response<QueryOrderListResponseDTO> queryUserOrderList(
            QueryOrderListRequestDTO requestDTO,
            String authorization);

    Response<RefundOrderResponseDTO> refundOrder(
            RefundOrderRequestDTO requestDTO,
            String authorization);

    String groupBuyNotify(NotifyRequestDTO notifyRequestDTO, String notifyToken);

}
