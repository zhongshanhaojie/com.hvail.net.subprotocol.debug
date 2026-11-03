package com.choco.net.xiaoya.lvcc.response;

/**
 * @Author: tanning
 * @Date: 2024/06/17/11:01
 * @Description:插座状态更改上报：设备->网关(响应)
 */
public final class PortStatusChangeUpResponse {

    private PortStatusChangeUpResponse() {
    }

    public static byte[] buildContent() {
        return new byte[]{1};
    }
}
