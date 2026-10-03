package com.example.cleaning.model;

/**
 * 一条时间记录的状态：
 * PENDING     本方已提交，等待对方确认
 * CONFIRMED   对方确认，claimedAt 直接作为计费时间
 * REJECTED    对方否认（可能伴随对方的反提记录与争议）
 * ADJUDICATED 客服裁定，adoptedAt 为最终计费时间（任何一方都不能改）
 */
public enum LogStatus {
    PENDING, CONFIRMED, REJECTED, ADJUDICATED
}
