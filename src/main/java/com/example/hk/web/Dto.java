package com.example.hk.web;

import com.example.hk.service.BillingService;

import java.util.List;

/** 出参 DTO（嵌套 record 定义，避免实体直接序列化导致懒加载问题）。 */
public final class Dto {

    private Dto() {
    }

    public record UserDto(long id, String name, String role, String phone) {
    }

    public record EventDto(long id, String eventType, String actorRole, String actorName,
                           String action, long clockAt, String clockAtText,
                           long recordedAt, String recordedAtText,
                           String location, String note) {
    }

    public record DisputeDto(long id, String reason, String raisedByRole, String raisedByName,
                             long raisedAt, String raisedAtText, String status,
                             String resolution, String resolutionNote,
                             String resolvedByName, Long resolvedAt, String resolvedAtText,
                             Long claimedBilledMinutes) {
    }

    public record PaymentDto(long id, String type, String idempotencyKey, long amountFen,
                             String status, String receiptNo, long createdAt, String createdAtText,
                             Long succeededAt, String succeededAtText, String note) {
    }

    public record TimeConflict(String point, Long workerClock, String workerClockText,
                               Long customerClock, String customerClockText, long diffMinutes) {
    }

    public record OrderSummary(long id, String status, String customerName, String workerName,
                               String scheduledStart, int scheduledMinutes, String tasks,
                               boolean disputed) {
    }

    public record OrderDetail(long id, String status, String statusText,
                              long customerId, String customerName,
                              long workerId, String workerName,
                              String scheduledStart, int scheduledMinutes,
                              String tasks, int hourlyRateFen,
                              boolean disputed, boolean frozen,
                              String disputeReason, String disputeResolution,
                              List<EventDto> events,
                              List<DisputeDto> disputes,
                              List<PaymentDto> payments,
                              BillingService.Breakdown breakdown,
                              BillingService.Breakdown workerSideBreakdown,
                              BillingService.Breakdown customerSideBreakdown,
                              List<TimeConflict> conflicts,
                              Long chargedFen, Long refundedFen, Long netPaidFen,
                              String finalizedAtText, String finalizerName,
                              String viewerRole) {
    }

    public record ActionResult(boolean ok, String message, boolean idempotentReplay,
                               OrderDetail order) {
    }
}
