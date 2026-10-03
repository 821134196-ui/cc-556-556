package com.example.cleaning.model;

public enum DisputeStatus {
    OPEN("挂起中"), RESOLVED("客服已处理");

    public final String label;

    DisputeStatus(String label) {
        this.label = label;
    }
}
