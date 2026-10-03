package com.example.cleaning.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * 结算单（客户付款回执，本地模拟支付）。
 * idempotencyKey 唯一约束保证：结算确认重复提交不会二次扣款。
 */
@Entity
@Table(name = "settlements")
public class Settlement extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    public Order order;

    /** 客户端幂等键（如 "settle-ORD001"），重复提交返回首条结果 */
    @Column(nullable = false, unique = true, length = 64)
    public String idempotencyKey;

    @Column(nullable = false)
    public long baseCents;       // 约定费用
    @Column(nullable = false)
    public long lateDeductCents; // 迟到扣减
    @Column(nullable = false)
    public long earlyDeductCents;// 提前结束扣减
    @Column(nullable = false)
    public long outDeductCents;  // 中途外出不计费扣减
    @Column(nullable = false)
    public long extraCents;      // 客服核准的加时/加活费
    @Column(nullable = false)
    public long qualityDeductCents; // 质量/外出扣减
    @Column(nullable = false)
    public long heldCents;       // 历史争议挂起（结算单生成时应为 0）

    @Column(nullable = false)
    public long totalCents;      // 实扣金额

    @Column(length = 40)
    public String paymentTxnNo;  // 模拟支付流水号

    public Long paidCents;       // 支付回执实际扣款（=totalCents）

    public java.time.LocalDateTime createdAt;
    public java.time.LocalDateTime paidAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = java.time.LocalDateTime.now();
    }
}
