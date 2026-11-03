package com.choco.net.xiaoya.lvcc.enums;

/**
 * 换电柜 {@code 0x0057}「故障详情」码（协议换电柜故障详情备注）。
 * <p>
 * 【审核点】与充电桩 {@code 0x0016}/{@link EnumLvccFaultCode} 不是同一套码表，勿混用。
 */
public enum EnumLvccCabinetFault {

    NORMAL(0, "正常"),
    CHARGER(1, "充电机故障"),
    FUSE_BURN(2, "保险丝烧断"),
    CONTACTOR_STICK(3, "接触器粘连"),
    SMOKE(4, "烟雾报警触发"),
    FIRE(5, "灭火报警触发"),
    TEMP(6, "温度异常"),
    BATTERY_COMM(7, "电池通讯中断故障"),
    BATTERY(8, "电池故障"),
    CHARGE_OVER_CURRENT(9, "电池充电过流"),
    DISCHARGE_OVER_CURRENT(10, "电池放电过流"),
    SHORT(11, "电池短路保护"),
    CHARGE_HIGH_TEMP(12, "电池充电高温保护"),
    DISCHARGE_HIGH_TEMP(13, "电池放电高温保护"),
    CHARGE_LOW_TEMP(14, "电池充电低温保护"),
    DISCHARGE_LOW_TEMP(15, "电池放电低温保护");

    private final int code;
    private final String title;

    EnumLvccCabinetFault(int code, String title) {
        this.code = code;
        this.title = title;
    }

    public int getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    /** 【审核点】未枚举到的码（如电芯过充/过放 19~58）仍返回可读描述，避免丢告警。 */
    public static String describe(int code) {
        for (EnumLvccCabinetFault e : values()) {
            if (e.code == code) {
                return e.title;
            }
        }
        if (code >= 19 && code <= 38) {
            return "电芯" + (code - 18) + "#过充电压保护";
        }
        if (code >= 39 && code <= 58) {
            return "电芯" + (code - 38) + "#过放电压保护";
        }
        return "换电柜未知故障:" + code;
    }
}
