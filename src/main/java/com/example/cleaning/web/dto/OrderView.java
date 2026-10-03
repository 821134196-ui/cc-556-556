package com.example.cleaning.web.dto;

import java.util.List;

/** 订单详情聚合视图：约定/实际时长、费用明细、时间记录、争议、结算、退款、当前身份权限 */
public record OrderView(
        Long id, String orderNo, String status, String statusLabel,
        Person customer, Person cleaner,
        String workContent,
        String scheduledStart, String scheduledEnd, long scheduledMinutes,
        long rateCents, int lateGraceMinutes,
        boolean customerConfirmedComplete,
        BillView bill,
        List<LogView> logs,
        List<DisputeView> disputes,
        SettlementView settlement,
        List<RefundView> refunds,
        Permissions perms
) {
    public record Person(Long id, String name, String roleLabel) {}

    public record BillView(
            String arriveAt, String leaveAt,
            long scheduledMinutes, long actualMinutes, long outMinutes, long deductMinutes,
            long lateMinutes, long earlyMinutes, long overtimeMinutes,
            long baseCents, long lateDeductCents, long earlyDeductCents,
            long outDeductCents, long qualityDeductCents, long overtimeCents, long approvedExtraCents,
            long heldCents, long payableCents,
            List<Item> items
    ) {
        public record Item(String label, long cents, String kind) {}
    }

    public record LogView(
            Long id, String reporter, String reporterRole, String type, String typeLabel,
            String claimedAt, String adoptedAt, String effectiveAt, String location,
            String status, String statusLabel, Long conflictWithId, String note,
            boolean canConfirm
    ) {}

    public record DisputeView(
            Long id, String raisedBy, String raisedByRole, String reason, String reasonLabel,
            String description, String status, String statusLabel, long heldCents,
            String adoptedArriveAt, String adoptedLeaveAt, Boolean serviceAccepted,
            Integer deductMinutes, Long approvedExtraCents,
            Long releaseToCleanerCents, Long refundToCustomerCents,
            String resolution, String resolvedBy, String createdAt, String resolvedAt
    ) {}

    public record SettlementView(
            Long id, String idempotencyKey,
            long baseCents, long lateDeductCents, long earlyDeductCents, long outDeductCents,
            long extraCents, long qualityDeductCents, long totalCents,
            String paymentTxnNo, Long paidCents, String paidAt, boolean idempotentReplay
    ) {}

    public record RefundView(
            Long id, long requestCents, long refundCents, String status, String statusLabel,
            String paymentCallbackId, String refundTxnNo, String reason,
            String createdAt, String processedAt, boolean idempotentReplay
    ) {}

    public record Permissions(
            String role, boolean party,
            boolean canClock, boolean canConfirmLogs, boolean canRaiseDispute,
            boolean canResolveDisputes, boolean canConfirmComplete,
            boolean canSettle, boolean canRequestRefund,
            boolean frozenByDispute, String freezeReason
    ) {}
}
