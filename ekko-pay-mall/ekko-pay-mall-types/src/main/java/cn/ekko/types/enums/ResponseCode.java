package cn.ekko.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ResponseCode {

    SUCCESS("0000", "成功"),
    UN_ERROR("0001", "未知失败"),
    ILLEGAL_PARAMETER("0002", "非法参数"),

    GROUP_BUY_HTTP_ERROR("1001", "拼团营销HTTP调用失败"),
    GROUP_BUY_EMPTY_RESPONSE("1002", "拼团营销返回空响应"),
    GROUP_BUY_BUSINESS_ERROR("1003", "拼团营销业务处理失败"),
    GROUP_BUY_INVALID_RESPONSE("1004", "拼团营销返回数据不完整"),
    ORDER_PAY_AMOUNT_ERROR("1005", "订单支付金额无效"),
    AUTH_REQUIRED("1006", "请先登录或重新登录"),
    ORDER_NOT_FOUND("1007", "订单不存在或不属于当前用户"),
    ORDER_STATUS_ERROR("1008", "当前订单状态不允许退单"),
    PRODUCT_NOT_FOUND("1009", "商品不存在或已下架"),
    ACCOUNT_EXISTS("1010", "用户名已存在"),
    ACCOUNT_CREDENTIAL_ERROR("1011", "账号或密码错误"),
    ACCOUNT_DISABLED("1012", "账号已停用"),
    PASSWORD_CHANGE_ERROR("1013", "密码修改失败"),
    ;

    private String code;
    private String info;

}
