package com.example.cleaning.service;

import com.example.cleaning.entity.Refund;
import com.example.cleaning.entity.Settlement;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地模拟支付网关：生成流水号、即时返回成功。
 * 真实环境里扣款/退款是异步回调；这里保留"发起 → 回执回调"两步，
 * 以演示结算重复提交与退款回执重复回传的幂等处理。
 */
@ApplicationScoped
public class PaymentGateway {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicInteger seq = new AtomicInteger(1000);

    public String nextPaymentTxnNo() {
        return "PAY-" + LocalDateTime.now().format(FMT) + "-" + seq.incrementAndGet();
    }

    public String nextRefundTxnNo() {
        return "REF-" + LocalDateTime.now().format(FMT) + "-" + seq.incrementAndGet();
    }

    /** 模拟即时扣款 */
    public void charge(Settlement s) {
        s.paymentTxnNo = nextPaymentTxnNo();
        s.paidCents = s.totalCents;
        s.paidAt = LocalDateTime.now();
    }

    /** 模拟退款出款 */
    public void payout(Refund r) {
        r.refundTxnNo = nextRefundTxnNo();
        r.processedAt = LocalDateTime.now();
    }
}
