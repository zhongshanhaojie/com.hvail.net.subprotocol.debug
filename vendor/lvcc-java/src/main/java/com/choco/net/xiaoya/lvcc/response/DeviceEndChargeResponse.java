package com.choco.net.xiaoya.lvcc.response;

/**
 * @Author: tanning
 * @Date: 2024/06/13/14:03
 * @Description:结束充电：设备->网关(响应)
 */
public final class DeviceEndChargeResponse {

    private DeviceEndChargeResponse() {
    }

    public static byte[] buildContent() {
        return new byte[]{0x01};
    }
}
