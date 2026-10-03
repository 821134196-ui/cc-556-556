package com.example.cleaning.entity;

import com.example.cleaning.model.LogStatus;
import com.example.cleaning.model.LogType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 服务时间记录。客户与钟点工各自提交，互不可改：
 * - 钟点工到达打卡 / 离开打卡（含定位模拟）
 * - 客户对打卡记录确认或反提（conflictWith 指向对方记录）
 * - 中途外出/返回由发现方登记
 * 记录一经提交时间不可修改；只有客服裁定能写入 adoptedAt。
 */
@Entity
@Table(name = "time_logs", indexes = {
        @Index(name = "idx_log_order", columnList = "order_id")
})
public class TimeLog extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public LogType type;

    /** 提交方主张的时间 */
    @Column(nullable = false)
    public LocalDateTime claimedAt;

    /** 模拟定位文本 */
    public String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public LogStatus status = LogStatus.PENDING;

    /** 对方反提时，指向被反对的那条记录 */
    @ManyToOne(fetch = FetchType.LAZY)
    public TimeLog conflictWith;

    /** 客服裁定后采纳的计费时间（唯一可覆盖 claimedAt 的途径） */
    public LocalDateTime adoptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    public Dispute resolvedByDispute;

    public String note;

    public LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /** 实际计费采用的时间 */
    public LocalDateTime effectiveAt() {
        return adoptedAt != null ? adoptedAt : claimedAt;
    }
}
