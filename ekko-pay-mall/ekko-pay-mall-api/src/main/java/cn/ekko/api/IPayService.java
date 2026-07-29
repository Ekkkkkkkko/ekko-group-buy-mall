package cn.ekko.api;

import cn.ekko.api.dto.CreatePayRequestDTO;
import cn.ekko.api.dto.NotifyRequestDTO;
import cn.ekko.api.response.Response;

public interface IPayService {

    Response<String> createPayOrder(CreatePayRequestDTO createPayRequestDTO);

    String groupBuyNotify(NotifyRequestDTO notifyRequestDTO, String notifyToken);

}
