package com.choco.net.xiaoya.lvcc.response;

import com.choco.net.xiaoya.lvcc.utils.LvccByteFactory;

/**
 * 心跳：设备->网关(响应)，指令内容：当前服务器时间
 */
public final class DeviceHeatResponse {

    private DeviceHeatResponse() {
    }

    public static byte[] buildContent() {
        return LvccByteFactory.int2ByteArray((int) (System.currentTimeMillis() / 1000));
    }
}
