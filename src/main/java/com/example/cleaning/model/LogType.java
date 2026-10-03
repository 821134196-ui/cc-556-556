package com.example.cleaning.model;

/** 服务时间记录类型：到达、离开、中途外出、外出返回 */
public enum LogType {
    ARRIVE("到达"),
    LEAVE("离开"),
    OUT("中途外出"),
    BACK("外出返回");

    public final String label;

    LogType(String label) {
        this.label = label;
    }
}
