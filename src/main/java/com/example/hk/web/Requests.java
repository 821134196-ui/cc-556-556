package com.example.hk.web;

/** REST 公共请求结构。 */
public final class Requests {

    private Requests() {
    }

    public record CreateOrderRequest(long customerId, long workerId, String scheduledStart,
                                     int scheduledMinutes, String tasks, String address,
                                     int hourlyRateFen) {
    }

    public record ClockRequest(long viewerId, String viewerRole, String eventType,
                               Long clockAt, String location, String note) {
    }

    public record ActorRequest(long viewerId, String viewerRole) {
    }

    public record DisputeRequest(long viewerId, String viewerRole, String reason,
                                 Long claimedBilledMinutes) {
    }

    public record ResolveRequest(long staffId, String staffRole, String resolution,
                                 String note, Long claimedBilledMinutes) {
    }

    public record SettleRequest(long viewerId, String viewerRole, String idempotencyKey) {
    }

    public record CallbackRequest(String type, String idempotencyKey, Long amountFen,
                                  String receiptNo, String status) {
    }
}
