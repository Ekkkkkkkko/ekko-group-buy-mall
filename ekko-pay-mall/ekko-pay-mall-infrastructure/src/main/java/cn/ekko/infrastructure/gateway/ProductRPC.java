package cn.ekko.infrastructure.gateway;

import cn.ekko.infrastructure.gateway.dto.ProductDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 模拟RPC接口，商品库商品查询。
 */
@Service
public class ProductRPC {

    public ProductDTO queryProductByProductId(String productId) {
        ProductDTO productDTO = new ProductDTO();
        productDTO.setProductId(productId);
        productDTO.setProductName("测试商品");
        productDTO.setProductDesc("这是一个测试商品");
        productDTO.setPrice(new BigDecimal("1.68"));
        return productDTO;
    }

}
