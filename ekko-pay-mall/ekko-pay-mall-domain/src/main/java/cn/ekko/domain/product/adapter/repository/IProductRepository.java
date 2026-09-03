package cn.ekko.domain.product.adapter.repository;

import cn.ekko.domain.order.model.entity.ProductEntity;

import java.util.List;

/**
 * 商城商品查询仓储。
 */
public interface IProductRepository {

    List<ProductEntity> queryAvailableProductList();

    ProductEntity queryAvailableProductByProductId(String productId);
}
