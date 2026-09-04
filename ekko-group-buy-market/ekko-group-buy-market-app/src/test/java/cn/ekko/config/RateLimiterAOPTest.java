package cn.ekko.config;

import cn.ekko.api.dto.GoodsMarketRequestDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimiterAOPTest {

    private final RateLimiterAOP rateLimiterAOP = new RateLimiterAOP();

    @Test
    void shouldBuildCompositeKeyFromUserAndGoods() {
        GoodsMarketRequestDTO request = new GoodsMarketRequestDTO();
        request.setUserId("user-1");
        request.setGoodsId("TL-7DR3630");

        String key = rateLimiterAOP.getAttrValue("userId,goodsId", new Object[]{request});

        assertEquals("user-1|TL-7DR3630", key);
    }

    @Test
    void shouldKeepSupportingSingleFieldKeys() {
        GoodsMarketRequestDTO request = new GoodsMarketRequestDTO();
        request.setUserId("user-2");

        String key = rateLimiterAOP.getAttrValue("userId", new Object[]{request});

        assertEquals("user-2", key);
    }
}
