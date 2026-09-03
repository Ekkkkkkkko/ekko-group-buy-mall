package cn.ekko.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryOrderListResponseDTO {

    private List<OrderItemDTO> orderList;
    private boolean hasMore;
    private Long lastId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDTO {
        private Long id;
        private String userId;
        private String productId;
        private String productName;
        private String orderId;
        private Date orderTime;
        private BigDecimal totalAmount;
        private String status;
        private String payUrl;
        private Integer marketType;
        private BigDecimal marketDeductionAmount;
        private BigDecimal payAmount;
        private Date payTime;
    }
}
