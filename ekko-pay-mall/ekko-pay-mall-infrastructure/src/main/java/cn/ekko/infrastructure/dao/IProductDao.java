package cn.ekko.infrastructure.dao;

import cn.ekko.infrastructure.dao.po.Product;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IProductDao {

    List<Product> queryAvailableProductList();

    Product queryAvailableProductByProductId(String productId);
}
