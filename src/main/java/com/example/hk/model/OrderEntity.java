package com.example.hk.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 家政服务订单。所有时间戳统一存 epochMillis（UTC 绝对时间），
 * 跨日服务通过时区 Asia/Shanghai 还原“到达日/离开日”。
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long createdAt;

    @Column(nullable = false)
    private long customerId;

    @Column(nullable = false)
    private long workerId;

    /** 约定开始时间，格式 yyyy-MM-dd HH:mm（Asia/Shanghai） */
    @Column(nullable = false)
    private String scheduledStart;

    /** 约定时长（分钟） */
    @Column(nullable = false)
    private int scheduledMinutes;

    @Column(nullable = false)
    private String tasks;

    @Column(nullable = false)
    private String address;

    /** 小时单价（分/小时） */
    @Column(nullable = false)
    private int hourlyRateFen;

    /**
     * BOOKED 已预约
     * IN_PROGRESS 服务中（到达已双方确认）
     * DISPUTED 争议挂起（计时/完成情况有异议，争议金额冻结）
     * COMPLETED 客户已确认任务完成（客服无权代确认）
     * SETTLED 已结算扣款（不可重复扣款）
     */
    @Column(nullable = false)
    private String status = "BOOKED";

    // ---- 双方确认状态：服务员先记录，客户再确认；任何一方不能替对方操作 ----
    private String workerArrivalStatus;   // REPORTED
    private String customerArrivalStatus; // CONFIRMED
    private String workerDepartureStatus;
    private String customerDepartureStatus;

    private boolean disputed;
    private String disputeReason;
    private long disputeRaisedAt;
    /** 客服处理结果：APPROVED 认可服务员主张 / DENY 按客户主张，挂起金额随之放/扣 */
    private String disputeResolution;
    private String disputeResolverName;
    private long disputeResolvedAt;

    private long finalizedAt;
    private String finalizerName;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("clockAt ASC, id ASC")
    private List<WorkEventEntity> events = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<DisputeEntity> disputes = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<PaymentEntity> payments = new ArrayList<>();

    protected OrderEntity() {
    }

    public OrderEntity(long createdAt, long customerId, long workerId,
                       String scheduledStart, int scheduledMinutes,
                       String tasks, String address, int hourlyRateFen) {
        this.createdAt = createdAt;
        this.customerId = customerId;
        this.workerId = workerId;
        this.scheduledStart = scheduledStart;
        this.scheduledMinutes = scheduledMinutes;
        this.tasks = tasks;
        this.address = address;
        this.hourlyRateFen = hourlyRateFen;
    }

    public Long getId() { return id; }
    public long getCreatedAt() { return createdAt; }
    public long getCustomerId() { return customerId; }
    public long getWorkerId() { return workerId; }
    public String getScheduledStart() { return scheduledStart; }
    public int getScheduledMinutes() { return scheduledMinutes; }
    public String getTasks() { return tasks; }
    public String getAddress() { return address; }
    public int getHourlyRateFen() { return hourlyRateFen; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getWorkerArrivalStatus() { return workerArrivalStatus; }
    public void setWorkerArrivalStatus(String s) { this.workerArrivalStatus = s; }
    public String getCustomerArrivalStatus() { return customerArrivalStatus; }
    public void setCustomerArrivalStatus(String s) { this.customerArrivalStatus = s; }
    public String getWorkerDepartureStatus() { return workerDepartureStatus; }
    public void setWorkerDepartureStatus(String s) { this.workerDepartureStatus = s; }
    public String getCustomerDepartureStatus() { return customerDepartureStatus; }
    public void setCustomerDepartureStatus(String s) { this.customerDepartureStatus = s; }

    public boolean isDisputed() { return disputed; }
    public void setDisputed(boolean disputed) { this.disputed = disputed; }
    public String getDisputeReason() { return disputeReason; }
    public void setDisputeReason(String disputeReason) { this.disputeReason = disputeReason; }
    public long getDisputeRaisedAt() { return disputeRaisedAt; }
    public void setDisputeRaisedAt(long t) { this.disputeRaisedAt = t; }
    public String getDisputeResolution() { return disputeResolution; }
    public void setDisputeResolution(String r) { this.disputeResolution = r; }
    public String getDisputeResolverName() { return disputeResolverName; }
    public void setDisputeResolverName(String n) { this.disputeResolverName = n; }
    public long getDisputeResolvedAt() { return disputeResolvedAt; }
    public void setDisputeResolvedAt(long t) { this.disputeResolvedAt = t; }

    public long getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(long t) { this.finalizedAt = t; }
    public String getFinalizerName() { return finalizerName; }
    public void setFinalizerName(String n) { this.finalizerName = n; }

    public List<WorkEventEntity> getEvents() { return events; }
    public List<DisputeEntity> getDisputes() { return disputes; }
    public List<PaymentEntity> getPayments() { return payments; }
}
