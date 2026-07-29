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

    int updateOrderPayInfo(PayOrder order);

    PayOrder queryOrderByOrderId(String orderId);

    PayOrder queryOrderByUserIdAndOrderId(
            @Param("userId") String userId,
            @Param("orderId") String orderId);

    List<PayOrder> queryUserOrderList(
            @Param("userId") String userId,
            @Param("lastId") Long lastId,
            @Param("limit") int limit);

    int refundMarketOrder(
            @Param("userId") String userId,
            @Param("orderId") String orderId,
            @Param("expectedStatus") String expectedStatus);

    int refundOrder(
            @Param("userId") String userId,
            @Param("orderId") String orderId,
            @Param("expectedStatus") String expectedStatus);

    int closeRefundOrder(
            @Param("userId") String userId,
            @Param("orderId") String orderId);

    List<PayOrder> queryTimeoutWaitRefundOrders();

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

    int changeOrderClose(String orderId);

}
