package com.example.hk.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地模拟支付网关：同步返回成功回执，不依赖外部服务。
 * 扣款与退款都带幂等键，真实网关重复回调时同一键只入账一次。
 */
@ApplicationScoped
public class MockPaymentGateway {

    private final AtomicLong seq = new AtomicLong(1000);

    public String charge(long amountFen) {
        long no = seq.incrementAndGet();
        return "PAY" + no + "C";
    }

    public String refund(long amountFen) {
        long no = seq.incrementAndGet();
        return "RFD" + no + "R";
    }
}
