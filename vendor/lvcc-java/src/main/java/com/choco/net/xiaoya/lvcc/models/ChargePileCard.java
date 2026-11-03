package com.choco.net.xiaoya.lvcc.models;

import java.math.BigDecimal;

/**
 * @Author: tanning
 * @Date: 2024/06/17/19:03
 * @Description: 刷卡查询可充电时间
 */
public class ChargePileCard {

    private String cardId;
    /**
     * 结果
     * 1：成功
     * 2：异常失败
     * 3：余额不足
     * 4: 该卡未录入系统
     */
    //结果(1：成功
    //2：异常失败
    //3：余额不足
    //4: 该卡未录入系统)
    private int result;
    //卡号
    private String aliasId;
    //卡余额
    private BigDecimal totalBalance;
    //可充电时长(分钟)
    private int chargeTime;
    //订单号(长度20,不足20补0)
    private String orderNo;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }

    public String getAliasId() {
        return aliasId;
    }

    public void setAliasId(String aliasId) {
        this.aliasId = aliasId;
    }

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(BigDecimal totalBalance) {
        this.totalBalance = totalBalance;
    }

    public int getChargeTime() {
        return chargeTime;
    }

    public void setChargeTime(int chargeTime) {
        this.chargeTime = chargeTime;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
}
