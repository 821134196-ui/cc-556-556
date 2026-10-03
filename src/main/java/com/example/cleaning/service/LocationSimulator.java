package com.example.cleaning.service;

import com.example.cleaning.entity.Order;
import java.time.LocalDateTime;

/** 定位本地模拟：根据打卡时间生成一条模拟 GPS/地址文本，无需真实定位服务 */
public final class LocationSimulator {

    private LocationSimulator() {}

    public static String describe(Order o, LocalDateTime at) {
        // 模拟在客户住址 120 米范围内打卡
        int drift = Math.abs((at.toLocalDate().toString() + at.getHour() + at.getMinute()).hashCode()) % 120;
        return "模拟定位：客户住址 %d 米范围内（%s 附近，坐标本地生成）".formatted(drift, o.workContent.length() > 8 ? o.workContent.substring(0, 8) : "服务地址");
    }
}
