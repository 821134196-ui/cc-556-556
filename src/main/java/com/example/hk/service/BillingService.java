package com.example.hk.service;

import com.example.hk.model.OrderEntity;
import com.example.hk.model.WorkEventEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 计费规则（全部在订单页面上对客户透明展示）：
 * <ul>
 *   <li>约定费：约定时长不足 1 小时按 1 小时向上取整 × 时薪；</li>
 *   <li>迟到：宽限 5 分钟；超出宽限的迟到分钟按 时薪/60 从约定费中扣减，扣完为止；</li>
 *   <li>加时：实际工作时长超过约定时长，超出部分每 30 分钟一档向上取整，按半小时单价加收；</li>
 *   <li>提前结束：实际工作比约定少 10 分钟以上，按实际时长（不足 1 小时按 1 小时）计费；</li>
 *   <li>中途外出：PAUSE_OUT→RETURN 区间不计入工作时长；</li>
 *   <li>争议处理结果可把“计费工作分钟”改为客户主张的分钟数（客服 DENY 客户主张成立时）。</li>
 * </ul>
 */
public final class BillingService {

    /** 迟到宽限分钟 */
    public static final long LATE_GRACE_MINUTES = 5;
    /** 提前结束容忍分钟（以内仍按约定计费） */
    public static final long EARLY_TOLERANCE_MINUTES = 10;
    /** 加时计费档位（分钟） */
    public static final long OVERTIME_STEP_MINUTES = 30;

    private BillingService() {
    }

    public static class Line {
        public final String label;
        public final long amountFen; // 正=加收 负=扣减
        public final String detail;

        public Line(String label, long amountFen, String detail) {
            this.label = label;
            this.amountFen = amountFen;
            this.detail = detail;
        }
    }

    public static class Breakdown {
        public Long arrivalMillis;
        public Long departureMillis;
        public String arrivalText;
        public String departureText;
        public boolean crossDay;
        public int dayCount;
        public long pauseMinutes;
        public Long workedMinutes;

        public long scheduledStartMillis;
        public int scheduledMinutes;
        public long lateMinutes;

        /** NORMAL / OVERTIME / EARLY / null（计时未完成） */
        public String scenario;

        public long scheduledFeeFen;
        public long lateDeductionFen;
        public long overtimeFeeFen;
        public long actualFeeFen;
        /** 最终应收（争议挂起时仍可计算，但先挂起不收） */
        public long payableFen;
        /** 实际用于计费的工作分钟（客服采信客户主张时可能小于实测） */
        public Long billedMinutes;

        public final List<Line> lines = new ArrayList<>();
    }

    /**
     * 计算账单拆解。
     *
     * @param claimedBilledMinutes 争议处理后客服采信的计费分钟；null 表示按实测
     */
    public static Breakdown calculate(OrderEntity order, List<WorkEventEntity> events,
                                      Long claimedBilledMinutes) {
        Breakdown b = new Breakdown();
        b.scheduledStartMillis = TimeUtil.parseScheduled(order.getScheduledStart());
        b.scheduledMinutes = order.getScheduledMinutes();
        int rate = order.getHourlyRateFen();
        b.scheduledFeeFen = roundUpHours(b.scheduledMinutes) * rate;

        long workerArrival = findClock(events, "ARRIVAL", "WORKER", "REPORTED");
        long customerArrival = findClock(events, "ARRIVAL", "CUSTOMER", "CONFIRMED");
        long workerDeparture = findClock(events, "DEPARTURE", "WORKER", "REPORTED");
        long customerDeparture = findClock(events, "DEPARTURE", "CUSTOMER", "CONFIRMED");

        // 双方时间一致（1 分钟内视为一致）才采信
        b.arrivalMillis = agreeTime(workerArrival, customerArrival);
        b.departureMillis = agreeTime(workerDeparture, customerDeparture);
        b.pauseMinutes = sumPauses(events);
        if (b.arrivalMillis != null) {
            b.arrivalText = TimeUtil.format(b.arrivalMillis);
        }
        if (b.departureMillis != null) {
            b.departureText = TimeUtil.format(b.departureMillis);
            b.crossDay = TimeUtil.crossDay(b.arrivalMillis, b.departureMillis);
            b.dayCount = TimeUtil.dayCount(b.arrivalMillis, b.departureMillis);
        }

        b.lines.add(new Line("约定服务费", b.scheduledFeeFen,
                "约定 " + b.scheduledMinutes + " 分钟，单价 " + fen(rate) + " 元/小时"));

        if (b.arrivalMillis == null) {
            b.payableFen = b.scheduledFeeFen;
            return b;
        }

        // 迟到：以实际到达时间 vs 约定开始时间
        b.lateMinutes = Math.max(0,
                TimeUtil.minutesBetween(b.scheduledStartMillis, b.arrivalMillis));
        long chargeableLate = Math.max(0, b.lateMinutes - LATE_GRACE_MINUTES);
        if (chargeableLate > 0) {
            b.lateDeductionFen = Math.min(b.scheduledFeeFen,
                    minutesToFen(chargeableLate, rate));
            b.lines.add(new Line("迟到扣减", -b.lateDeductionFen,
                    "迟到 " + b.lateMinutes + " 分钟，宽限 " + LATE_GRACE_MINUTES
                            + " 分钟，超出 " + chargeableLate + " 分钟按分钟单价扣减"));
        }

        if (b.departureMillis == null) {
            b.payableFen = b.scheduledFeeFen - b.lateDeductionFen;
            return b;
        }

        long rawWorked = TimeUtil.minutesBetween(b.arrivalMillis, b.departureMillis) - b.pauseMinutes;
        b.workedMinutes = Math.max(0, rawWorked);

        if (b.pauseMinutes > 0) {
            b.lines.add(new Line("中途外出暂停", 0,
                    "外出 " + b.pauseMinutes + " 分钟不计入服务时长"));
        }

        long billed = b.workedMinutes;
        if (claimedBilledMinutes != null) {
            billed = Math.min(billed, claimedBilledMinutes);
            b.lines.add(new Line("客服采信客户主张", 0,
                    "计费工作时长按 " + billed + " 分钟计（客户主张 "
                            + claimedBilledMinutes + " 分钟）"));
        }
        b.billedMinutes = billed;

        long diff = billed - b.scheduledMinutes;

        if (diff > 0) {
            b.scenario = "OVERTIME";
            long overtimeMinutes = billed - b.scheduledMinutes;
            long billedOvertime = roundUp(overtimeMinutes, OVERTIME_STEP_MINUTES)
                    * OVERTIME_STEP_MINUTES;
            b.overtimeFeeFen = minutesToFen(billedOvertime, rate);
            b.lines.add(new Line("临时加活加时", b.overtimeFeeFen,
                    "实际服务 " + billed + " 分钟，加时 " + overtimeMinutes
                            + " 分钟，按 " + OVERTIME_STEP_MINUTES + " 分钟一档向上取整为 "
                            + billedOvertime + " 分钟"));
            b.payableFen = b.scheduledFeeFen - b.lateDeductionFen + b.overtimeFeeFen;
        } else if (diff < -EARLY_TOLERANCE_MINUTES) {
            b.scenario = "EARLY";
            long chargedHours = roundUpHours((int) billed);
            b.actualFeeFen = chargedHours * rate;
            b.payableFen = Math.max(0, b.actualFeeFen - b.lateDeductionFen);
            b.lines.add(new Line("提前结束按实计费", b.actualFeeFen - b.scheduledFeeFen,
                    "实际服务 " + billed + " 分钟，比约定少 " + -diff
                            + " 分钟（超过 " + EARLY_TOLERANCE_MINUTES + " 分钟容差），按 "
                            + chargedHours + " 小时计"));
        } else {
            b.scenario = "NORMAL";
            b.payableFen = b.scheduledFeeFen - b.lateDeductionFen;
            b.lines.add(new Line("按时完成", 0,
                    "实际服务 " + billed + " 分钟，与约定一致，按约定金额结算"));
        }

        return b;
    }

    private static long findClock(List<WorkEventEntity> events, String type,
                                  String actorRole, String action) {
        for (WorkEventEntity e : events) {
            if (e.getEventType().equals(type)
                    && e.getActorRole().equals(actorRole)
                    && e.getAction().equals(action)) {
                return e.getClockAt();
            }
        }
        return 0L;
    }

    /** 双方时间相差不超过 1 分钟才视为一致，采信服务员打卡时间；否则返回 null（挂争议）。 */
    private static Long agreeTime(long workerClock, long customerClock) {
        if (workerClock <= 0 || customerClock <= 0) {
            return null;
        }
        long delta = Math.abs(workerClock - customerClock) / 60000;
        return delta <= 1 ? workerClock : null;
    }

    private static long sumPauses(List<WorkEventEntity> events) {
        long total = 0;
        Long outAt = null;
        for (WorkEventEntity e : events) {
            if (e.getActorRole().equals("WORKER") && e.getEventType().equals("PAUSE_OUT")) {
                outAt = e.getClockAt();
            } else if (outAt != null && e.getActorRole().equals("WORKER")
                    && e.getEventType().equals("RETURN")) {
                total += Math.max(0, TimeUtil.minutesBetween(outAt, e.getClockAt()));
                outAt = null;
            }
        }
        return total;
    }

    /** 争议被客服处理后，用采信方的时间统一双方计时再重新计算。 */
    public static Breakdown calculateAfterResolution(OrderEntity order,
                                                     List<WorkEventEntity> events,
                                                     boolean useWorkerSide,
                                                     Long claimedBilledMinutes) {
        long workerArrival = findClock(events, "ARRIVAL", "WORKER", "REPORTED");
        long customerArrival = findClock(events, "ARRIVAL", "CUSTOMER", "CONFIRMED");
        long workerDeparture = findClock(events, "DEPARTURE", "WORKER", "REPORTED");
        long customerDeparture = findClock(events, "DEPARTURE", "CUSTOMER", "CONFIRMED");

        long arrival = useWorkerSide
                ? (workerArrival > 0 ? workerArrival : customerArrival)
                : (customerArrival > 0 ? customerArrival : workerArrival);
        long departure = useWorkerSide
                ? (workerDeparture > 0 ? workerDeparture : customerDeparture)
                : (customerDeparture > 0 ? customerDeparture : workerDeparture);

        List<WorkEventEntity> shifted = overridePair(events, "ARRIVAL", arrival);
        shifted = overridePair(shifted, "DEPARTURE", departure);
        return calculate(order, shifted, claimedBilledMinutes);
    }

    private static List<WorkEventEntity> overridePair(List<WorkEventEntity> events,
                                                      String type, long clockAt) {
        List<WorkEventEntity> out = new ArrayList<>();
        for (WorkEventEntity e : events) {
            if (e.getEventType().equals(type)) {
                out.add(new WorkEventEntity(e.getOrder(), type, e.getActorRole(), e.getActorName(),
                        e.getAction(), clockAt, e.getRecordedAt(), e.getLocation(), e.getNote()));
            } else {
                out.add(e);
            }
        }
        return out;
    }

    public static long roundUpHours(int minutes) {
        return roundUp(Math.max(1, minutes), 60);
    }

    private static long roundUp(long value, long step) {
        return (value + step - 1) / step;
    }

    private static long minutesToFen(long minutes, int ratePerHour) {
        return Math.round(minutes / 60.0 * ratePerHour);
    }

    private static String fen(long v) {
        return String.format("%.2f", v / 100.0);
    }
}
