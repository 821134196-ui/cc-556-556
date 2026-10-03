package com.example.cleaning.web;

import com.example.cleaning.entity.User;
import com.example.cleaning.model.Role;
import com.example.cleaning.service.OrderService;
import com.example.cleaning.web.dto.OrderView;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderService service;

    @HeaderParam("X-Actor-Id") Long actorId;

    @GET
    public List<OrderView> list() {
        return service.list(Actors.current(actorId));
    }

    @GET
    @Path("/{id}")
    public OrderView get(@PathParam("id") Long id) {
        return service.getView(id, Actors.current(actorId));
    }

    public record CreateOrderRequest(String cleanerName, String workContent,
                                     String scheduledStart, String scheduledEnd,
                                     Long rateCents, Integer lateGraceMinutes) {}

    @POST
    public Map<String, Object> create(CreateOrderRequest req) {
        User customer = Actors.requireRole(actorId, Role.CUSTOMER);
        var o = service.create(customer, req.cleanerName(), req.workContent(),
                LocalDateTime.parse(req.scheduledStart().replace(' ', 'T')),
                LocalDateTime.parse(req.scheduledEnd().replace(' ', 'T')),
                req.rateCents() == null ? 5000 : req.rateCents(),
                req.lateGraceMinutes() == null ? 10 : req.lateGraceMinutes());
        return Map.of("id", o.id, "orderNo", o.orderNo);
    }

    public record ClockRequest(String type, String at, String note) {}

    /** 钟点工到达/离开打卡 */
    @POST
    @Path("/{id}/clock")
    public Map<String, Object> clock(@PathParam("id") Long id, ClockRequest req) {
        User cleaner = Actors.requireRole(actorId, Role.CLEANER);
        var type = com.example.cleaning.model.LogType.valueOf(req.type());
        var at = LocalDateTime.parse(req.at().replace(' ', 'T'));
        var log = service.clock(id, cleaner, type, at, req.note());
        return Map.of("id", log.id, "status", "ok");
    }

    /** 登记中途外出/返回（双方均可） */
    @POST
    @Path("/{id}/midway")
    public Map<String, Object> midway(@PathParam("id") Long id, ClockRequest req) {
        User viewer = Actors.current(actorId);
        var type = com.example.cleaning.model.LogType.valueOf(req.type());
        var at = LocalDateTime.parse(req.at().replace(' ', 'T'));
        var log = service.reportMidway(id, viewer, type, at, req.note());
        return Map.of("id", log.id, "status", "ok");
    }

    public record ConfirmLogRequest(Boolean accept, String note) {}

    /** 客户/钟点工确认或否认对方的时间记录（否认即产生确认冲突并挂起） */
    @POST
    @Path("/{id}/logs/{logId}/confirm")
    public Map<String, Object> confirmLog(@PathParam("id") Long id, @PathParam("logId") Long logId,
                                          ConfirmLogRequest req) {
        User viewer = Actors.current(actorId);
        service.confirmLog(id, logId, viewer, req.accept() == null || req.accept(), req.note());
        return Map.of("status", "ok");
    }

    /** 客户确认任务完成 / 拒签（客服不能代确认） */
    @POST
    @Path("/{id}/complete")
    public Map<String, Object> complete(@PathParam("id") Long id, ConfirmLogRequest req) {
        User customer = Actors.requireRole(actorId, Role.CUSTOMER);
        service.confirmComplete(id, customer, req.accept() == null || req.accept());
        return Map.of("status", "ok");
    }

    public record DisputeRequest(String reason, String description, Long heldCents) {}

    @POST
    @Path("/{id}/disputes")
    public Map<String, Object> dispute(@PathParam("id") Long id, DisputeRequest req) {
        User viewer = Actors.current(actorId);
        var reason = com.example.cleaning.model.DisputeReason.valueOf(req.reason());
        var d = service.raiseDispute(id, viewer, reason, req.description(), req.heldCents());
        return Map.of("id", d.id, "heldCents", d.heldCents, "status", "ok");
    }

    public record ResolveRequest(String adoptedArriveAt, String adoptedLeaveAt,
                                 Boolean serviceAccepted, Integer deductMinutes,
                                 Long approvedExtraCents, Long releaseToCleanerCents,
                                 Long refundToCustomerCents, String resolution) {}

    /** 客服裁定争议 */
    @POST
    @Path("/disputes/{disputeId}/resolve")
    public Map<String, Object> resolve(@PathParam("disputeId") Long disputeId, ResolveRequest req) {
        User cs = Actors.requireRole(actorId, Role.CUSTOMER_SERVICE);
        service.resolveDispute(disputeId, cs,
                parse(req.adoptedArriveAt()), parse(req.adoptedLeaveAt()),
                req.serviceAccepted(), req.deductMinutes(),
                req.approvedExtraCents(), req.releaseToCleanerCents(), req.refundToCustomerCents(),
                req.resolution());
        return Map.of("status", "ok");
    }

    public record SettleRequest(String idempotencyKey) {}

    /** 客户结算确认付款；相同/重复提交不二次扣款 */
    @POST
    @Path("/{id}/settle")
    public OrderView settle(@PathParam("id") Long id, SettleRequest req) {
        User customer = Actors.requireRole(actorId, Role.CUSTOMER);
        return service.settle(id, customer, req == null ? null : req.idempotencyKey());
    }

    public record RefundRequest(Long cents, String reason) {}

    @POST
    @Path("/{id}/refunds")
    public Map<String, Object> refund(@PathParam("id") Long id, RefundRequest req) {
        User customer = Actors.requireRole(actorId, Role.CUSTOMER);
        var r = service.requestRefund(id, customer, req.cents(), req.reason());
        return Map.of("id", r.id, "refundCents", r.refundCents, "status", "ok");
    }

    @POST
    @Path("/refunds/{refundId}/approve")
    public Map<String, Object> approveRefund(@PathParam("refundId") Long refundId) {
        User cs = Actors.requireRole(actorId, Role.CUSTOMER_SERVICE);
        var r = service.approveRefund(refundId, cs);
        return Map.of("id", r.id, "status", r.status.name(), "paymentCallbackId",
                r.paymentCallbackId == null ? "" : r.paymentCallbackId);
    }

    public record CallbackRequest(String paymentCallbackId, String result) {}

    /** 支付渠道退款回执回调；同一回执号重复回传幂等，不多退 */
    @POST
    @Path("/refunds/callback")
    public Map<String, Object> callback(CallbackRequest req) {
        var r = service.refundCallback(req.paymentCallbackId(), req.result() == null ? "SUCCESS" : req.result());
        return Map.of("refundId", r.id, "status", r.status.name(),
                "refundCents", r.refundCents, "refundTxnNo", r.refundTxnNo == null ? "" : r.refundTxnNo);
    }

    private static LocalDateTime parse(String s) {
        return s == null || s.isBlank() ? null : LocalDateTime.parse(s.replace(' ', 'T'));
    }
}
