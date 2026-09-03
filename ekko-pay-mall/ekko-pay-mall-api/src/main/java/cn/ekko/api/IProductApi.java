package cn.ekko.api;

import cn.ekko.api.dto.QueryProductResponseDTO;
import cn.ekko.api.response.Response;

import java.util.List;

public interface IProductApi {

    Response<List<QueryProductResponseDTO>> queryProductList();

    Response<QueryProductResponseDTO> queryProductDetail(String productId, String userId);
}
