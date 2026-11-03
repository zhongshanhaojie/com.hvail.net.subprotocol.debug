package com.choco.net.xiaoya.lvcc.enums;

import java.util.Arrays;

/**
 * @author hvail
 * @version 1.0.0
 * @file EnumCallCommand.java
 * @date 2022/4/15 16:03
 * @note
 */
public enum EnumCallCommand {

    HEART_KEEP(0x0002, "心跳"),
    REGISTER(0x0001, "注册"),
    START_CHARGE(0x0005, "开启充电"),
    QUERY_CHARGE_STATUS(0x0006, "查询充电状态"),
    STOP_CHARGE(0x0007, "停止充电"),
    CARD_QUERY_CHARGE_TIME(0x0008, "刷卡查询充电时间"),
    UPLOAD_CARD_ORDER(0x0009, "上报刷卡订单"),
    END_CHARGE(0x0010, "充电结束"),
    QUERY_DEVICE_INFO(0x0011, "查询设备信息"),
    QUERY_DEVICE_ARGS_INFO(0x0013, "查询设备配置信息"),
    SET_DEVICE_ARGS_INFO(0x0014, "设置设备配置信息"),
    SET_DEVICE_VOLUME(0x0027, "设置喇叭音量"),
    QUERY_DEVICE_VOLUME(0x0028, "查询喇叭音量"),
    /** 【审核点-告警】充电桩/通用故障上报（烟感/温度/漏电/保险丝等），主动上报 0x12 */
    FAULT_UPLOAD(0x0016, "故障上报"),
    DEVICE_REBOOT(0x0017, "设备重启"),
    QUERY_DEVICE_PORT_STATUS(0x0019, "查询设备插座状态"),
    PORT_STATUS_CHANGE_UP(0x0021, "插座状态更改上报"),
    DEVICE_CHARGE_STATUS_UP(0x0030, "充电状态定时上报"),
    /** 【审核点-告警】网关下发复位设备故障；设备以 0x11 回结果 */
    FAULT_RESET(0x0038, "复位设备故障"),
    REAL_CHARGE_STATUS_timing_up(0x0048, "实时充电状态定时上报"),
    /**
     * 【审核点-告警】换电柜设备状态主动上报（仓位故障详情在属性块内）。
     * 与 0x0016 码表不同，见 {@link EnumLvccCabinetFault}。
     */
    CABINET_STATUS_UP(0x0057, "换电柜状态主动上报"),

    NULL(0xFF, "未定义类型");

    private int code;
    private String title;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    EnumCallCommand(int code, String description) {
        this.code = code;
        this.title = description + " - " + this.code;
    }

    public static EnumCallCommand getEnumByIndex(int index) {
        return Arrays.stream(values()).filter(e -> e.code == index).findAny().orElse(EnumCallCommand.NULL);
    }

}
