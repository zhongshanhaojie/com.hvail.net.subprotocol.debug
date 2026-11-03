package com.choco.net.xiaoya.lvcc.models;

import com.choco.net.basic.dto.DeviceCardGetBalanceDTO;
import com.choco.net.basic.dto.DeviceRFICChargeUpDTO;

/**
 * @author tanning
 * @version 1.0.0
 * @date 2024/6/17 11:42
 */
public class ChargePileCardRequest {
    //设备编号
    private String deviceNo;
    //端口、插座
    private String port;
    //使用时间
    private long useTime;
    //支付金额
    private long payPrice;
    //卡号
    private String cardNumber;
    //订单号
    private String orderIndex;

    public ChargePileCardRequest() {
    }
    public ChargePileCardRequest(DeviceCardGetBalanceDTO dto) {
        this.deviceNo = dto.getDeviceNo();
        this.cardNumber = dto.getCardNo();
    }
    public ChargePileCardRequest(DeviceRFICChargeUpDTO dto) {
        this.deviceNo=dto.getDeviceNo();
        this.cardNumber=dto.getCardNo();
        this.port=String.valueOf(dto.getPortId()+1);
        this.orderIndex=dto.getOrderId();
        this.useTime=dto.getChargeTime();
    }



    public String getDeviceNo() {
        return deviceNo;
    }

    public void setDeviceNo(String deviceNo) {
        this.deviceNo = deviceNo;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public long getUseTime() {
        return useTime;
    }

    public void setUseTime(long useTime) {
        this.useTime = useTime;
    }

    public long getPayPrice() {
        return payPrice;
    }

    public void setPayPrice(long payPrice) {
        this.payPrice = payPrice;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(String orderIndex) {
        this.orderIndex = orderIndex;
    }
}