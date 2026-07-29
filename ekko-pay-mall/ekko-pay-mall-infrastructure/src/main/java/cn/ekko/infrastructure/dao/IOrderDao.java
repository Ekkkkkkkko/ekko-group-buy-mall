package cn.ekko.infrastructure.dao;

import cn.ekko.infrastructure.dao.po.PayOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IOrderDao {

    void insert(PayOrder order);

    PayOrder queryUnPayOrder(PayOrder order);

    int updateOrderMarketInfo(PayOrder order);

    void updateOrderPayInfo(PayOrder order);

    PayOrder queryOrderByOrderId(String orderId);

    int changeOrderPaySuccess(PayOrder order);

    List<PayOrder> queryPaidMarketOrders();

    List<String> queryNoPayNotifyOrder();

    List<String> queryTimeoutCloseOrderList();

    boolean changeOrderClose(String orderId);

}
