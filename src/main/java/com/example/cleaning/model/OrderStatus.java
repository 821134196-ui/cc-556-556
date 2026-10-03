package com.example.cleaning.model;

/**
 * 订单状态机：
 * BOOKED(已预约) -> IN_PROGRESS(服务中) -> COMPLETED(服务完成待结算)
 *   任何环节产生未解决争议 -> DISPUTED(争议挂起)
 * COMPLETED 客户付款 -> SETTLED(已结算)，结算后仍可投诉并产生退款
 */
public enum OrderStatus {
    BOOKED, IN_PROGRESS, COMPLETED, DISPUTED, SETTLED, CANCELLED
}
