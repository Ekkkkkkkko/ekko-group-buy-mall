package cn.ekko.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Product {

    private Long id;
    private String productId;
    private String productName;
    private String productDesc;
    private String productModel;
    private String productSpecs;
    private BigDecimal basePrice;
    private String imageUrl;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}
