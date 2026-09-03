package cn.ekko.infrastructure.adapter.repository;

import cn.ekko.domain.order.model.entity.ProductEntity;
import cn.ekko.domain.product.adapter.repository.IProductRepository;
import cn.ekko.infrastructure.dao.IProductDao;
import cn.ekko.infrastructure.dao.po.Product;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ProductRepository implements IProductRepository {

    private final IProductDao productDao;

    public ProductRepository(IProductDao productDao) {
        this.productDao = productDao;
    }

    @Override
    public List<ProductEntity> queryAvailableProductList() {
        return productDao.queryAvailableProductList().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public ProductEntity queryAvailableProductByProductId(String productId) {
        return toEntity(productDao.queryAvailableProductByProductId(productId));
    }

    private ProductEntity toEntity(Product product) {
        if (null == product) return null;
        return ProductEntity.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productDesc(product.getProductDesc())
                .productModel(product.getProductModel())
                .productSpecs(product.getProductSpecs())
                .price(product.getBasePrice())
                .imageUrl(product.getImageUrl())
                .status(product.getStatus())
                .build();
    }
}
