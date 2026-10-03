package com.example.hk.service;

import com.example.hk.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单/计时/争议/结算核心服务。
 * 权限模型（userId + role 模拟登录身份）：
 *  - WORKER 只能操作 workerId == 自己的订单；
 *  - CUSTOMER 只能操作 customerId == 自己的订单，且只有客户能确认任务完成；
 *  - STAFF 可查看全部、处理争议，但不能代客户确认完成、不能直接改计时；
 *  - 争议挂起后，除 STAFF 处理争议外，任何计时/结算操作一律拒绝。
 */
@ApplicationScoped
public class OrderService {

    @Inject
    EntityManager em;

    @Inject
    MockPaymentGateway gateway;

    // ---------------------------------------------------------------- 订单

    @Transactional
    public OrderEntity createOrder(long now, long customerId, long workerId,
                                   String scheduledStart, int scheduledMinutes,
                                   String tasks, String address, int hourlyRateFen) {
        requireUser(customerId, "CUSTOMER");
        requireUser(workerId, "WORKER");
        OrderEntity o = new OrderEntity(now, customerId, workerId, scheduledStart,
                scheduledMinutes, tasks, address, hourlyRateFen);
        em.persist(o);
        em.flush();
        return o;
    }

    @Transactional
    public List<OrderEntity> listOrders(long viewerId, String viewerRole) {
        if ("STAFF".equals(viewerRole)) {
            return em.createQuery("select o from OrderEntity o order by o.id", OrderEntity.class)
                    .getResultList();
        }
        if ("WORKER".equals(viewerRole)) {
            // 服务员只能查看自己的订单
            return em.createQuery(
                            "select o from OrderEntity o where o.workerId = :uid order by o.id",
                            OrderEntity.class)
                    .setParameter("uid", viewerId).getResultList();
        }
        return em.createQuery(
                        "select o from OrderEntity o where o.customerId = :uid order by o.id",
                        OrderEntity.class)
                .setParameter("uid", viewerId).getResultList();
    }

    @Transactional
    public OrderEntity getOrderForView(Long id, long viewerId, String viewerRole) {
        OrderEntity o = em.find(OrderEntity.class, id);
        if (o == null) {
            throw new ApiException(404, "订单不存在");
        }
        if (!canView(o, viewerId, viewerRole)) {
            throw new ApiException(403, "服务员只能查看自己的订单");
        }
        return o;
    }

    private boolean canView(OrderEntity o, long viewerId, String viewerRole) {
        return "STAFF".equals(viewerRole)
                || ("WORKER".equals(viewerRole) && o.getWorkerId() == viewerId)
                || ("CUSTOMER".equals(viewerRole) && o.getCustomerId() == viewerId);
    }

    private void requireActor(OrderEntity o, long viewerId, String viewerRole, String wantRole) {
        if (!viewerRole.equals(wantRole)) {
            throw new ApiException(403, "该操作仅限 " + roleText(wantRole) + " 执行");
        }
        long owner = wantRole.equals("WORKER") ? o.getWorkerId() : o.getCustomerId();
        if (owner != viewerId) {
            throw new ApiException(403, "不能操作他人订单");
        }
    }

    private void requireUser(long userId, String role) {
        UserEntity u = em.find(UserEntity.class, userId);
        if (u == null || !u.getRole().equals(role)) {
            throw new ApiException(400, roleText(role) + " 不存在: id=" + userId);
        }
    }

    private void assertNotFrozen(OrderEntity o) {
        if (o.isDisputed()) {
            throw new ApiException(409, "订单存在挂起争议，双方均不能改计时/结算，请等待客服处理");
        }
    }

    // ---------------------------------------------------------------- 计时

    /**
     * 留下服务记录。服务员上报到达/离开（REPORTED），客户确认（CONFIRMED）；
     * 中途外出仅服务员可记录。记录只追加，不允许修改或删除。
     */
    @Transactional
    public OrderEntity clock(long now, long orderId, long viewerId, String viewerRole,
                             String eventType, Long clockAt, String location, String note) {
        OrderEntity o = em.find(OrderEntity.class, orderId);
        if (o == null) throw new ApiException(404, "订单不存在");

        long at = clockAt != null ? clockAt : now;
        String action;
        String actorName;

        switch (eventType) {
            case "ARRIVAL", "DEPARTURE" -> {
                boolean worker = "WORKER".equals(viewerRole);
                requireActor(o, viewerId, viewerRole, worker ? "WORKER" : "CUSTOMER");
                assertNotFrozen(o);
                if ("SETTLED".equals(o.getStatus()) || "COMPLETED".equals(o.getStatus())) {
                    throw new ApiException(409, "订单已完成/结算，不能再追加计时记录");
                }
                action = worker ? "REPORTED" : "CONFIRMED";
                actorName = em.find(UserEntity.class, viewerId).getName();

                boolean duplicated = eventType.equals("ARRIVAL")
                        ? (worker ? o.getWorkerArrivalStatus() != null
                                  : o.getCustomerArrivalStatus() != null)
                        : (worker ? o.getWorkerDepartureStatus() != null
                                  : o.getCustomerDepartureStatus() != null);
                if (duplicated) {
                    throw new ApiException(409, (eventType.equals("ARRIVAL") ? "到达" : "离开")
                            + "记录已存在，计时记录不可修改");
                }

                if (eventType.equals("ARRIVAL")) {
                    if (worker) {
                        o.setWorkerArrivalStatus(action);
                        if ("BOOKED".equals(o.getStatus())) o.setStatus("IN_PROGRESS");
                    } else {
                        o.setCustomerArrivalStatus(action);
                    }
                } else {
                    if (worker) o.setWorkerDepartureStatus(action);
                    else o.setCustomerDepartureStatus(action);
                }
            }
            case "PAUSE_OUT", "RETURN" -> {
                requireActor(o, viewerId, viewerRole, "WORKER");
                assertNotFrozen(o);
                if ("SETTLED".equals(o.getStatus()) || "COMPLETED".equals(o.getStatus())) {
                    throw new ApiException(409, "订单已完成/结算，不能再追加计时记录");
                }
                action = "REPORTED";
                actorName = em.find(UserEntity.class, viewerId).getName();
            }
            default -> throw new ApiException(400, "未知记录类型: " + eventType);
        }

        WorkEventEntity e = new WorkEventEntity(o, eventType, viewerRole, actorName,
                action, at, now, location, note);
        em.persist(e);
        o.getEvents().add(e);

        // 离开双方都记录后：若时间冲突，自动进入争议挂起；否则等待客户确认完成
        maybeDetectConflict(o, now);
        return o;
    }

    /** 双方到达/离开时间差超过 1 分钟即冲突，挂起争议，任何一方都不能继续改计时。 */
    private void maybeDetectConflict(OrderEntity o, long now) {
        if (hasConflict(o) && o.getWorkerDepartureStatus() != null
                && o.getCustomerDepartureStatus() != null && !o.isDisputed()) {
            o.setDisputed(true);
            o.setDisputeRaisedAt(now);
            o.setDisputeReason("双方打卡时间不一致，系统自动挂起，等待客服核对记录");
            DisputeEntity d = new DisputeEntity(o, o.getDisputeReason(), "SYSTEM", "系统", now);
            d.setFromStatus(o.getStatus());
            em.persist(d);
            o.getDisputes().add(d);
            o.setStatus("DISPUTED");
        }
    }

    // ---------------------------------------------------------------- 完成确认

    /** 只有客户能确认任务完成；客服、服务员调用都被拒绝。 */
    @Transactional
    public OrderEntity confirmCompletion(long now, Long orderId, long viewerId,
                                         String viewerRole) {
        OrderEntity o = em.find(OrderEntity.class, orderId);
        if (o == null) throw new ApiException(404, "订单不存在");
        requireActor(o, viewerId, viewerRole, "CUSTOMER");
        assertNotFrozen(o);
        if (o.getWorkerDepartureStatus() == null || o.getCustomerDepartureStatus() == null) {
            throw new ApiException(409, "双方离开记录未齐，不能确认完成");
        }
        if (!"IN_PROGRESS".equals(o.getStatus()) && !"BOOKED".equals(o.getStatus())) {
            throw new ApiException(409, "当前状态 " + o.getStatus() + " 不能确认完成");
        }
        o.setStatus("COMPLETED");
        o.setFinalizedAt(now);
        o.setFinalizerName(em.find(UserEntity.class, viewerId).getName());
        return o;
    }

    // ---------------------------------------------------------------- 争议

    @Transactional
    public OrderEntity raiseDispute(long now, Long orderId, long viewerId, String viewerRole,
                                    String reason, Long claimedBilledMinutes) {
        OrderEntity o = em.find(OrderEntity.class, orderId);
        if (o == null) throw new ApiException(404, "订单不存在");
        if (!canView(o, viewerId, viewerRole) || "STAFF".equals(viewerRole)) {
            throw new ApiException(403, "只有客户或服务员可以发起争议");
        }
        if (o.isDisputed()) {
            throw new ApiException(409, "已存在挂起争议");
        }
        if ("SETTLED".equals(o.getStatus())) {
            throw new ApiException(409, "订单已结算，争议请走退款流程");
        }
        UserEntity u = em.find(UserEntity.class, viewerId);
        DisputeEntity d = new DisputeEntity(o, reason, viewerRole, u.getName(), now);
        d.setFromStatus(o.getStatus());
        if (claimedBilledMinutes != null) d.setClaimedBilledMinutes(claimedBilledMinutes);
        em.persist(d);
        o.getDisputes().add(d);
        o.setDisputed(true);
        o.setDisputeReason(reason);
        o.setDisputeRaisedAt(now);
        o.setStatus("DISPUTED");
        return o;
    }

    /**
     * 客服处理争议。APPROVED=采信服务员记录，DENY=采信客户主张（可用 claimedBilledMinutes）。
     * 客服只能核对/采信双方记录，不能直接编辑任何打卡时间。
     */
    @Transactional
    public OrderEntity resolveDispute(long now, Long orderId, long staffId, String staffRole,
                                      String resolution, String note, Long claimedBilledMinutes) {
        if (!"STAFF".equals(staffRole)) {
            throw new ApiException(403, "只有客服可以处理争议");
        }
        OrderEntity o = em.find(OrderEntity.class, orderId);
        if (o == null) throw new ApiException(404, "订单不存在");
        if (!o.isDisputed()) throw new ApiException(409, "订单没有挂起争议");
        if (!"APPROVED".equals(resolution) && !"DENY".equals(resolution)) {
            throw new ApiException(400, "resolution 必须是 APPROVED 或 DENY");
        }
        UserEntity staff = em.find(UserEntity.class, staffId);
        DisputeEntity open = o.getDisputes().stream()
                .filter(d -> "OPEN".equals(d.getStatus()))
                .reduce((a, b) -> b).orElseThrow(() -> new ApiException(409, "没有待处理争议"));
        open.setStatus("RESOLVED");
        open.setResolution(resolution);
        open.setResolutionNote(note);
        open.setResolvedByName(staff.getName());
        open.setResolvedAt(now);
        if (claimedBilledMinutes != null) open.setClaimedBilledMinutes(claimedBilledMinutes);

        o.setDisputed(false);
        o.setDisputeResolution(resolution);
        o.setDisputeResolverName(staff.getName());
        o.setDisputeResolvedAt(now);

        String from = open.getFromStatus();
        // 恢复到挂起前状态；若双方离开记录已齐，直接落到待客户确认完成
        if (o.getWorkerDepartureStatus() != null && o.getCustomerDepartureStatus() != null
                && !"COMPLETED".equals(from) && !"SETTLED".equals(from)) {
            o.setStatus("IN_PROGRESS");
        } else {
            o.setStatus(from != null ? from : "IN_PROGRESS");
        }
        return o;
    }

    // ---------------------------------------------------------------- 结算

    /** 客户确认结算并扣款。幂等键：同一订单重复提交只扣一次。 */
    @Transactional
    public PaymentEntity settle(long now, Long orderId, long viewerId, String viewerRole,
                                String idempotencyKey) {
        OrderEntity o = em.find(OrderEntity.class, orderId);
        if (o == null) throw new ApiException(404, "订单不存在");
        requireActor(o, viewerId, viewerRole, "CUSTOMER");
        assertNotFrozen(o);

        // 幂等优先：已结算或同键重复提交，一律回传原扣款流水，绝不二次扣款
        String key = idempotencyKey != null ? idempotencyKey : ("charge-" + orderId);
        PaymentEntity existing = findPayment(key);
        if (existing != null) {
            return existing;
        }
        PaymentEntity orderCharge = o.getPayments().stream()
                .filter(p -> "CHARGE".equals(p.getType())).findFirst().orElse(null);
        if (orderCharge != null) {
            return orderCharge;
        }
        if (!"COMPLETED".equals(o.getStatus())) {
            throw new ApiException(409, "任务未经客户确认完成，不能结算");
        }
        BillingService.Breakdown b = currentBreakdown(o);
        if (b.payableFen <= 0) {
            throw new ApiException(409, "应收金额为 0，无需扣款");
        }
        PaymentEntity p = new PaymentEntity(o, "CHARGE", key, b.payableFen, now,
                "结算扣款：" + scenarioText(b));
        try {
            em.persist(p);
            em.flush();
        } catch (PersistenceException ex) {
            // 唯一约束并发兜底：键已存在则返回原记录
            PaymentEntity again = findPayment(key);
            if (again != null) return again;
            throw ex;
        }
        p.setReceiptNo(gateway.charge(b.payableFen));
        p.setStatus("SUCCEEDED");
        p.setSucceededAt(now);
        o.setStatus("SETTLED");
        return p;
    }

    /**
     * 支付/退款回执回传（模拟网关回调）。
     * 同一 idempotencyKey 重复回传只入账一次：退款绝不会多退。
     */
    @Transactional
    public PaymentEntity paymentCallback(long now, Long orderId, String type,
                                         String idempotencyKey, Long amountFen,
                                         String receiptNo, String status) {
        OrderEntity o = em.find(OrderEntity.class, orderId);
        if (o == null) throw new ApiException(404, "订单不存在");
        if (!"CHARGE".equals(type) && !"REFUND".equals(type)) {
            throw new ApiException(400, "type 必须是 CHARGE 或 REFUND");
        }
        PaymentEntity existing = findPayment(idempotencyKey);
        if (existing != null) {
            // 幂等：重复回传不改变任何金额与状态
            return existing;
        }
        long amount;
        String note;
        if ("REFUND".equals(type)) {
            long refundable = refundableFen(o);
            amount = amountFen != null ? amountFen : refundable;
            if (amount <= 0) throw new ApiException(409, "没有可退金额");
            if (amount > refundable) {
                throw new ApiException(409, "退款金额 " + amount + " 分超过可退余额 "
                        + refundable + " 分，拒绝多退");
            }
            note = "退款回执";
        } else {
            amount = amountFen != null ? amountFen : currentBreakdown(o).payableFen;
            note = "扣款回执";
        }
        PaymentEntity p = new PaymentEntity(o, type, idempotencyKey, amount, now, note);
        try {
            em.persist(p);
            em.flush();
        } catch (PersistenceException ex) {
            PaymentEntity again = findPayment(idempotencyKey);
            if (again != null) return again;
            throw ex;
        }
        p.setReceiptNo(receiptNo != null ? receiptNo
                : (type.equals("REFUND") ? gateway.refund(amount) : gateway.charge(amount)));
        p.setStatus(status != null ? status : "SUCCEEDED");
        p.setSucceededAt(now);
        return p;
    }

    private long refundableFen(OrderEntity o) {
        long charged = o.getPayments().stream()
                .filter(p -> "CHARGE".equals(p.getType()) && "SUCCEEDED".equals(p.getStatus()))
                .mapToLong(PaymentEntity::getAmountFen).sum();
        long refunded = o.getPayments().stream()
                .filter(p -> "REFUND".equals(p.getType()) && "SUCCEEDED".equals(p.getStatus()))
                .mapToLong(PaymentEntity::getAmountFen).sum();
        return charged - refunded;
    }

    private PaymentEntity findPayment(String key) {
        List<PaymentEntity> list = em.createQuery(
                        "select p from PaymentEntity p where p.idempotencyKey = :k",
                        PaymentEntity.class)
                .setParameter("k", key).getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    /** 回调前置检查：键是否已入账；键若属于其他订单则拒绝串用。 */
    @Transactional
    public PaymentEntity findPaymentForCheck(Long orderId, String key) {
        PaymentEntity p = findPayment(key);
        if (p != null && !p.getOrder().getId().equals(orderId)) {
            throw new ApiException(409, "幂等键已用于其他订单，拒绝串用");
        }
        return p;
    }

    /** 系统视角取订单（回调等内部流程用，不走角色过滤）。 */
    @Transactional
    public OrderEntity getOrderById(Long id) {
        OrderEntity o = em.find(OrderEntity.class, id);
        if (o == null) throw new ApiException(404, "订单不存在");
        return o;
    }

    // ---------------------------------------------------------------- 计算

    /** 当前生效的账单拆解（已处理争议时按采信方重算）。 */
    public BillingService.Breakdown currentBreakdown(OrderEntity o) {
        List<WorkEventEntity> events = o.getEvents();
        if (o.getDisputeResolution() != null) {
            Long claimed = o.getDisputes().stream()
                    .filter(d -> d.getClaimedBilledMinutes() != null)
                    .reduce((a, b) -> b)
                    .map(DisputeEntity::getClaimedBilledMinutes).orElse(null);
            return BillingService.calculateAfterResolution(o, events,
                    "APPROVED".equals(o.getDisputeResolution()), claimed);
        }
        return BillingService.calculate(o, events, null);
    }

    private BillingService.Breakdown sideBreakdown(OrderEntity o, boolean workerSide) {
        return BillingService.calculateAfterResolution(o, o.getEvents(), workerSide, null);
    }

    public boolean hasConflict(OrderEntity o) {
        return !conflicts(o).isEmpty();
    }

    /** 返回双方打卡冲突点（到达/离开时间差超过 1 分钟）。 */
    public List<DtoConflict> conflicts(OrderEntity o) {
        List<DtoConflict> out = new ArrayList<>();
        conflictPoint(o, "ARRIVAL", "到达").ifPresent(out::add);
        conflictPoint(o, "DEPARTURE", "离开").ifPresent(out::add);
        return out;
    }

    private java.util.Optional<DtoConflict> conflictPoint(OrderEntity o, String type, String label) {
        long w = 0, c = 0;
        for (WorkEventEntity e : o.getEvents()) {
            if (!e.getEventType().equals(type)) continue;
            if (e.getActorRole().equals("WORKER")) w = e.getClockAt();
            if (e.getActorRole().equals("CUSTOMER")) c = e.getClockAt();
        }
        if (w <= 0 || c <= 0 || Math.abs(w - c) <= 60_000) return java.util.Optional.empty();
        long diff = Math.abs(w - c) / 60_000;
        return java.util.Optional.of(new DtoConflict(label, w, c, diff));
    }

    /** 内部轻量冲突结构，Resource 层再转成对外 Dto.TimeConflict（带格式化时间）。 */
    public record DtoConflict(String point, long workerClock, long customerClock, long diff) {
    }

    public static String roleText(String role) {
        return switch (role) {
            case "CUSTOMER" -> "客户";
            case "WORKER" -> "服务员";
            case "STAFF" -> "客服";
            default -> role;
        };
    }

    public static String scenarioText(BillingService.Breakdown b) {
        if (b.scenario == null) return "计时未完成";
        return switch (b.scenario) {
            case "OVERTIME" -> "加时服务";
            case "EARLY" -> "提前结束";
            default -> "正常服务";
        };
    }
}
