package cn.ekko.api;

import cn.ekko.api.dto.GoodsMarketRequestDTO;
import cn.ekko.api.dto.GoodsMarketResponseDTO;
import cn.ekko.api.response.Response;

/**
 * @author Ekko
 * @description 营销首页服务接口
 */
public interface IMarketIndexService {

    /**
     * 查询拼团营销配置
     *
     * @param goodsMarketRequestDTO 营销商品信息
     * @return 营销配置信息
     */
    Response<GoodsMarketResponseDTO> queryGroupBuyMarketConfig(GoodsMarketRequestDTO goodsMarketRequestDTO);

}
