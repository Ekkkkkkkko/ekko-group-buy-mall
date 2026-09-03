package cn.ekko.domain.order.adapter.port;

import cn.ekko.domain.order.model.entity.ProductEntity;

public interface IProductPort {

    /**
     * 查询可售商品信息
     *
     * @param productId 商品ID
     * @return 商品实体对象
     */
    ProductEntity queryProductByProductId(String productId);

}
