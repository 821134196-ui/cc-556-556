package com.example.cleaning.entity;

import com.example.cleaning.model.DisputeReason;
import com.example.cleaning.model.DisputeStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 争议单。开启后争议金额从应付款中挂起（held），订单冻结结算，
 * 任何一方都不能继续改计时；由客服根据双方记录裁定。
 */
@Entity
@Table(name = "disputes")
public class Dispute extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public User raisedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    public DisputeReason reason;

    @Column(nullable = false, length = 500)
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public DisputeStatus status = DisputeStatus.OPEN;

    /** 挂起金额（分），结算时从应付款中冻结 */
    @Column(nullable = false)
    public long heldCents;

    // ---- 客服裁定结果 ----
    @ManyToOne(fetch = FetchType.LAZY)
    public User resolvedBy;

    /** 客服裁定的到达时间（可选，TIME_CONFLICT 时使用） */
    public LocalDateTime adoptedArriveAt;

    /** 客服裁定的离开时间（可选） */
    public LocalDateTime adoptedLeaveAt;

    /** 客户拒签场景：客服是否认定服务按约完成 */
    public Boolean serviceAccepted;

    /** 中途外出/质量等的分钟级扣减（不计费工时） */
    public Integer deductMinutes;

    /** 临时加活经客服核准的追加费用（分） */
    public Long approvedExtraCents;

    /** 挂起金额的处理：退给客户 / 释放支付给钟点工 */
    public Long releaseToCleanerCents;
    public Long refundToCustomerCents;

    @Column(length = 500)
    public String resolution;

    public LocalDateTime createdAt;
    public LocalDateTime resolvedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
