package com.example.hk.model;

import jakarta.persistence.*;

/**
 * 结算/退款流水。idempotencyKey 唯一：
 * 结算确认重复提交、退款回执重复回传都只命中同一条记录，绝不二次扣款/多退。
 */
@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_idem", columnNames = "idempotencyKey"))
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    /** CHARGE 扣款 / REFUND 退款 */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    /** 金额，分为单位，恒为正数；方向由 type 表示 */
    @Column(nullable = false)
    private long amountFen;

    /** PENDING / SUCCEEDED / FAILED */
    @Column(nullable = false)
    private String status = "PENDING";

    private String receiptNo;

    @Column(nullable = false)
    private long createdAt;

    private long succeededAt;

    private String note;

    protected PaymentEntity() {
    }

    public PaymentEntity(OrderEntity order, String type, String idempotencyKey,
                         long amountFen, long createdAt, String note) {
        this.order = order;
        this.type = type;
        this.idempotencyKey = idempotencyKey;
        this.amountFen = amountFen;
        this.createdAt = createdAt;
        this.note = note;
    }

    public Long getId() { return id; }
    public OrderEntity getOrder() { return order; }
    public String getType() { return type; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getAmountFen() { return amountFen; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String r) { this.receiptNo = r; }
    public long getCreatedAt() { return createdAt; }
    public long getSucceededAt() { return succeededAt; }
    public void setSucceededAt(long t) { this.succeededAt = t; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
