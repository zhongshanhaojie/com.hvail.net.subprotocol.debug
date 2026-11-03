package com.choco.net.xiaoya.lvcc.vo;

import com.choco.net.basic.ex.DeviceCommandArgsCountException;
import com.choco.net.basic.ex.ProtocolException;
import com.choco.net.basic.utils.ByteArrayUtil;
import com.choco.net.xiaoya.lvcc.LvccProtoDecode;
import com.choco.net.xiaoya.lvcc.utils.LvccByteFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * @Author: tanning
 * @Date: 2024/06/07/17:24
 * @Description: 开始充电
 */
public class StartChargeVo implements IProtocolOutput {
    private final static Logger log = LoggerFactory.getLogger(StartChargeVo.class);
    /****
     * 订单号
     ***/
    private String orderNo;

    /****
     * 端口号/插座
     */
    private int port;
    /****
     * 最小功率（单位：瓦）默认1
     */
    private int minPower;

    /****
     * 最大功率（单位：瓦）默认800
     */
    private int maxPower;
    /**
     * 空载等待时间（单位:秒）默认：30
     */
    private int noLoadWaitTime;
    /**
     * 充满等待时间（单位：秒）默认：30秒
     */
    private int fullWaitTime;
    /****
     * 充电时长（单位：10分钟）
     */
    private int chargeTime;
    /**
     * 计费方式（1.比列时间，2最大功率，3，电量计费，平价功率）默认1
     */
    private int billingMode;
    /**
     * 开电方式（1，时间，2金额，3，度数）默认1
     */
    private int turnOnMode;

    /****
     * 充电模式（是否充满断电0否、1，是） 默认1
     ***/
    private byte mode;

    /*****
     * charge_start:{订单号}:{端口}:{模式}:{时长(秒)}:{最小功率}:{最大功率}
     */
    @Override
    public void inCommandString(String command) throws ProtocolException {
        String[] args = command.split(":");
        //todo 自定义最大功率和最小功率
        if(args.length==7){
            this.minPower=Integer.parseInt(args[5]);
            this.maxPower=Integer.parseInt(args[6]);
        } else if (args.length==6) {
            this.minPower=Integer.parseInt(args[5]);
            this.maxPower = 800;
        }else if(args.length==5){
            this.minPower=25;
            this.maxPower = 800;
        }else{
            throw new DeviceCommandArgsCountException("开启充电 参数数量错误");
        }

        log.debug("StartChargeVo->inCommandString 原orderNo:" + args[1]);
        if (args[1].length() > 20) {
            throw new DeviceCommandArgsCountException("订单号大于20");
        }
       BigInteger s= new BigInteger(args[1]);
        this.orderNo=String.format("%020d", s);
        //this.orderNo = args[1];
        log.debug("StartChargeVo->inCommandString 当前orderNo:" + this.orderNo);
        this.port=args[2].equals("0")?Integer.parseInt(args[2]):Integer.parseInt(args[2])-1;

        this.noLoadWaitTime=30;
        this.fullWaitTime = 30;
        log.debug("StartChargeVo->inCommandString chargeTime:"+Integer.parseInt(args[4]));
        this.chargeTime = Integer.parseInt(args[4]) / 60;
        this.billingMode = 1;
        this.turnOnMode = 1;
        this.mode = 1;
    }

    @Override
    public byte[] outCommandBytes() {
        byte[] result = new byte[34];
        // todo 插座
        result[0] = (byte) this.port;
        // todo 最小功率
        byte[] minPowerBitys = LvccByteFactory.ShortToBytes(this.minPower);
        result[1] = minPowerBitys[0];
        result[2] = minPowerBitys[1];
        //todo 最大功率
        byte[] maxPowerBitys = LvccByteFactory.ShortToBytes(this.maxPower);
        result[3] = maxPowerBitys[0];
        result[4] = maxPowerBitys[1];
        //todo 空载等待时间
        byte[] noLoadWaitTimeBytes=LvccByteFactory.ShortToBytes(this.noLoadWaitTime);
        result[5] = noLoadWaitTimeBytes[0];
        result[6] = noLoadWaitTimeBytes[1];
        //todo 充满等待时间
        byte[] fullWaitTimeBytes = LvccByteFactory.ShortToBytes(this.fullWaitTime);
        result[7] = fullWaitTimeBytes[0];
        result[8] = fullWaitTimeBytes[1];

        byte[] chargeTimeBitys = LvccByteFactory.ShortToBytes(this.chargeTime);
        result[9] = chargeTimeBitys[0];
        result[10] = chargeTimeBitys[1];
        // todo 订单号
        log.debug("StartChargeVo->outCommandBytes orderNo:" + this.orderNo);
        byte[] orderNoBs = this.orderNo.getBytes(StandardCharsets.US_ASCII);
        log.debug("StartChargeVo->outCommandBytes hex to string orderNo:" +  LvccByteFactory.bytesToString(orderNoBs));
        int orderNoLen = this.orderNo.length();
        log.debug("订单号hex len：" + orderNoLen);
        System.arraycopy(orderNoBs, 0, result, 11, orderNoBs.length);
        //todo 计费方式
        result[31] = (byte) this.billingMode;
        //todo 开电方式
        result[32] = (byte) this.turnOnMode;
        //todo 是否充满断电
        result[33] = (byte) this.mode;
        return result;
    }


    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getMinPower() {
        return minPower;
    }

    public void setMinPower(int minPower) {
        this.minPower = minPower;
    }

    public int getMaxPower() {
        return maxPower;
    }

    public void setMaxPower(int maxPower) {
        this.maxPower = maxPower;
    }

    public int getFullWaitTime() {
        return fullWaitTime;
    }

    public void setFullWaitTime(int fullWaitTime) {
        this.fullWaitTime = fullWaitTime;
    }

    public int getChargeTime() {
        return chargeTime;
    }

    public void setChargeTime(int chargeTime) {
        this.chargeTime = chargeTime;
    }

    public int getBillingMode() {
        return billingMode;
    }

    public void setBillingMode(int billingMode) {
        this.billingMode = billingMode;
    }

    public int getTurnOnMode() {
        return turnOnMode;
    }

    public void setTurnOnMode(int turnOnMode) {
        this.turnOnMode = turnOnMode;
    }

    public byte getMode() {
        return mode;
    }

    public void setMode(byte mode) {
        this.mode = mode;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
}
