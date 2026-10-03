package com.example.cleaning.bootstrap;

import com.example.cleaning.entity.*;
import com.example.cleaning.model.*;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 启动种子数据：固定构造四个演示场景 */
@ApplicationScoped
public class SeedData {

    @Inject
    com.example.cleaning.service.PaymentGateway gateway;

    @Transactional
    public void seed(@Observes StartupEvent ev) {
        new File("data").mkdirs();
        if (User.count() > 0) return;

        User wang = user("王芳（客户）", Role.CUSTOMER);
        User li = user("李强（客户）", Role.CUSTOMER);
        User zhang = user("张阿姨（钟点工）", Role.CLEANER);
        User chen = user("陈师傅（钟点工）", Role.CLEANER);
        User cs = user("客服小周", Role.CUSTOMER_SERVICE);

        seedCrossMidnight(wang, zhang);
        seedTimeConflict(li, chen);
        seedRejectSign(wang, chen);
        seedRefundReplay(li, zhang);
    }

    // 场景一：跨日服务 22:00→次日 03:00，迟到 25 分钟（宽限 10），加时 15 分钟
    private void seedCrossMidnight(User customer, User cleaner) {
        LocalDate d = LocalDate.now();
        LocalDateTime start = LocalDateTime.of(d.minusDays(1), LocalTime.of(22, 0));
        LocalDateTime end = LocalDateTime.of(d, LocalTime.of(3, 0)); // 跨日 300 分钟
        Order o = order("ORD-CROSS-01", customer, cleaner, "跨日深度保洁：客厅+厨房+两卫", start, end, 5000, 10);

        TimeLog a = log(o, cleaner, LogType.ARRIVE, start.plusMinutes(25), "22:25 到达", LogStatus.CONFIRMED);
        TimeLog out = log(o, cleaner, LogType.OUT, LocalDateTime.of(d.minusDays(1), LocalTime.of(23, 30)),
                "下楼取清洁工具", LogStatus.CONFIRMED);
        TimeLog back = log(o, cleaner, LogType.BACK, LocalDateTime.of(d.minusDays(1), LocalTime.of(23, 50)),
                "返回", LogStatus.CONFIRMED);
        // 次日 03:30 离开：跨日加时 30 分钟
        TimeLog l = log(o, cleaner, LogType.LEAVE, LocalDateTime.of(d, LocalTime.of(3, 30)),
                "实际 03:30 离开（跨日加时 30 分钟）", LogStatus.CONFIRMED);
        o.customerConfirmedComplete = true;
        o.status = OrderStatus.COMPLETED; // 待客户结算：可验证迟到扣减+加时费+跨日时长
    }

    // 场景二：双方确认冲突——钟点工称 09:05 到，客户称 09:40 到，争议挂起中
    private void seedTimeConflict(User customer, User cleaner) {
        LocalDate d = LocalDate.now();
        LocalDateTime start = LocalDateTime.of(d, LocalTime.of(9, 0));
        LocalDateTime end = LocalDateTime.of(d, LocalTime.of(12, 0));
        Order o = order("ORD-CONFLICT-02", customer, cleaner, "日常保洁 3 小时", start, end, 4500, 10);

        TimeLog cleanerArrive = log(o, cleaner, LogType.ARRIVE, start.plusMinutes(5),
                "钟点工打卡：09:05 已到楼下", LogStatus.REJECTED);
        TimeLog customerArrive = log(o, customer, LogType.ARRIVE, start.plusMinutes(40),
                "客户反提：实际 09:40 才进门", LogStatus.PENDING);
        customerArrive.conflictWith = cleanerArrive;

        Dispute dp = new Dispute();
        dp.order = o; dp.raisedBy = customer;
        dp.reason = DisputeReason.TIME_CONFLICT;
        dp.description = "到达时间双方说法不一致：钟点工称 09:05 到达，客户称 09:40 才进门，差额 35 分钟";
        dp.heldCents = 2500; // 挂起 ¥25.00
        dp.persist();
        o.status = OrderStatus.DISPUTED;
    }

    // 场景三：提前结束 + 客户拒签
    private void seedRejectSign(User customer, User cleaner) {
        LocalDate d = LocalDate.now();
        LocalDateTime start = LocalDateTime.of(d, LocalTime.of(13, 0));
        LocalDateTime end = LocalDateTime.of(d, LocalTime.of(15, 0));
        Order o = order("ORD-REJECT-03", customer, cleaner, "擦玻璃+地面清洁", start, end, 5000, 10);

        log(o, cleaner, LogType.ARRIVE, start, "准时到达", LogStatus.CONFIRMED);
        log(o, cleaner, LogType.LEAVE, start.plusMinutes(80),
                "钟点工 14:20 离开，称活已干完", LogStatus.CONFIRMED);
        // 客户拒签确认完成 -> REJECT_SIGN 争议挂起
        Dispute dp = new Dispute();
        dp.order = o; dp.raisedBy = customer;
        dp.reason = DisputeReason.REJECT_SIGN;
        dp.description = "客户拒签：玻璃水渍明显、厨房未打扫，钟点工提前 40 分钟离开";
        dp.heldCents = 4000;
        dp.persist();
        o.status = OrderStatus.DISPUTED;
    }

    // 场景四：已结算 + 退款进行中，用于演示退款回执重复回传不多退
    private void seedRefundReplay(User customer, User cleaner) {
        LocalDate d = LocalDate.now();
        LocalDateTime start = LocalDateTime.of(d, LocalTime.of(16, 0));
        LocalDateTime end = LocalDateTime.of(d, LocalTime.of(18, 0));
        Order o = order("ORD-REFUND-04", customer, cleaner, "搬家后开荒保洁（2 小时）", start, end, 5000, 10);

        log(o, cleaner, LogType.ARRIVE, start, "准时到达", LogStatus.CONFIRMED);
        log(o, cleaner, LogType.LEAVE, end, "准时离开", LogStatus.CONFIRMED);
        o.customerConfirmedComplete = true;

        Settlement s = new Settlement();
        s.order = o;
        s.idempotencyKey = "seed-settle-ORD-REFUND-04";
        s.baseCents = 10000;
        s.lateDeductCents = 0; s.earlyDeductCents = 0; s.outDeductCents = 0;
        s.extraCents = 0;
        s.qualityDeductCents = 0; s.heldCents = 0;
        s.totalCents = 10000;
        s.persist();
        gateway.charge(s);

        Refund r = new Refund();
        r.order = o; r.settlement = s;
        r.requestCents = 3000; r.refundCents = 3000;
        r.status = RefundStatus.PROCESSING;
        r.paymentCallbackId = "CB-ORD-REFUND-04-1";
        r.reason = "客户投诉卫生间未达标，客服核准退 ¥30.00";
        r.persist();

        o.status = OrderStatus.SETTLED;
    }

    private User user(String name, Role role) {
        User u = new User();
        u.name = name;
        u.role = role;
        u.persist();
        return u;
    }

    private Order order(String no, User customer, User cleaner, String content,
                        LocalDateTime start, LocalDateTime end, int rateCents, int grace) {
        Order o = new Order();
        o.orderNo = no;
        o.customer = customer;
        o.cleaner = cleaner;
        o.workContent = content;
        o.scheduledStart = start;
        o.scheduledEnd = end;
        o.rateCents = rateCents;
        o.lateGraceMinutes = grace;
        o.persist();
        return o;
    }

    private TimeLog log(Order o, User reporter, LogType type, LocalDateTime at, String note, LogStatus status) {
        TimeLog l = new TimeLog();
        l.order = o;
        l.reporter = reporter;
        l.type = type;
        l.claimedAt = at;
        l.note = note;
        l.status = status;
        l.location = com.example.cleaning.service.LocationSimulator.describe(o, at);
        l.persist();
        return l;
    }
}
