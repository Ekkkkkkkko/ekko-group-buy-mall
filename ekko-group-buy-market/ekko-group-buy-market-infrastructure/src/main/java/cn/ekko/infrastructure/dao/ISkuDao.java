package cn.ekko.infrastructure.dao;

import cn.ekko.infrastructure.dao.po.Sku;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Ekko
 * @description 商品查询
 */
@Mapper
public interface ISkuDao {

    Sku querySkuByGoodsId(String goodsId);

}
