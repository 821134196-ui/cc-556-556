package com.example.cleaning.service;

import com.example.cleaning.entity.*;
import com.example.cleaning.model.*;
import com.example.cleaning.web.ApiException;
import com.example.cleaning.web.dto.OrderView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.hibernate.exception.ConstraintViolationException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class OrderService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    BillingService billing;
    @Inject
    PaymentGateway gateway;

    // ---------------- 查询 & 视图 ----------------

    @Transactional
    public List<OrderView> list(User viewer) {
        var q = Order.<Order>listAll().stream()
                .filter(o -> viewer.role == Role.CUSTOMER_SERVICE
                        || (viewer.role == Role.CUSTOMER && o.customer.id.equals(viewer.id))
                        || (viewer.role == Role.CLEANER && o.cleaner.id.equals(viewer.id)))
                .sorted(Comparator.comparing((Order o) -> o.scheduledStart).reversed())
                .toList();
        return q.stream().map(o -> toView(o, viewer)).toList();
    }

    @Transactional
    public OrderView getView(Long id, User viewer) {
        return toView(getOrderFor(id, viewer), viewer);
    }

    public Order getOrderFor(Long id, User viewer) {
        Order o = Order.findById(id);
        if (o == null) throw ApiException.notFound("订单不存在：" + id);
        if (viewer.role == Role.CLEANER && !o.cleaner.id.equals(viewer.id))
            throw ApiException.forbidden("钟点工只能查看自己的订单");
        if (viewer.role == Role.CUSTOMER && !o.customer.id.equals(viewer.id))
            throw ApiException.forbidden("只能查看自己的订单");
        return o;
    }

    private boolean hasOpenDispute(Order o) {
        return Dispute.count("order = ?1 and status = ?2", o, DisputeStatus.OPEN) > 0;
    }

    private void assertNotFrozen(Order o) {
        if (hasOpenDispute(o))
            throw ApiException.conflict("订单存在挂起争议，争议金额冻结期间任何一方都不能改动计时，请等待客服处理");
        if (o.status == OrderStatus.SETTLED)
            throw ApiException.conflict("订单已结算，时间记录不可再变更；如有异议请申请退款，由客服按售后流程处理");
    }

    OrderView toView(Order o, User viewer) {
        BillingService.Bill b = billing.calculate(o);
        boolean frozen = hasOpenDispute(o);
        boolean party = o.customer.id.equals(viewer.id) || o.cleaner.id.equals(viewer.id);
        boolean isCustomer = o.customer.id.equals(viewer.id);
        boolean isCleaner = o.cleaner.id.equals(viewer.id);

        List<TimeLog> logs = TimeLog.<TimeLog>list("order", o).stream()
                .sorted(Comparator.comparing((TimeLog l) -> l.effectiveAt())).toList();
        List<Dispute> disputes = Dispute.<Dispute>list("order", o).stream()
                .sorted(Comparator.comparing(d -> d.createdAt)).toList();
        Settlement st = Settlement.find("order", o).firstResult();
        List<Refund> refunds = Refund.<Refund>list("order", o).stream()
                .sorted(Comparator.comparing(r -> r.createdAt)).toList();

        boolean hasArrive = logs.stream().anyMatch(l -> l.type == LogType.ARRIVE);
        boolean hasLeave = logs.stream().anyMatch(l -> l.type == LogType.LEAVE);
        long paidTotal = st == null ? 0 : st.totalCents;
        long refunded = refunds.stream()
                .filter(r -> r.status == RefundStatus.PAID || r.status == RefundStatus.PROCESSING)
                .mapToLong(r -> r.refundCents).sum();

        var perms = new OrderView.Permissions(
                viewer.role.name(), party,
                isCleaner && !frozen && !hasLeave && o.status != OrderStatus.SETTLED,
                party && !frozen && o.status != OrderStatus.SETTLED,
                party && !frozen && o.status != OrderStatus.SETTLED,
                viewer.role == Role.CUSTOMER_SERVICE,
                isCustomer && !frozen && hasLeave && !o.customerConfirmedComplete,
                isCustomer && !frozen && o.customerConfirmedComplete && st == null && !frozen,
                isCustomer && o.status == OrderStatus.SETTLED && paidTotal - refunded > 0,
                frozen,
                frozen ? "存在挂起争议，计时与结算均已冻结" : null
        );

        return new OrderView(
                o.id, o.orderNo, o.status.name(), statusLabel(o),
                person(o.customer), person(o.cleaner),
                o.workContent,
                fmt(o.scheduledStart), fmt(o.scheduledEnd), o.scheduledMinutes(),
                o.rateCents, o.lateGraceMinutes, o.customerConfirmedComplete,
                new OrderView.BillView(
                        fmt(b.arriveAt()), fmt(b.leaveAt()),
                        b.scheduledMinutes(), b.actualMinutes(), b.outMinutes(), b.deductMinutes(),
                        b.lateMinutes(), b.earlyMinutes(), b.overtimeMinutes(),
                        b.baseCents(), b.lateDeductCents(), b.earlyDeductCents(),
                        b.outDeductCents(), b.qualityDeductCents(), b.overtimeCents(), b.approvedExtraCents(),
                        b.heldCents(), b.payableCents(),
                        b.items().stream().map(i -> new OrderView.BillView.Item(i.label(), i.cents(), i.kind())).toList()
                ),
                logs.stream().map(l -> new OrderView.LogView(
                        l.id, l.reporter.name, l.reporter.role.label,
                        l.type.name(), l.type.label, fmt(l.claimedAt), fmt(l.adoptedAt),
                        fmt(l.effectiveAt()), l.location, l.status.name(), logStatusLabel(l.status),
                        l.conflictWith == null ? null : l.conflictWith.id, l.note,
                        perms.canConfirmLogs() && l.status == LogStatus.PENDING
                                && !l.reporter.id.equals(viewer.id) && viewer.role != Role.CUSTOMER_SERVICE
                )).toList(),
                disputes.stream().map(d -> new OrderView.DisputeView(
                        d.id, d.raisedBy.name, d.raisedBy.role.label,
                        d.reason.name(), d.reason.label, d.description,
                        d.status.name(), d.status.label, d.heldCents,
                        fmt(d.adoptedArriveAt), fmt(d.adoptedLeaveAt), d.serviceAccepted,
                        d.deductMinutes, d.approvedExtraCents,
                        d.releaseToCleanerCents, d.refundToCustomerCents,
                        d.resolution, d.resolvedBy == null ? null : d.resolvedBy.name,
                        fmt(d.createdAt), fmt(d.resolvedAt)
                )).toList(),
                st == null ? null : new OrderView.SettlementView(
                        st.id, st.idempotencyKey, st.baseCents, st.lateDeductCents, st.earlyDeductCents,
                        st.outDeductCents, st.extraCents, st.qualityDeductCents, st.totalCents,
                        st.paymentTxnNo, st.paidCents, fmt(st.paidAt), false
                ),
                refunds.stream().map(r -> new OrderView.RefundView(
                        r.id, r.requestCents, r.refundCents, r.status.name(), r.status.label,
                        r.paymentCallbackId, r.refundTxnNo, r.reason,
                        fmt(r.createdAt), fmt(r.processedAt), false
                )).toList(),
                perms
        );
    }

    private OrderView.Person person(User u) {
        return new OrderView.Person(u.id, u.name, u.role.label);
    }

    // ---------------- 订单 ----------------

    @Transactional
    public Order create(User customer, String cleanerName, String workContent,
                        LocalDateTime start, LocalDateTime end, long rateCents, int grace) {
        if (customer.role != Role.CUSTOMER) throw ApiException.forbidden("仅客户可下单");
        if (!end.isAfter(start)) throw ApiException.badRequest("预约结束时间必须晚于开始时间（支持跨日）");
        User cleaner = User.findByName(cleanerName);
        if (cleaner == null || cleaner.role != Role.CLEANER)
            throw ApiException.badRequest("钟点工不存在：" + cleanerName);
        Order o = new Order();
        o.orderNo = "ORD" + System.currentTimeMillis() % 10_000_000 + "-" + (Order.count() + 1);
        o.customer = customer;
        o.cleaner = cleaner;
        o.workContent = workContent;
        o.scheduledStart = start;
        o.scheduledEnd = end;
        o.rateCents = rateCents;
        o.lateGraceMinutes = grace;
        o.persist();
        return o;
    }

    // ---------------- 打卡 / 时间记录 ----------------

    @Transactional
    public TimeLog clock(Long orderId, User cleaner, LogType type, LocalDateTime at, String note) {
        Order o = getOrderFor(orderId, cleaner);
        if (cleaner.role != Role.CLEANER || !o.cleaner.id.equals(cleaner.id))
            throw ApiException.forbidden("仅本单钟点工可打卡");
        assertNotFrozen(o);
        if (type != LogType.ARRIVE && type != LogType.LEAVE)
            throw ApiException.badRequest("打卡只支持到达/离开");
        boolean exists = TimeLog.<TimeLog>list("order", o).stream().anyMatch(l -> l.type == type);
        if (exists) throw ApiException.conflict(type.label + "打卡已存在，记录不可修改；如有异议请发起争议由客服处理");

        TimeLog l = new TimeLog();
        l.order = o; l.reporter = cleaner; l.type = type; l.claimedAt = at;
        l.location = LocationSimulator.describe(o, at);
        l.note = note;
        l.persist();
        recomputeStatus(o);
        return l;
    }

    /** 双方均可登记中途外出/返回 */
    @Transactional
    public TimeLog reportMidway(Long orderId, User viewer, LogType type, LocalDateTime at, String note) {
        Order o = getOrderFor(orderId, viewer);
        if (viewer.role == Role.CUSTOMER_SERVICE) throw ApiException.forbidden("客服不登记服务记录");
        assertNotFrozen(o);
        if (type != LogType.OUT && type != LogType.BACK)
            throw ApiException.badRequest("仅支持登记外出/返回");
        TimeLog l = new TimeLog();
        l.order = o; l.reporter = viewer; l.type = type; l.claimedAt = at;
        l.location = LocationSimulator.describe(o, at);
        l.note = note;
        l.persist();
        return l;
    }

    /** 对方确认一条时间记录；拒绝即构成确认冲突，自动挂起争议金额交客服处理 */
    @Transactional
    public void confirmLog(Long orderId, Long logId, User viewer, boolean accept, String note) {
        Order o = getOrderFor(orderId, viewer);
        TimeLog l = TimeLog.findById(logId);
        if (l == null || !l.order.id.equals(o.id)) throw ApiException.notFound("记录不存在");
        if (l.reporter.id.equals(viewer.id)) throw ApiException.forbidden("不能确认自己提交的记录");
        if (viewer.role == Role.CUSTOMER_SERVICE) throw ApiException.forbidden("客服不能代替双方确认记录");
        assertNotFrozen(o);
        if (l.status != LogStatus.PENDING) throw ApiException.conflict("该记录已处理，不能重复确认");

        if (accept) {
            l.status = LogStatus.CONFIRMED;
            if (note != null) l.note = (l.note == null ? "" : l.note + "｜") + "对方确认：" + note;
        } else {
            l.status = LogStatus.REJECTED;
            if (note != null) l.note = (l.note == null ? "" : l.note + "｜") + "对方否认：" + note;
            // 确认冲突 -> 挂起争议金额，冻结计时，等待客服
            Dispute d = new Dispute();
            d.order = o;
            d.raisedBy = viewer;
            d.reason = DisputeReason.TIME_CONFLICT;
            d.description = "双方对「%s」时间主张不一致：%s 主张 %s，%s 否认。%s"
                    .formatted(l.type.label, l.reporter.name, fmt(l.claimedAt), viewer.name,
                            note == null ? "" : "备注：" + note);
            d.heldCents = billing.estimateHold(o);
            d.persist();
            l.conflictWith = null;
        }
        recomputeStatus(o);
    }

    /** 客户确认任务完成（客服无法代劳；加时费从确认起生效） */
    @Transactional
    public void confirmComplete(Long orderId, User customer, boolean accepted) {
        Order o = getOrderFor(orderId, customer);
        if (!o.customer.id.equals(customer.id)) throw ApiException.forbidden("仅客户本人可确认任务完成，客服不能代确认");
        assertNotFrozen(o);
        boolean hasLeave = TimeLog.<TimeLog>list("order", o).stream().anyMatch(l -> l.type == LogType.LEAVE);
        if (!hasLeave) throw ApiException.conflict("钟点工尚未离开打卡，不能确认完成");
        if (!accepted) {
            // 客户拒签 -> 争议挂起
            Dispute d = new Dispute();
            d.order = o; d.raisedBy = customer;
            d.reason = DisputeReason.REJECT_SIGN;
            d.description = "客户拒签确认任务完成";
            d.heldCents = billing.estimateHold(o);
            d.persist();
            recomputeStatus(o);
            return;
        }
        o.customerConfirmedComplete = true;
        recomputeStatus(o);
    }

    // ---------------- 争议 ----------------

    @Transactional
    public Dispute raiseDispute(Long orderId, User viewer, DisputeReason reason, String description, Long heldCents) {
        Order o = getOrderFor(orderId, viewer);
        if (viewer.role == Role.CUSTOMER_SERVICE) throw ApiException.forbidden("客服不发起争议");
        assertNotFrozen(o);
        if (o.status == OrderStatus.SETTLED) throw ApiException.conflict("已结算订单请走退款流程");
        Dispute d = new Dispute();
        d.order = o; d.raisedBy = viewer; d.reason = reason;
        d.description = description == null || description.isBlank() ? reason.label : description;
        d.heldCents = heldCents != null && heldCents > 0 ? heldCents : billing.estimateHold(o);
        d.persist();
        recomputeStatus(o);
        return d;
    }

    /** 客服裁定：采纳时间、认定完成、工时扣减、加活核准、挂起金额去向 —— 只有客服能写这些 */
    @Transactional
    public void resolveDispute(Long disputeId, User cs,
                               LocalDateTime adoptedArrive, LocalDateTime adoptedLeave,
                               Boolean serviceAccepted, Integer deductMinutes,
                               Long approvedExtraCents, Long releaseCents, Long refundCents,
                               String resolution) {
        if (cs.role != Role.CUSTOMER_SERVICE) throw ApiException.forbidden("仅客服可裁定争议");
        Dispute d = Dispute.findById(disputeId);
        if (d == null) throw ApiException.notFound("争议不存在");
        if (d.status != DisputeStatus.OPEN) throw ApiException.conflict("争议已处理，不能重复裁定");
        Order o = d.order;

        long release = releaseCents == null ? 0 : Math.max(0, releaseCents);
        long refund = refundCents == null ? 0 : Math.max(0, refundCents);
        if (release + refund > d.heldCents)
            throw ApiException.badRequest("挂起金额合计 ¥%s 中，释放给钟点工 ¥%s 与退客户 ¥%s 之和超出挂起额"
                    .formatted(yuan(d.heldCents), yuan(release), yuan(refund)));
        if (deductMinutes != null && deductMinutes < 0) throw ApiException.badRequest("扣减分钟不能为负");
        if (approvedExtraCents != null && approvedExtraCents < 0) throw ApiException.badRequest("追加费用不能为负");

        d.adoptedArriveAt = adoptedArrive;
        d.adoptedLeaveAt = adoptedLeave;
        d.serviceAccepted = serviceAccepted;
        d.deductMinutes = deductMinutes;
        d.approvedExtraCents = approvedExtraCents;
        d.releaseToCleanerCents = release;
        d.refundToCustomerCents = refund;
        d.resolution = resolution;
        d.resolvedBy = cs;
        d.status = DisputeStatus.RESOLVED;
        d.resolvedAt = LocalDateTime.now();

        // 将裁定时间落到被争议/待确认的打卡记录上（唯一可覆盖计时的途径）
        if (adoptedArrive != null) adoptTime(o, LogType.ARRIVE, adoptedArrive, d);
        if (adoptedLeave != null) adoptTime(o, LogType.LEAVE, adoptedLeave, d);
        if (Boolean.FALSE.equals(serviceAccepted)) o.customerConfirmedComplete = false;

        recomputeStatus(o);
    }

    private void adoptTime(Order o, LogType type, LocalDateTime at, Dispute d) {
        List<TimeLog> ls = TimeLog.<TimeLog>list("order", o).stream()
                .filter(l -> l.type == type && l.status != LogStatus.CONFIRMED).toList();
        TimeLog target = ls.stream().filter(l -> l.status == LogStatus.REJECTED).findFirst()
                .orElseGet(() -> ls.stream().findFirst().orElse(null));
        if (target != null) {
            target.status = LogStatus.ADJUDICATED;
            target.adoptedAt = at;
            target.resolvedByDispute = d;
        } else {
            // 双方都没有该类型记录时，以客服名义登记一条裁定记录
            TimeLog l = new TimeLog();
            l.order = o; l.reporter = d.resolvedBy; l.type = type;
            l.claimedAt = at; l.adoptedAt = at;
            l.status = LogStatus.ADJUDICATED;
            l.resolvedByDispute = d;
            l.note = "客服依据双方记录裁定";
            l.persist();
        }
    }

    // ---------------- 结算（幂等扣款） ----------------

    @Transactional
    public OrderView settle(Long orderId, User customer, String idempotencyKey) {
        Order o = getOrderFor(orderId, customer);
        if (!o.customer.id.equals(customer.id)) throw ApiException.forbidden("仅客户可确认结算付款");
        Settlement existing = Settlement.find("order", o).firstResult();
        if (existing != null) {
            // 重复提交：直接返回首次结算结果，绝不二次扣款
            return withReplay(toView(o, customer), existing.idempotencyKey);
        }
        assertNotFrozen(o);
        if (!o.customerConfirmedComplete)
            throw ApiException.conflict("请先由客户确认任务完成后再结算（客服不能代确认）");

        BillingService.Bill b = billing.calculate(o);
        Settlement s = new Settlement();
        s.order = o;
        s.idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? "settle-" + o.orderNo : idempotencyKey;
        s.baseCents = b.baseCents();
        s.lateDeductCents = b.lateDeductCents();
        s.earlyDeductCents = b.earlyDeductCents();
        s.outDeductCents = b.outDeductCents();
        s.extraCents = b.overtimeCents() + b.approvedExtraCents();
        s.qualityDeductCents = b.qualityDeductCents();
        s.heldCents = b.heldCents();
        s.totalCents = b.payableCents();
        try {
            s.persist();
            s.flush(); // 触发 idempotencyKey 唯一约束，挡住并发重复提交
        } catch (PersistenceException e) {
            if (isUniqueViolation(e)) {
                Settlement first = Settlement.find("order", o).firstResult();
                return withReplay(toView(o, customer), first == null ? s.idempotencyKey : first.idempotencyKey);
            }
            throw e;
        }
        gateway.charge(s); // 模拟扣款
        recomputeStatus(o);
        return toView(o, customer);
    }

    private boolean isUniqueViolation(Throwable t) {
        while (t != null) {
            if (t instanceof ConstraintViolationException) return true;
            t = t.getCause();
        }
        return false;
    }

    private OrderView withReplay(OrderView v, String key) {
        var st = v.settlement();
        var replay = new OrderView.SettlementView(st.id(), key, st.baseCents(), st.lateDeductCents(),
                st.earlyDeductCents(), st.outDeductCents(), st.extraCents(), st.qualityDeductCents(), st.totalCents(),
                st.paymentTxnNo(), st.paidCents(), st.paidAt(), true);
        return new OrderView(v.id(), v.orderNo(), v.status(), v.statusLabel(), v.customer(), v.cleaner(),
                v.workContent(), v.scheduledStart(), v.scheduledEnd(), v.scheduledMinutes(),
                v.rateCents(), v.lateGraceMinutes(), v.customerConfirmedComplete(), v.bill(),
                v.logs(), v.disputes(), replay, v.refunds(), v.perms());
    }

    // ---------------- 退款（幂等回执） ----------------

    @Transactional
    public Refund requestRefund(Long orderId, User customer, Long cents, String reason) {
        Order o = getOrderFor(orderId, customer);
        if (!o.customer.id.equals(customer.id)) throw ApiException.forbidden("仅客户可申请退款");
        Settlement s = Settlement.find("order", o).firstResult();
        if (s == null) throw ApiException.conflict("订单尚未结算，不能退款");
        if (cents == null || cents <= 0) throw ApiException.badRequest("退款金额必须大于 0");
        long inFlight = Refund.<Refund>list("order", o).stream()
                .filter(r -> r.status == RefundStatus.PAID || r.status == RefundStatus.PROCESSING)
                .mapToLong(r -> r.refundCents).sum();
        if (inFlight + cents > s.totalCents)
            throw ApiException.badRequest("退款总额 ¥%s 超过实付金额 ¥%s（已退/处理中 ¥%s）"
                    .formatted(yuan(inFlight + cents), yuan(s.totalCents), yuan(inFlight)));
        Refund r = new Refund();
        r.order = o; r.settlement = s;
        r.requestCents = cents; r.refundCents = cents;
        r.reason = reason;
        r.persist();
        return r;
    }

    /** 客服审核通过 -> 支付渠道生成退款回执号，进入处理中 */
    @Transactional
    public Refund approveRefund(Long refundId, User cs) {
        if (cs.role != Role.CUSTOMER_SERVICE) throw ApiException.forbidden("仅客服可审核退款");
        Refund r = Refund.findById(refundId);
        if (r == null) throw ApiException.notFound("退款单不存在");
        if (r.status != RefundStatus.PENDING) throw ApiException.conflict("退款单已处理");
        Settlement s = r.settlement;
        long inFlight = Refund.<Refund>list("order", r.order).stream()
                .filter(x -> x.status == RefundStatus.PAID || x.status == RefundStatus.PROCESSING)
                .mapToLong(x -> x.refundCents).sum();
        // 审核时再次按实付上限夹紧，杜绝多退
        r.refundCents = Math.min(r.requestCents, Math.max(0, s.totalCents - inFlight));
        if (r.refundCents <= 0) {
            r.status = RefundStatus.FAILED;
            r.reason = (r.reason == null ? "" : r.reason + "｜") + "已退金额达到实付上限，拒绝";
            return r;
        }
        r.status = RefundStatus.PROCESSING;
        r.paymentCallbackId = "CB-" + r.order.orderNo + "-" + r.id;
        return r;
    }

    /**
     * 支付渠道回执回调。按 paymentCallbackId 幂等：
     * 同一回执号重复回传只返回首条结果，不会再次出款（多退）。
     */
    @Transactional
    public Refund refundCallback(String callbackId, String result) {
        Refund r = Refund.find("paymentCallbackId", callbackId).firstResult();
        if (r == null) throw ApiException.notFound("未知退款回执号：" + callbackId);
        if (r.status == RefundStatus.PAID) return r; // 重复回传：幂等吞掉
        if (!"SUCCESS".equalsIgnoreCase(result)) {
            r.status = RefundStatus.FAILED;
            return r;
        }
        // 出款前最后一道总额校验
        Settlement s = r.settlement;
        long others = Refund.<Refund>list("order", r.order).stream()
                .filter(x -> !x.id.equals(r.id))
                .filter(x -> x.status == RefundStatus.PAID || x.status == RefundStatus.PROCESSING)
                .mapToLong(x -> x.refundCents).sum();
        if (others + r.refundCents > s.totalCents)
            throw ApiException.conflict("退款总额将超过实付金额，拦截出款");
        r.status = RefundStatus.PAID;
        gateway.payout(r);
        return r;
    }

    // ---------------- 状态机 ----------------

    private void recomputeStatus(Order o) {
        if (o.status == OrderStatus.CANCELLED) return;
        if (hasOpenDispute(o)) { o.status = OrderStatus.DISPUTED; return; }
        if (Settlement.find("order", o).firstResult() != null) { o.status = OrderStatus.SETTLED; return; }
        List<TimeLog> logs = TimeLog.<TimeLog>list("order", o);
        boolean hasLeave = logs.stream().anyMatch(l -> l.type == LogType.LEAVE && l.status != LogStatus.REJECTED);
        boolean hasArrive = logs.stream().anyMatch(l -> l.type == LogType.ARRIVE && l.status != LogStatus.REJECTED);
        if (hasLeave) o.status = OrderStatus.COMPLETED;
        else if (hasArrive) o.status = OrderStatus.IN_PROGRESS;
        else o.status = OrderStatus.BOOKED;
    }

    // ---------------- 杂项 ----------------

    static String fmt(LocalDateTime t) { return t == null ? null : t.format(DT); }

    static String yuan(long cents) { return "%.2f".formatted(cents / 100.0); }

    private String statusLabel(Order o) {
        return switch (o.status) {
            case BOOKED -> "已预约";
            case IN_PROGRESS -> "服务中";
            case COMPLETED -> o.customerConfirmedComplete ? "已完成（待结算）" : "服务结束（待客户确认）";
            case DISPUTED -> "争议挂起";
            case SETTLED -> "已结算";
            case CANCELLED -> "已取消";
        };
    }

    private String logStatusLabel(LogStatus s) {
        return switch (s) {
            case PENDING -> "待对方确认";
            case CONFIRMED -> "对方已确认";
            case REJECTED -> "对方否认（冲突）";
            case ADJUDICATED -> "客服已裁定";
        };
    }
}
