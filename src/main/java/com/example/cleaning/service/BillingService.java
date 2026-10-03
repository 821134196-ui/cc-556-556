package com.example.cleaning.service;

import com.example.cleaning.entity.*;
import com.example.cleaning.model.DisputeStatus;
import com.example.cleaning.model.LogStatus;
import com.example.cleaning.model.LogType;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 计费规则（所有金额单位：分）：
 * 1. 约定费用 = 约定分钟数 × 时薪 / 60
 * 2. 迟到：实际到达晚于预约开始，超过宽限分钟的部分按分钟扣减
 * 3. 提前结束：实际离开早于预约结束，少做的分钟按分钟扣减
 * 4. 加时：实际在岗超过约定时长，客户已确认完成则自动按分钟计加时费；
 *    临时加活有异议的，经客服核准则计入 approvedExtra
 * 5. 中途外出：外出段（OUT→BACK）不计费；客服裁定的扣减分钟也不计费
 * 6. 争议挂起：未解决争议的 heldCents 从应付款中冻结，挂起期间不可结算
 * 跨日服务（如 22:00 至次日 02:00）直接用 LocalDateTime 时间差计算，天然支持。
 */
@ApplicationScoped
public class BillingService {

    public long minutesCost(long minutes, long rateCents) {
        return Math.round(rateCents * minutes / 60.0);
    }

    /** 找到当前计费采用的到达时间 */
    public LocalDateTime effectiveArrive(Order order) {
        List<TimeLog> arrives = logsOfType(order, LogType.ARRIVE);
        return arrives.stream()
                .filter(l -> l.status == LogStatus.CONFIRMED || l.status == LogStatus.ADJUDICATED)
                .map(TimeLog::effectiveAt).min(Comparator.naturalOrder())
                .orElseGet(() -> arrives.stream()
                        .filter(l -> l.status == LogStatus.PENDING)
                        .map(l -> l.claimedAt).min(Comparator.naturalOrder()).orElse(null));
    }

    /** 找到当前计费采用的离开时间 */
    public LocalDateTime effectiveLeave(Order order) {
        List<TimeLog> leaves = logsOfType(order, LogType.LEAVE);
        return leaves.stream()
                .filter(l -> l.status == LogStatus.CONFIRMED || l.status == LogStatus.ADJUDICATED)
                .map(TimeLog::effectiveAt).max(Comparator.naturalOrder())
                .orElseGet(() -> leaves.stream()
                        .filter(l -> l.status == LogStatus.PENDING)
                        .map(l -> l.claimedAt).max(Comparator.naturalOrder()).orElse(null));
    }

    private List<TimeLog> logsOfType(Order order, LogType type) {
        return TimeLog.<TimeLog>list("order", order).stream()
                .filter(l -> l.type == type && l.status != LogStatus.REJECTED)
                .toList();
    }

    /** 外出段合计分钟（成对的 OUT→BACK，按发生顺序配对；未闭合的外出段不计入） */
    public long outMinutes(Order order) {
        List<TimeLog> outs = TimeLog.<TimeLog>list("order", order).stream()
                .filter(l -> (l.type == LogType.OUT || l.type == LogType.BACK)
                        && l.status != LogStatus.REJECTED)
                .sorted(Comparator.comparing(TimeLog::effectiveAt))
                .toList();
        long mins = 0;
        LocalDateTime outSince = null;
        for (TimeLog l : outs) {
            if (l.type == LogType.OUT && outSince == null) outSince = l.effectiveAt();
            else if (l.type == LogType.BACK && outSince != null) {
                mins += Math.max(0, Duration.between(outSince, l.effectiveAt()).toMinutes());
                outSince = null;
            }
        }
        return mins;
    }

    public Bill calculate(Order order) {
        long scheduled = order.scheduledMinutes();
        long base = minutesCost(scheduled, order.rateCents);

        LocalDateTime arrive = effectiveArrive(order);
        LocalDateTime leave = effectiveLeave(order);
        long outMins = outMinutes(order);

        long lateMins = 0, lateDeduct = 0;
        long earlyMins = 0, earlyDeduct = 0;
        long onSiteMins = 0;

        if (arrive != null) {
            lateMins = Math.max(0, Duration.between(order.scheduledStart, arrive).toMinutes());
            lateDeduct = minutesCost(Math.max(0, lateMins - order.lateGraceMinutes), order.rateCents);
        }
        if (leave != null) {
            earlyMins = Math.max(0, Duration.between(leave, order.scheduledEnd).toMinutes());
            earlyDeduct = minutesCost(earlyMins, order.rateCents);
        }
        if (arrive != null && leave != null) {
            onSiteMins = Math.max(0, Duration.between(arrive, leave).toMinutes() - outMins);
        }

        // 客服在已处理争议中的裁定
        List<Dispute> resolved = Dispute.<Dispute>list("order", order).stream()
                .filter(d -> d.status == DisputeStatus.RESOLVED).toList();
        long deductMins = resolved.stream()
                .filter(d -> d.deductMinutes != null)
                .mapToInt(d -> d.deductMinutes).sum();
        deductMins = Math.min(deductMins, Math.max(0, onSiteMins));
        long qualityDeduct = minutesCost(deductMins, order.rateCents);
        // 中途外出时段不付费（按分钟从约定打包费用中扣）
        long outDeduct = minutesCost(outMins, order.rateCents);
        long approvedExtra = resolved.stream()
                .filter(d -> d.approvedExtraCents != null)
                .mapToLong(d -> d.approvedExtraCents).sum();
        // 客服裁定挂起金额中直接退给客户的部分（永久扣减，不付给钟点工）
        long adjudicatedRefund = resolved.stream()
                .filter(d -> d.refundToCustomerCents != null)
                .mapToLong(d -> d.refundToCustomerCents).sum();

        // 加时：实际离开晚于预约结束的分钟（迟到与加时独立计费，客户确认完成后生效）
        long overtimeMins = leave != null
                ? Math.max(0, Duration.between(order.scheduledEnd, leave).toMinutes()) : 0;
        long overtime = (order.customerConfirmedComplete && overtimeMins > 0)
                ? minutesCost(overtimeMins, order.rateCents) : 0;

        long held = Dispute.<Dispute>list("order", order).stream()
                .filter(d -> d.status == DisputeStatus.OPEN)
                .mapToLong(d -> d.heldCents).sum();

        long provisional = base - lateDeduct - earlyDeduct - outDeduct - qualityDeduct - adjudicatedRefund
                + overtime + approvedExtra;
        long payable = Math.max(0, provisional - held);
        held = Math.min(held, Math.max(0, provisional)); // 挂起额不超过应付额

        List<BillItem> items = new ArrayList<>();
        items.add(new BillItem("约定费用（%d 分钟 × ¥%.2f/时）".formatted(scheduled, order.rateYuan()), base, "base"));
        if (lateDeduct > 0)
            items.add(new BillItem("迟到扣减（迟到 %d 分钟，宽限 %d 分钟）".formatted(lateMins, order.lateGraceMinutes), -lateDeduct, "deduct"));
        else if (lateMins > 0)
            items.add(new BillItem("迟到 %d 分钟，在 %d 分钟宽限内不扣款".formatted(lateMins, order.lateGraceMinutes), 0, "note"));
        if (earlyDeduct > 0)
            items.add(new BillItem("提前结束扣减（少做 %d 分钟）".formatted(earlyMins), -earlyDeduct, "deduct"));
        if (outMins > 0)
            items.add(new BillItem("中途外出 %d 分钟不计费".formatted(outMins), -outDeduct, "deduct"));
        if (qualityDeduct > 0)
            items.add(new BillItem("客服裁定扣减（%d 分钟）".formatted(deductMins), -qualityDeduct, "deduct"));
        if (overtime > 0)
            items.add(new BillItem("加时费（超时 %d 分钟，客户已确认）".formatted(overtimeMins), overtime, "extra"));
        if (approvedExtra > 0)
            items.add(new BillItem("客服核准加活/加时费", approvedExtra, "extra"));
        if (adjudicatedRefund > 0)
            items.add(new BillItem("客服裁定挂起金额退客户", -adjudicatedRefund, "deduct"));
        if (held > 0)
            items.add(new BillItem("争议挂起（客服处理前冻结，不付给钟点工）", -held, "held"));

        return new Bill(scheduled, arrive, leave, outMins, deductMins, onSiteMins,
                lateMins, earlyMins, overtimeMins,
                base, lateDeduct, earlyDeduct, outDeduct, qualityDeduct, overtime, approvedExtra, held, payable, items);
    }

    /** 争议发起时估算应挂起的金额（分） */
    public long estimateHold(Order order) {
        Bill b = calculate(order);
        long maxHold = Math.max(0, b.baseCents() - b.lateDeductCents() - b.earlyDeductCents()
                - b.outDeductCents() - b.qualityDeductCents()
                + b.overtimeCents() + b.approvedExtraCents());
        // 默认挂起约定费用的 30% 与当前应付中较小者，客服可在处理时调整
        return Math.min(maxHold, Math.round(maxHold * 0.3 / 10.0) * 10);
    }

    public record BillItem(String label, long cents, String kind) {}

    public record Bill(
            long scheduledMinutes, LocalDateTime arriveAt, LocalDateTime leaveAt,
            long outMinutes, long deductMinutes, long actualMinutes,
            long lateMinutes, long earlyMinutes, long overtimeMinutes,
            long baseCents, long lateDeductCents, long earlyDeductCents,
            long outDeductCents, long qualityDeductCents,
            long overtimeCents, long approvedExtraCents,
            long heldCents, long payableCents, List<BillItem> items) {
    }
}
