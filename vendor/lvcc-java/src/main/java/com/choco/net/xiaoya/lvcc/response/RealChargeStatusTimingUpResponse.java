package com.choco.net.xiaoya.lvcc.response;

/**
 * @Author: tanning
 * @Date: 2024/06/17/11:11
 * @Description:实时充电状态定时上报: 设备->网关(响应)
 */
public final class RealChargeStatusTimingUpResponse {

    private RealChargeStatusTimingUpResponse() {
    }

    public static byte[] buildContent() {
        return new byte[]{1};
    }
}
