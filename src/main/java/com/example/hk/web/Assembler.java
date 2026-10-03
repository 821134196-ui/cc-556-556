package com.example.hk.web;

import com.example.hk.model.*;
import com.example.hk.service.BillingService;
import com.example.hk.service.OrderService;
import com.example.hk.service.TimeUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.*;
import java.util.stream.Collectors;

/** 实体 → 对外 DTO 组装。 */
@ApplicationScoped
public class Assembler {

    @Inject
    EntityManager em;

    @Inject
    OrderService orderService;

    private Map<Long, String> userNames() {
        return em.createQuery("select u from UserEntity u", UserEntity.class).getResultList()
                .stream().collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));
    }

    public Dto.UserDto user(UserEntity u) {
        return new Dto.UserDto(u.getId(), u.getName(), u.getRole(), u.getPhone());
    }

    public Dto.OrderSummary summary(OrderEntity o, Map<Long, String> names) {
        return new Dto.OrderSummary(o.getId(), o.getStatus(),
                names.getOrDefault(o.getCustomerId(), "?"),
                names.getOrDefault(o.getWorkerId(), "?"),
                o.getScheduledStart(), o.getScheduledMinutes(), o.getTasks(), o.isDisputed());
    }

    public List<Dto.OrderSummary> summaries(List<OrderEntity> list) {
        Map<Long, String> names = userNames();
        return list.stream().map(o -> summary(o, names)).toList();
    }

    public Dto.OrderDetail detail(OrderEntity o, String viewerRole) {
        Map<Long, String> names = userNames();

        List<Dto.EventDto> events = o.getEvents().stream().map(e -> new Dto.EventDto(
                e.getId(), e.getEventType(), e.getActorRole(), e.getActorName(), e.getAction(),
                e.getClockAt(), TimeUtil.format(e.getClockAt()),
                e.getRecordedAt(), TimeUtil.format(e.getRecordedAt()),
                e.getLocation(), e.getNote())).toList();

        List<Dto.DisputeDto> disputes = o.getDisputes().stream().map(d -> new Dto.DisputeDto(
                d.getId(), d.getReason(), d.getRaisedByRole(), d.getRaisedByName(),
                d.getRaisedAt(), TimeUtil.format(d.getRaisedAt()), d.getStatus(),
                d.getResolution(), d.getResolutionNote(), d.getResolvedByName(),
                d.getResolvedAt() > 0 ? d.getResolvedAt() : null,
                d.getResolvedAt() > 0 ? TimeUtil.format(d.getResolvedAt()) : null,
                d.getClaimedBilledMinutes())).toList();

        List<Dto.PaymentDto> payments = o.getPayments().stream().map(p -> new Dto.PaymentDto(
                p.getId(), p.getType(), p.getIdempotencyKey(), p.getAmountFen(), p.getStatus(),
                p.getReceiptNo(), p.getCreatedAt(), TimeUtil.format(p.getCreatedAt()),
                p.getSucceededAt() > 0 ? p.getSucceededAt() : null,
                p.getSucceededAt() > 0 ? TimeUtil.format(p.getSucceededAt()) : null,
                p.getNote())).toList();

        BillingService.Breakdown current = orderService.currentBreakdown(o);

        List<OrderService.DtoConflict> conflicts = orderService.conflicts(o);
        List<Dto.TimeConflict> conflictDtos = conflicts.stream()
                .map(c -> new Dto.TimeConflict(c.point(), c.workerClock(),
                        TimeUtil.format(c.workerClock()), c.customerClock(),
                        TimeUtil.format(c.customerClock()), c.diff()))
                .toList();

        // 争议挂起或存在时间冲突时，把双方各自口径的账单都算给客服参考
        BillingService.Breakdown workerSide = null;
        BillingService.Breakdown customerSide = null;
        if (o.isDisputed() || !conflicts.isEmpty() || o.getDisputeResolution() != null) {
            Long claimed = o.getDisputes().stream()
                    .map(DisputeEntity::getClaimedBilledMinutes)
                    .filter(Objects::nonNull).reduce((a, b) -> b).orElse(null);
            workerSide = BillingService.calculateAfterResolution(o, o.getEvents(), true, null);
            customerSide = BillingService.calculateAfterResolution(o, o.getEvents(), false,
                    claimed);
        }

        long charged = o.getPayments().stream()
                .filter(p -> "CHARGE".equals(p.getType()) && "SUCCEEDED".equals(p.getStatus()))
                .mapToLong(PaymentEntity::getAmountFen).sum();
        long refunded = o.getPayments().stream()
                .filter(p -> "REFUND".equals(p.getType()) && "SUCCEEDED".equals(p.getStatus()))
                .mapToLong(PaymentEntity::getAmountFen).sum();

        return new Dto.OrderDetail(
                o.getId(), o.getStatus(), statusText(o),
                o.getCustomerId(), names.getOrDefault(o.getCustomerId(), "?"),
                o.getWorkerId(), names.getOrDefault(o.getWorkerId(), "?"),
                o.getScheduledStart(), o.getScheduledMinutes(),
                o.getTasks(), o.getHourlyRateFen(),
                o.isDisputed(), o.isDisputed(),
                o.getDisputeReason(), o.getDisputeResolution(),
                events, disputes, payments,
                current, workerSide, customerSide, conflictDtos,
                charged, refunded, charged - refunded,
                o.getFinalizedAt() > 0 ? TimeUtil.format(o.getFinalizedAt()) : null,
                o.getFinalizerName(), viewerRole);
    }

    public static String statusText(OrderEntity o) {
        String base = switch (o.getStatus()) {
            case "BOOKED" -> "已预约";
            case "IN_PROGRESS" -> "服务中/待确认完成";
            case "DISPUTED" -> "争议挂起（金额冻结）";
            case "COMPLETED" -> "客户已确认完成";
            case "SETTLED" -> "已结算";
            default -> o.getStatus();
        };
        if (o.isDisputed() && !"DISPUTED".equals(o.getStatus())) {
            base += "（争议挂起中）";
        }
        return base;
    }
}
