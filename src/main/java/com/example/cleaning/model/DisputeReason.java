package com.example.cleaning.model;

/** 争议原因 */
public enum DisputeReason {
    TIME_CONFLICT("到达/离开时间双方说法不一致"),
    LEFT_MIDWAY("服务员中途离岗"),
    REJECT_SIGN("客户拒签确认完成"),
    WORK_QUALITY("完成情况/质量异议"),
    EXTRA_WORK("临时加活费用异议");

    public final String label;

    DisputeReason(String label) {
        this.label = label;
    }
}
