package com.choco.net.xiaoya.lvcc.enums;

/**
 * 驴充充 {@code 0x0016} 故障码语义。
 * <p>
 * 【审核点】同一数字在「整机」与「通道」下含义不同，必须结合插座字段判断：
 * 插座={@code 0xFF} → 整机故障码；否则 → 通道故障码。
 * 协议见 Lvcc-Protocol.md「13.故障上报」。
 */
public enum EnumLvccFaultCode {

    // --- 整机故障码（插座=0xFF）---
    DEVICE_FUSE_BLOW(1, true, "保险丝熔断(早期设备)"),
    DEVICE_RTC_ERROR(2, true, "RTC错误"),
    DEVICE_STORAGE_ERROR(3, true, "储存器错误"),
    // 协议表整机码无 4；温度报警为 14。保留位避免误映射
    DEVICE_SMOKE(5, true, "烟感报警(整机)"),
    DEVICE_METER_IC(6, true, "计量IC故障"),
    DEVICE_FUSE_DETECT(7, true, "保险丝检测电路故障"),
    DEVICE_CARD_BOARD(8, true, "刷卡板故障"),
    DEVICE_OVER_VOLTAGE(9, true, "电压过压"),
    DEVICE_CHARGER(10, true, "充电机故障"),
    DEVICE_FIRE_ALARM(11, true, "灭火报警触发"),
    DEVICE_LEAKAGE(12, true, "漏电故障"),
    DEVICE_WATER(13, true, "水浸报警"),
    DEVICE_TEMP(14, true, "温度报警"),

    // --- 通道故障码（插座=具体端口）---
    PORT_RELAY_STICK(1, false, "继电器粘连"),
    PORT_FUSE_BURN(2, false, "保险丝烧断"),
    PORT_FUSE_SHORT(3, false, "保险丝短路"),
    PORT_FIRE_EXT(4, false, "灭火器触发"),
    PORT_SMOKE(5, false, "烟感报警(单通道)"),
    PORT_CHARGER(10, false, "充电机故障"),
    PORT_FIRE_ALARM(11, false, "灭火报警触发"),
    PORT_TEMP(14, false, "温度报警");

    private final int code;
    /** true=整机码表，false=通道码表 */
    private final boolean deviceScope;
    private final String title;

    EnumLvccFaultCode(int code, boolean deviceScope, String title) {
        this.code = code;
        this.deviceScope = deviceScope;
        this.title = title;
    }

    public int getCode() {
        return code;
    }

    public boolean isDeviceScope() {
        return deviceScope;
    }

    public String getTitle() {
        return title;
    }

    /**
     * 【审核点】按作用域解析故障码文案；未知码返回「未知故障码:N」。
     *
     * @param code         协议故障码
     * @param wholeDevice  是否整机（插座==0xFF）
     */
    public static String describe(int code, boolean wholeDevice) {
        for (EnumLvccFaultCode e : values()) {
            if (e.code == code && e.deviceScope == wholeDevice) {
                return e.title;
            }
        }
        return "未知故障码:" + code + (wholeDevice ? "(整机)" : "(通道)");
    }
}
