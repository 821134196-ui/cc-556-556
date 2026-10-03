package com.example.hk.web;

import com.example.hk.model.OrderEntity;
import com.example.hk.model.PaymentEntity;
import com.example.hk.service.OrderService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/**
 * 订单 REST 接口。身份用 viewerId + viewerRole 模拟登录（本地演示，无鉴权框架）。
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrdersResource {

    @Inject
    OrderService service;

    @Inject
    Assembler assembler;

    @POST
    public Dto.OrderDetail create(Requests.CreateOrderRequest req) {
        long now = System.currentTimeMillis();
        OrderEntity o = service.createOrder(now, req.customerId(), req.workerId(),
                req.scheduledStart(), req.scheduledMinutes(), req.tasks(), req.address(),
                req.hourlyRateFen() > 0 ? req.hourlyRateFen() : 5000);
        return assembler.detail(o, "CUSTOMER");
    }

    @GET
    public List<Dto.OrderSummary> list(@QueryParam("viewerId") long viewerId,
                                       @QueryParam("viewerRole") String viewerRole) {
        requireIdentity(viewerId, viewerRole);
        return assembler.summaries(service.listOrders(viewerId, viewerRole));
    }

    @GET
    @Path("/{id}")
    public Dto.OrderDetail get(@PathParam("id") long id,
                               @QueryParam("viewerId") long viewerId,
                               @QueryParam("viewerRole") String viewerRole) {
        requireIdentity(viewerId, viewerRole);
        return assembler.detail(service.getOrderForView(id, viewerId, viewerRole), viewerRole);
    }

    /** 服务员到达/离开上报、客户确认、中途外出/返回。记录只追加。 */
    @POST
    @Path("/{id}/clock")
    public Dto.ActionResult clock(@PathParam("id") long id, Requests.ClockRequest req) {
        requireIdentity(req.viewerId(), req.viewerRole());
        OrderEntity o = service.clock(System.currentTimeMillis(), id, req.viewerId(),
                req.viewerRole(), req.eventType(), req.clockAt(), req.location(), req.note());
        return new Dto.ActionResult(true, "记录已提交（不可修改）", false,
                assembler.detail(o, req.viewerRole()));
    }

    /** 客户确认任务完成——客服/服务员调用返回 403。 */
    @POST
    @Path("/{id}/confirm-completion")
    public Dto.ActionResult confirm(@PathParam("id") long id, Requests.ActorRequest req) {
        requireIdentity(req.viewerId(), req.viewerRole());
        OrderEntity o = service.confirmCompletion(System.currentTimeMillis(), id,
                req.viewerId(), req.viewerRole());
        return new Dto.ActionResult(true, "客户已确认任务完成", false,
                assembler.detail(o, req.viewerRole()));
    }

    /** 客户/服务员发起争议（客户拒签、中途外出纠纷、完成情况异议等）。 */
    @POST
    @Path("/{id}/disputes")
    public Dto.ActionResult raiseDispute(@PathParam("id") long id, Requests.DisputeRequest req) {
        requireIdentity(req.viewerId(), req.viewerRole());
        OrderEntity o = service.raiseDispute(System.currentTimeMillis(), id, req.viewerId(),
                req.viewerRole(), req.reason(), req.claimedBilledMinutes());
        return new Dto.ActionResult(true, "争议已挂起，争议金额冻结，等待客服处理", false,
                assembler.detail(o, req.viewerRole()));
    }

    /** 客服处理争议：APPROVED 采信服务员 / DENY 采信客户主张。 */
    @POST
    @Path("/{id}/resolve")
    public Dto.ActionResult resolve(@PathParam("id") long id, Requests.ResolveRequest req) {
        OrderEntity o = service.resolveDispute(System.currentTimeMillis(), id, req.staffId(),
                req.staffRole(), req.resolution(), req.note(), req.claimedBilledMinutes());
        return new Dto.ActionResult(true,
                "APPROVED".equals(req.resolution()) ? "已采信服务员记录" : "已采信客户主张",
                false, assembler.detail(o, "STAFF"));
    }

    /** 客户确认结算扣款；重复提交同一幂等键不二次扣款。 */
    @POST
    @Path("/{id}/settle")
    public Dto.ActionResult settle(@PathParam("id") long id, Requests.SettleRequest req) {
        requireIdentity(req.viewerId(), req.viewerRole());
        PaymentEntity p = service.settle(System.currentTimeMillis(), id, req.viewerId(),
                req.viewerRole(), req.idempotencyKey());
        OrderEntity o = service.getOrderForView(id, req.viewerId(), req.viewerRole());
        boolean replay = "SETTLED".equals(o.getStatus());
        return new Dto.ActionResult(true,
                replay ? "该订单已结算，重复提交未再次扣款（幂等）" : "扣款成功，订单已结算",
                replay, assembler.detail(o, req.viewerRole()));
    }

    /**
     * 模拟支付网关回执回传（扣款/退款）。
     * 同一 idempotencyKey 重复回传只入账一次，退款不会多退。
     */
    @POST
    @Path("/{id}/payments/callback")
    public Dto.ActionResult callback(@PathParam("id") long id, Requests.CallbackRequest req) {
        if (req.idempotencyKey() == null || req.idempotencyKey().isBlank()) {
            throw new com.example.hk.service.ApiException(400, "idempotencyKey 必填");
        }
        PaymentEntity before = service.findPaymentForCheck(id, req.idempotencyKey());
        PaymentEntity p = service.paymentCallback(System.currentTimeMillis(), id, req.type(),
                req.idempotencyKey(), req.amountFen(), req.receiptNo(), req.status());
        OrderEntity o = service.getOrderById(id);
        boolean replay = before != null;
        String msg = replay
                ? "回执重复回传，幂等键 " + req.idempotencyKey() + " 已存在，未重复入账"
                : (req.type().equals("REFUND") ? "退款回执已入账" : "扣款回执已入账");
        return new Dto.ActionResult(true, msg, replay, assembler.detail(o, "STAFF"));
    }

    private void requireIdentity(long viewerId, String viewerRole) {
        if (viewerId <= 0 || viewerRole == null
                || !List.of("CUSTOMER", "WORKER", "STAFF").contains(viewerRole)) {
            throw new com.example.hk.service.ApiException(400,
                    "需要 viewerId 和 viewerRole（CUSTOMER/WORKER/STAFF）");
        }
    }

    /** 给健康检查/文档用的简单探针。 */
    @GET
    @Path("/_meta")
    public Map<String, Object> meta() {
        return Map.of(
                "lateGraceMinutes", com.example.hk.service.BillingService.LATE_GRACE_MINUTES,
                "earlyToleranceMinutes",
                com.example.hk.service.BillingService.EARLY_TOLERANCE_MINUTES,
                "overtimeStepMinutes",
                com.example.hk.service.BillingService.OVERTIME_STEP_MINUTES);
    }
}
