package com.example.cleaning.model;

public enum RefundStatus {
    PENDING("待发起退款"),
    PROCESSING("退款已发起，等待支付回执"),
    PAID("退款已到账"),
    FAILED("退款失败");

    public final String label;

    RefundStatus(String label) {
        this.label = label;
    }
}
