package cn.ekko.domain.goods.service;

/**
 * 商品履约服务。
 */
public interface IGoodsService {

    boolean deliverGoods(String userId, String orderId);

}
