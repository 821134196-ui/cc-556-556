package com.example.hk.model;

import jakarta.persistence.*;

/**
 * 服务记录（不可修改，只可追加）。
 * ARRIVAL / DEPARTURE 分别由服务员上报、客户确认，产生两条记录；
 * PAUSE_OUT / RETURN 记录服务员中途外出的暂停与返回。
 * clockAt 为客户端上报/确认的打卡时间（本地模拟定位一并记录）。
 */
@Entity
@Table(name = "work_events")
public class WorkEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    /** ARRIVAL / DEPARTURE / PAUSE_OUT / RETURN */
    @Column(nullable = false)
    private String eventType;

    /** CUSTOMER / WORKER —— 记录是谁留下的 */
    @Column(nullable = false)
    private String actorRole;

    @Column(nullable = false)
    private String actorName;

    /** WORKER: REPORTED；CUSTOMER: CONFIRMED */
    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private long clockAt;

    @Column(nullable = false)
    private long recordedAt;

    /** 本地模拟定位，如“上海市浦东新区张杨路 500 号（模拟）” */
    private String location;

    private String note;

    protected WorkEventEntity() {
    }

    public WorkEventEntity(OrderEntity order, String eventType, String actorRole, String actorName,
                           String action, long clockAt, long recordedAt, String location, String note) {
        this.order = order;
        this.eventType = eventType;
        this.actorRole = actorRole;
        this.actorName = actorName;
        this.action = action;
        this.clockAt = clockAt;
        this.recordedAt = recordedAt;
        this.location = location;
        this.note = note;
    }

    public Long getId() { return id; }
    public OrderEntity getOrder() { return order; }
    public String getEventType() { return eventType; }
    public String getActorRole() { return actorRole; }
    public String getActorName() { return actorName; }
    public String getAction() { return action; }
    public long getClockAt() { return clockAt; }
    public long getRecordedAt() { return recordedAt; }
    public String getLocation() { return location; }
    public String getNote() { return note; }
}
