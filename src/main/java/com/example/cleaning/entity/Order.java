package com.example.cleaning.entity;

import com.example.cleaning.model.OrderStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 订单号，幂等展示用 */
    @Column(nullable = false, unique = true, length = 32)
    public String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public User cleaner;

    @Column(nullable = false)
    public String workContent;

    /** 预约开始时间（可跨日，如 22:00 到次日 02:00） */
    @Column(nullable = false)
    public LocalDateTime scheduledStart;

    /** 预约结束时间 */
    @Column(nullable = false)
    public LocalDateTime scheduledEnd;

    /** 每小时单价（分） */
    @Column(nullable = false)
    public long rateCents;

    /** 迟到宽限分钟数，宽限内不扣款 */
    @Column(nullable = false)
    public int lateGraceMinutes = 10;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public OrderStatus status = OrderStatus.BOOKED;

    /** 客户是否确认任务完成（客服不能代确认） */
    public boolean customerConfirmedComplete;

    public LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public long scheduledMinutes() {
        return java.time.Duration.between(scheduledStart, scheduledEnd).toMinutes();
    }

    public BigDecimal rateYuan() {
        return BigDecimal.valueOf(rateCents).movePointLeft(2);
    }
}
