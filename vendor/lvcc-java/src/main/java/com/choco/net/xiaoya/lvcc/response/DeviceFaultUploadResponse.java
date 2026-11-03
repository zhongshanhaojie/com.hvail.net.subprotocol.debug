package com.choco.net.xiaoya.lvcc.response;

/**
 * @Description: 故障上报 0x0016：设备主动上报后，网关回 结果=1。
 * 【审核点】响应体仅 1 字节成功码；业务解析在 {@link ProtocolCallDataResponse#deviceFaultUpload}。
 */
public final class DeviceFaultUploadResponse {

    private DeviceFaultUploadResponse() {
    }

    /** 【审核点】协议响应：结果 1=成功 */
    public static byte[] buildContent() {
        return new byte[]{1};
    }
    
}
