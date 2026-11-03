package com.choco.net.xiaoya.lvcc.models;

/**
 * @Author: tanning
 * @Date: 2024/06/21/10:38
 * @Description: 正在运行的订单
 */
public class DeviceRunningOrder {

    private String orderId;
    private String deviceId;
    private String port;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

}
