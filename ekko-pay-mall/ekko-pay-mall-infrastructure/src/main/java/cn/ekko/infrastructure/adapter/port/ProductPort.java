package cn.ekko.infrastructure.adapter.port;

import cn.ekko.domain.order.adapter.port.IProductPort;
import cn.ekko.domain.order.model.entity.ProductEntity;
import cn.ekko.domain.product.adapter.repository.IProductRepository;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import org.springframework.stereotype.Component;

@Component
public class ProductPort implements IProductPort {

    private final IProductRepository productRepository;

    public ProductPort(IProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public ProductEntity queryProductByProductId(String productId) {
        ProductEntity product = productRepository.queryAvailableProductByProductId(productId);
        if (null == product) {
            throw new AppException(ResponseCode.PRODUCT_NOT_FOUND.getCode(), ResponseCode.PRODUCT_NOT_FOUND.getInfo());
        }
        return product;
    }

}
