package cn.ekko.infrastructure.dao;

import cn.ekko.infrastructure.dao.po.PayOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IOrderDao {

    void insert(PayOrder order);

    PayOrder queryUnPayOrder(PayOrder order);

    int updateOrderMarketInfo(PayOrder order);

    void updateOrderPayInfo(PayOrder order);

    PayOrder queryOrderByOrderId(String orderId);

    int changeOrderPaySuccess(PayOrder order);

    List<PayOrder> queryMarketOrdersByTeamIdAndOrderIds(
            @Param("teamId") String teamId,
            @Param("orderIds") List<String> orderIds);

    int changeOrderMarketSettlement(
            @Param("teamId") String teamId,
            @Param("orderIds") List<String> orderIds);

    int changeOrderDealDone(String orderId);

    List<PayOrder> queryPaidMarketOrders();

    List<String> queryNoPayNotifyOrder();

    List<String> queryTimeoutCloseOrderList();

    boolean changeOrderClose(String orderId);

}
