package cn.ekko.domain.product.service;

import cn.ekko.domain.order.model.entity.ProductEntity;

import java.util.List;

public interface IProductService {

    List<ProductEntity> queryProductList();

    ProductEntity queryProductDetail(String productId, String userId);
}
