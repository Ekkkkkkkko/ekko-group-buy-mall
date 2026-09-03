package cn.ekko.domain.product.service;

import cn.ekko.domain.order.model.entity.GroupBuyMarketEntity;
import cn.ekko.domain.order.model.entity.ProductEntity;
import cn.ekko.domain.product.adapter.port.IGroupBuyMarketQueryPort;
import cn.ekko.domain.product.adapter.repository.IProductRepository;
import cn.ekko.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductServiceTest {

    private FakeProductRepository productRepository;
    private FakeGroupBuyMarketQueryPort groupBuyMarketQueryPort;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = new FakeProductRepository();
        groupBuyMarketQueryPort = new FakeGroupBuyMarketQueryPort();
        productService = new ProductService(productRepository, groupBuyMarketQueryPort);
    }

    @Test
    void shouldQueryAvailableProductListWithoutCallingMarketService() {
        ProductEntity router = product();
        productRepository.productList = List.of(router);

        List<ProductEntity> products = productService.queryProductList();

        assertEquals(1, products.size());
        assertSame(router, products.get(0));
        assertNull(products.get(0).getGroupBuyMarket());
        assertEquals(0, groupBuyMarketQueryPort.callCount);
    }

    @Test
    void shouldAttachGroupBuyMarketToProductDetail() {
        ProductEntity router = product();
        GroupBuyMarketEntity market = GroupBuyMarketEntity.builder()
                .activityId(1001001L)
                .originalPrice(new BigDecimal("299.00"))
                .deductionPrice(new BigDecimal("50.00"))
                .payPrice(new BigDecimal("249.00"))
                .build();
        productRepository.productDetail = router;
        groupBuyMarketQueryPort.market = market;

        ProductEntity result = productService.queryProductDetail(" router-10001 ", " user-1 ");

        assertSame(router, result);
        assertSame(market, result.getGroupBuyMarket());
        assertEquals(new BigDecimal("249.00"), result.getGroupBuyMarket().getPayPrice());
        assertEquals("router-10001", productRepository.lastProductId);
        assertEquals("user-1", groupBuyMarketQueryPort.lastUserId);
        assertEquals("router-10001", groupBuyMarketQueryPort.lastProductId);
        assertEquals(1, groupBuyMarketQueryPort.callCount);
    }

    @Test
    void shouldReturnProductNotFoundForUnavailableProduct() {
        AppException exception = assertThrows(
                AppException.class,
                () -> productService.queryProductDetail("router-offline", "user-1")
        );

        assertEquals("1009", exception.getCode());
        assertEquals("router-offline", productRepository.lastProductId);
        assertEquals(0, groupBuyMarketQueryPort.callCount);
    }

    private ProductEntity product() {
        return ProductEntity.builder()
                .productId("router-10001")
                .productName("AX3000 Wi-Fi 6路由器")
                .productModel("AX3000")
                .price(new BigDecimal("299.00"))
                .imageUrl("https://example.oss-cn-hangzhou.aliyuncs.com/router/router-10001.png")
                .status(1)
                .build();
    }

    private static class FakeProductRepository implements IProductRepository {

        private List<ProductEntity> productList = List.of();
        private ProductEntity productDetail;
        private String lastProductId;

        @Override
        public List<ProductEntity> queryAvailableProductList() {
            return productList;
        }

        @Override
        public ProductEntity queryAvailableProductByProductId(String productId) {
            lastProductId = productId;
            return productDetail;
        }
    }

    private static class FakeGroupBuyMarketQueryPort implements IGroupBuyMarketQueryPort {

        private GroupBuyMarketEntity market;
        private String lastUserId;
        private String lastProductId;
        private int callCount;

        @Override
        public GroupBuyMarketEntity queryGroupBuyMarket(String userId, String productId) {
            lastUserId = userId;
            lastProductId = productId;
            callCount++;
            return market;
        }
    }
}
