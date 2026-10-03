package com.example.cleaning.model;

public enum Role {
    CUSTOMER("客户"),
    CLEANER("钟点工"),
    CUSTOMER_SERVICE("客服");

    public final String label;

    Role(String label) {
        this.label = label;
    }
}
