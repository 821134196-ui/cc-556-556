package com.example.hk.model;

import jakarta.persistence.*;

/** 争议单：客户拒签或双方对完成情况有异议时挂起，客服按双方记录处理。 */
@Entity
@Table(name = "disputes")
public class DisputeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String raisedByRole;

    @Column(nullable = false)
    private String raisedByName;

    @Column(nullable = false)
    private long raisedAt;

    /** OPEN / RESOLVED */
    @Column(nullable = false)
    private String status = "OPEN";

    /** 挂起争议时订单所处状态，处理完恢复回去（客服不代客户走流程） */
    private String fromStatus;

    /** APPROVED 认可服务员主张（挂起金额照付）/ DENY 按客户主张（取消挂起金额） */
    private String resolution;

    /** DENY 时客户主张的计费工作分钟（可空，表示仅按客户确认时间重算） */
    private Long claimedBilledMinutes;

    private String resolutionNote;

    private String resolvedByName;

    private long resolvedAt;

    protected DisputeEntity() {
    }

    public DisputeEntity(OrderEntity order, String reason, String raisedByRole,
                         String raisedByName, long raisedAt) {
        this.order = order;
        this.reason = reason;
        this.raisedByRole = raisedByRole;
        this.raisedByName = raisedByName;
        this.raisedAt = raisedAt;
    }

    public Long getId() { return id; }
    public OrderEntity getOrder() { return order; }
    public String getReason() { return reason; }
    public String getRaisedByRole() { return raisedByRole; }
    public String getRaisedByName() { return raisedByName; }
    public long getRaisedAt() { return raisedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getResolution() { return resolution; }
    public void setResolution(String r) { this.resolution = r; }
    public Long getClaimedBilledMinutes() { return claimedBilledMinutes; }
    public void setClaimedBilledMinutes(Long m) { this.claimedBilledMinutes = m; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String n) { this.resolutionNote = n; }
    public String getResolvedByName() { return resolvedByName; }
    public void setResolvedByName(String n) { this.resolvedByName = n; }
    public long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(long t) { this.resolvedAt = t; }
}
