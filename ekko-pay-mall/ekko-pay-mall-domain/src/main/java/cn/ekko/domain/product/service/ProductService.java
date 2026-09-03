package cn.ekko.domain.product.service;

import cn.ekko.domain.order.model.entity.GroupBuyMarketEntity;
import cn.ekko.domain.order.model.entity.ProductEntity;
import cn.ekko.domain.product.adapter.port.IGroupBuyMarketQueryPort;
import cn.ekko.domain.product.adapter.repository.IProductRepository;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService implements IProductService {

    private final IProductRepository productRepository;
    private final IGroupBuyMarketQueryPort groupBuyMarketQueryPort;

    public ProductService(
            IProductRepository productRepository,
            IGroupBuyMarketQueryPort groupBuyMarketQueryPort) {
        this.productRepository = productRepository;
        this.groupBuyMarketQueryPort = groupBuyMarketQueryPort;
    }

    @Override
    public List<ProductEntity> queryProductList() {
        return productRepository.queryAvailableProductList();
    }

    @Override
    public ProductEntity queryProductDetail(String productId, String userId) {
        if (null == productId || productId.isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "商品ID不能为空");
        }

        String normalizedProductId = productId.trim();
        ProductEntity product = productRepository.queryAvailableProductByProductId(normalizedProductId);
        if (null == product) {
            throw new AppException(ResponseCode.PRODUCT_NOT_FOUND.getCode(), ResponseCode.PRODUCT_NOT_FOUND.getInfo());
        }

        if (null != userId && !userId.isBlank()) {
            GroupBuyMarketEntity groupBuyMarket = groupBuyMarketQueryPort.queryGroupBuyMarket(
                    userId.trim(),
                    normalizedProductId
            );
            product.setGroupBuyMarket(groupBuyMarket);
        }
        return product;
    }
}
