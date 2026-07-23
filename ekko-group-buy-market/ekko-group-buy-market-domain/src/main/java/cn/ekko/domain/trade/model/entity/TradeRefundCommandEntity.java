package cn.ekko.domain.trade.model.entity;

import cn.ekko.domain.trade.model.valobj.RefundTypeEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 退单实体对象
 *
 * @author Ekko
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradeRefundCommandEntity {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 外部交易单号
     */
    private String outTradeNo;

    /** 渠道 */
    private String source;

    /** 来源 */
    private String channel;

}
