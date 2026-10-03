package com.example.cleaning.entity;

import com.example.cleaning.model.RefundStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * 退款单。paymentCallbackId（支付渠道回执号）唯一约束保证：
 * 退款回执重复回传时幂等处理，不会多退。
 */
@Entity
@Table(name = "refunds")
public class Refund extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    public Settlement settlement;

    /** 发起方请求的退款额（分） */
    @Column(nullable = false)
    public long requestCents;

    /** 实际退款额（分），受已退总额与实付总额上限约束 */
    @Column(nullable = false)
    public long refundCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public RefundStatus status = RefundStatus.PENDING;

    /** 退款回执号：支付渠道回调时携带，重复回传命中唯一约束 */
    @Column(unique = true, length = 64)
    public String paymentCallbackId;

    @Column(length = 40)
    public String refundTxnNo;

    @Column(length = 300)
    public String reason;

    public java.time.LocalDateTime createdAt;
    public java.time.LocalDateTime processedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = java.time.LocalDateTime.now();
    }
}
