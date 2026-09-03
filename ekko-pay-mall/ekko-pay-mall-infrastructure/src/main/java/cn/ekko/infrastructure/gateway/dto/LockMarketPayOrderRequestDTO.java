package cn.ekko.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockMarketPayOrderRequestDTO {

    /**
     * 商城登录用户ID
     */
    private String userId;

    /**
     * 加入已有拼团队伍时传入；
     * 为空表示创建新队伍
     */
    private String teamId;

    /**
     * 拼团活动ID
     */
    private Long activityId;

    /**
     * 拼团商品ID
     * 当前由商城 productId 映射得到
     */
    private String goodsId;

    /**
     * 商城来源标识，由服务端配置
     */
    private String source;

    /**
     * 商城渠道标识，由服务端配置
     */
    private String channel;

    /**
     * 商城订单号
     *
     * pay_order.order_id
     * =
     * group_buy_order_list.out_trade_no
     */
    private String outTradeNo;

    /**
     * 拼团完成后的通知方式
     */
    private NotifyConfigVO notifyConfigVO;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotifyConfigVO {

        /**
         * HTTP 或 MQ
         */
        private String notifyType;

        /**
         * MQ 主题；HTTP 模式下为空
         */
        private String notifyMQ;

        /**
         * HTTP 回调地址；MQ 模式下为空
         */
        private String notifyUrl;
    }
}