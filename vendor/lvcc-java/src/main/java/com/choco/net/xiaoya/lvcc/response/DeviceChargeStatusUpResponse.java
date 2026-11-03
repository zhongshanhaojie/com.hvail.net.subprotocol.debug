package com.choco.net.xiaoya.lvcc.response;

/**
 * @Author: tanning
 * @Date: 2024/06/14/18:02
 * @Description: 设备充电状态上报
 */
public final class DeviceChargeStatusUpResponse {

    private DeviceChargeStatusUpResponse() {
    }

    public static byte[] buildContent() {
        return new byte[]{1};
    }
}
