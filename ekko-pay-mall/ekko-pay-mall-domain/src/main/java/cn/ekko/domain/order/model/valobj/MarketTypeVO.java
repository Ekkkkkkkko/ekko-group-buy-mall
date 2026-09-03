package cn.ekko.domain.order.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MarketTypeVO {

    NO_MARKET(0),
    GROUP_BUY_MARKET(1);

    private final Integer code;


    public static MarketTypeVO valueOf(Integer code) {
        if (code == null || code == 0) {
            return NO_MARKET;
        }

        if (code == 1) {
            return GROUP_BUY_MARKET;
        }

        throw new IllegalArgumentException("不支持的营销类型：" + code);
    }
}