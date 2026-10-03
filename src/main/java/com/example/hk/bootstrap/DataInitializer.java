package com.example.hk.bootstrap;

import com.example.hk.model.*;
import com.example.hk.service.TimeUtil;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * 启动演示数据：
 * 订单 1 跨日计时（22:00 → 次日 02:40，加时）
 * 订单 2 迟到（迟到 20 分钟，客户待确认完成）
 * 订单 3 双方确认冲突（离开时间各执一词，自动挂起争议）
 * 订单 4 客户拒签→客服采信客户主张→已结算（供退款重复回传演示）
 * 订单 5 新建未开始（可手动走全流程）
 */
@ApplicationScoped
public class DataInitializer {

    @Inject
    EntityManager em;

    @Transactional
    void seed(@Observes StartupEvent ev) {
        Long count = em.createQuery("select count(u) from UserEntity u", Long.class)
                .getSingleResult();
        if (count > 0) {
            return;
        }

        UserEntity c1 = new UserEntity("王敏（客户）", "CUSTOMER", "13800000001");
        UserEntity c2 = new UserEntity("李强（客户）", "CUSTOMER", "13800000002");
        UserEntity c3 = new UserEntity("陈芳（客户）", "CUSTOMER", "13800000003");
        UserEntity w1 = new UserEntity("赵姐（钟点工）", "WORKER", "13900000001");
        UserEntity w2 = new UserEntity("孙姐（钟点工）", "WORKER", "13900000002");
        UserEntity s1 = new UserEntity("客服小周", "STAFF", "13700000001");
        for (UserEntity u : new UserEntity[]{c1, c2, c3, w1, w2, s1}) {
            em.persist(u);
        }
        em.flush();

        long seed = TimeUtil.parseScheduled("2026-10-03 08:00");

        // ---------- 订单 1：跨日 + 加时 ----------
        OrderEntity o1 = new OrderEntity(seed, c1.getId(), w1.getId(),
                "2026-10-08 22:00", 240, "深度保洁：厨房、卫生间、客厅",
                "上海市浦东新区张杨路 500 号", 5000);
        em.persist(o1);
        long a1 = TimeUtil.parseScheduled("2026-10-08 22:00");
        long d1 = TimeUtil.parseScheduled("2026-10-09 02:40"); // 跨日，280 分钟
        addEvent(o1, "ARRIVAL", "WORKER", w1.getName(), a1, seed, "到达客户家门口（模拟定位）", null);
        addEvent(o1, "ARRIVAL", "CUSTOMER", c1.getName(), a1, seed, "已开门确认", null);
        addEvent(o1, "PAUSE_OUT", "WORKER", w1.getName(),
                TimeUtil.parseScheduled("2026-10-08 23:30"), seed, "下楼取清洁工具（模拟）", null);
        addEvent(o1, "RETURN", "WORKER", w1.getName(),
                TimeUtil.parseScheduled("2026-10-09 00:00"), seed, "已返回（模拟定位）", null);
        addEvent(o1, "DEPARTURE", "WORKER", w1.getName(), d1, seed, "完工离开（模拟定位）",
                "客户临时要求加擦两扇落地窗");
        addEvent(o1, "DEPARTURE", "CUSTOMER", c1.getName(), d1, seed, "确认离开时间", null);
        o1.setWorkerArrivalStatus("REPORTED");
        o1.setCustomerArrivalStatus("CONFIRMED");
        o1.setWorkerDepartureStatus("REPORTED");
        o1.setCustomerDepartureStatus("CONFIRMED");
        o1.setStatus("COMPLETED");
        o1.setFinalizedAt(d1);
        o1.setFinalizerName(c1.getName());

        // ---------- 订单 2：迟到 20 分钟 ----------
        OrderEntity o2 = new OrderEntity(seed, c2.getId(), w2.getId(),
                "2026-10-09 09:00", 120, "日常保洁 2 小时",
                "上海市徐汇区漕溪北路 100 号", 5000);
        em.persist(o2);
        long a2 = TimeUtil.parseScheduled("2026-10-09 09:20");
        long d2 = TimeUtil.parseScheduled("2026-10-09 11:20");
        addEvent(o2, "ARRIVAL", "WORKER", w2.getName(), a2, seed, "到楼下（模拟定位）",
                "路上堵车，已提前电话告知");
        addEvent(o2, "ARRIVAL", "CUSTOMER", c2.getName(), a2, seed, "确认 09:20 进门", null);
        addEvent(o2, "DEPARTURE", "WORKER", w2.getName(), d2, seed, "完工离开（模拟定位）", null);
        addEvent(o2, "DEPARTURE", "CUSTOMER", c2.getName(), d2, seed, "确认离开时间", null);
        o2.setWorkerArrivalStatus("REPORTED");
        o2.setCustomerArrivalStatus("CONFIRMED");
        o2.setWorkerDepartureStatus("REPORTED");
        o2.setCustomerDepartureStatus("CONFIRMED");
        o2.setStatus("IN_PROGRESS"); // 等客户确认完成

        // ---------- 订单 3：双方确认冲突 → 自动挂起 ----------
        OrderEntity o3 = new OrderEntity(seed, c3.getId(), w1.getId(),
                "2026-10-10 13:00", 180, "搬家后开荒保洁 3 小时",
                "上海市闵行区莘庄镇都市路 88 号", 5500);
        em.persist(o3);
        addEvent(o3, "ARRIVAL", "WORKER", w1.getName(),
                TimeUtil.parseScheduled("2026-10-10 13:00"), seed, "13:00 到达（模拟定位）", null);
        addEvent(o3, "ARRIVAL", "CUSTOMER", c3.getName(),
                TimeUtil.parseScheduled("2026-10-10 13:00"), seed, "确认 13:00 到达", null);
        addEvent(o3, "DEPARTURE", "WORKER", w1.getName(),
                TimeUtil.parseScheduled("2026-10-10 17:00"), seed, "17:00 才走（模拟定位）",
                "客户中途加活，实际干到 17 点");
        addEvent(o3, "DEPARTURE", "CUSTOMER", c3.getName(),
                TimeUtil.parseScheduled("2026-10-10 16:00"), seed, "16:00 就离开了",
                "只干了 3 小时，没有加时");
        o3.setWorkerArrivalStatus("REPORTED");
        o3.setCustomerArrivalStatus("CONFIRMED");
        o3.setWorkerDepartureStatus("REPORTED");
        o3.setCustomerDepartureStatus("CONFIRMED");
        DisputeEntity d3 = new DisputeEntity(o3,
                "离开时间双方记录相差 60 分钟，系统自动挂起，等待客服核对记录",
                "SYSTEM", "系统", seed);
        d3.setFromStatus("IN_PROGRESS");
        em.persist(d3);
        o3.getDisputes().add(d3);
        o3.setDisputed(true);
        o3.setDisputeReason(d3.getReason());
        o3.setDisputeRaisedAt(seed);
        o3.setStatus("DISPUTED");

        // ---------- 订单 4：客户拒签 → 客服 DENY → 已结算（退款重复回传演示对象） ----------
        OrderEntity o4 = new OrderEntity(seed, c1.getId(), w2.getId(),
                "2026-10-02 08:00", 120, "厨房深度除油 + 日常保洁",
                "上海市浦东新区张杨路 500 号", 5000);
        em.persist(o4);
        long a4 = TimeUtil.parseScheduled("2026-10-02 08:00");
        long d4 = TimeUtil.parseScheduled("2026-10-02 10:00");
        addEvent(o4, "ARRIVAL", "WORKER", w2.getName(), a4, seed, "到达（模拟定位）", null);
        addEvent(o4, "ARRIVAL", "CUSTOMER", c1.getName(), a4, seed, "确认到达", null);
        addEvent(o4, "DEPARTURE", "WORKER", w2.getName(), d4, seed, "完工离开（模拟定位）", null);
        addEvent(o4, "DEPARTURE", "CUSTOMER", c1.getName(), d4, seed, "时间确认，但拒签完成", null);
        o4.setWorkerArrivalStatus("REPORTED");
        o4.setCustomerArrivalStatus("CONFIRMED");
        o4.setWorkerDepartureStatus("REPORTED");
        o4.setCustomerDepartureStatus("CONFIRMED");
        DisputeEntity disp4 = new DisputeEntity(o4,
                "厨房油烟机油污未清干净，客户拒绝签字，主张只按 90 分钟计费",
                "CUSTOMER", c1.getName(), seed);
        disp4.setFromStatus("IN_PROGRESS");
        disp4.setStatus("RESOLVED");
        disp4.setResolution("DENY");
        disp4.setClaimedBilledMinutes(90L);
        disp4.setResolutionNote("核对双方照片与定位记录，采信客户主张：按 90 分钟（不足 2 小时按 2 小时档）计费");
        disp4.setResolvedByName(s1.getName());
        disp4.setResolvedAt(seed);
        em.persist(disp4);
        o4.getDisputes().add(disp4);
        o4.setDisputeResolution("DENY");
        o4.setDisputeResolverName(s1.getName());
        o4.setDisputeResolvedAt(seed);
        o4.setStatus("COMPLETED");
        o4.setFinalizedAt(seed);
        o4.setFinalizerName(c1.getName());
        PaymentEntity pay4 = new PaymentEntity(o4, "CHARGE", "demo-charge-4",
                10000, seed, "结算扣款：客服采信客户主张后按 2 小时计（迟到 0 分钟）");
        pay4.setReceiptNo("PAY1001C");
        pay4.setStatus("SUCCEEDED");
        pay4.setSucceededAt(seed);
        em.persist(pay4);
        o4.getPayments().add(pay4);
        o4.setStatus("SETTLED");

        // ---------- 订单 5：全新预约 ----------
        OrderEntity o5 = new OrderEntity(seed, c2.getId(), w1.getId(),
                "2026-10-12 10:00", 90, "玻璃清洁 + 地面清洁",
                "上海市徐汇区漕溪北路 100 号", 5000);
        em.persist(o5);

        em.flush();
    }

    private void addEvent(OrderEntity o, String type, String actorRole, String actorName,
                          long clockAt, long recordedAt, String location, String note) {
        WorkEventEntity e = new WorkEventEntity(o, type, actorRole, actorName,
                actorRole.equals("WORKER") ? "REPORTED" : "CONFIRMED",
                clockAt, recordedAt, location, note);
        em.persist(e);
        o.getEvents().add(e);
    }
}
