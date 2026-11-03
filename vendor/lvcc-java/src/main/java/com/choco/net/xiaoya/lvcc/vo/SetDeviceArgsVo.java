package com.choco.net.xiaoya.lvcc.vo;

import com.choco.net.basic.dto.DeviceDynamicPropsDTO;
import com.choco.net.basic.ex.DeviceCommandArgsCountException;
import com.choco.net.basic.ex.ProtocolException;
import com.choco.net.xiaoya.lvcc.utils.LvccByteFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: tanning
 * @Date: 2024/06/24/10:08
 * @Description:修改设备配置参数:pile_dynamic_change:[k1,v1]:[k2,v2]:[k3,v3]:...
 */
public class SetDeviceArgsVo implements IProtocolOutput{
    private final static Pattern fieldPattern = Pattern.compile("\\[(\\w+),(\\w+)\\.?[0-9]?\\]");
    private int minPower;//最小功率(默认：15W)
    private int maxPower;//最大功率(默认：750W)
    private int noLoadWaitTime;//空载等待时间(默认：15s)
    private int fullWaitTime;//充满等待时间(默认：1800s)
    private int heartTime;//心跳间隔(默认30s)
    private int chargeDataReportTime;//充电数据汇报间隔时间(默认：1200s)
    private int workVoltage;//工作电压(默认值：230v)
    private int deviceInfoUploadTime;//设备信息上报(默认：12小时)
    private int temperatureAlarm;//温度报警
    private int smokeAlarmStatus;//烟雾报警状态(0.关闭，1.开启)
    @Override
    public void inCommandString(String command) throws ProtocolException {
    String[] args = command.split(":");
    if (args.length != 2) throw new DeviceCommandArgsCountException("修改设备配置参数 参数数量错误");
    this.minPower=15;
    this.maxPower=750;
    this.noLoadWaitTime=15;
    this.fullWaitTime=1800;
    this.heartTime=30;
    String kv = args[1];
    Matcher matcher = fieldPattern.matcher(kv);
    if (!matcher.matches()) throw new DeviceCommandArgsCountException("修改设备配置参数 参数格式错误");
    String key = matcher.group(1), val = matcher.group(2);
    this.chargeDataReportTime=Integer.parseInt(val);
    this.workVoltage=230;
    this.deviceInfoUploadTime=12;
    this.temperatureAlarm=80;
    this.smokeAlarmStatus=0;
    }

    @Override
    public byte[] outCommandBytes() {
        byte[] result=new byte[15];
        byte[] minPowerBytes = LvccByteFactory.ShortToBytes(this.minPower);
        result[0] = minPowerBytes[0];
        result[1] = minPowerBytes[1];
        byte[] maxPowerBytes = LvccByteFactory.ShortToBytes(this.maxPower);
        result[2] = maxPowerBytes[0];
        result[3] = maxPowerBytes[1];
        //todo 空载等待时间
        byte[] noLoadWaitTimeBytes=LvccByteFactory.ShortToBytes(this.noLoadWaitTime);
        result[4] = noLoadWaitTimeBytes[0];
        result[5] = noLoadWaitTimeBytes[1];
        //todo 充满等待时间
        byte[] fullWaitTimeBytes = LvccByteFactory.ShortToBytes(this.fullWaitTime);
        result[6] = fullWaitTimeBytes[0];
        result[7] = fullWaitTimeBytes[1];
        //todo 心跳间隔
        result[8]=(byte) this.heartTime;
        //todo 充电数据汇报间隔时间
        byte[] chargeDataReportTimeBytes = LvccByteFactory.ShortToBytes(this.chargeDataReportTime);
        result[9]=chargeDataReportTimeBytes[0];
        result[10]=chargeDataReportTimeBytes[1];
        //todo 工作电压
        result[11]=(byte)this.workVoltage;
        //todo 设备信息上报
        result[12]=(byte)this.deviceInfoUploadTime;
        //todo 温度报警阀值
        result[13]=(byte)this.temperatureAlarm;
        //todo 烟雾报警状态
        result[14]=(byte)this.smokeAlarmStatus;
        return result;
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

    public int getNoLoadWaitTime() {
        return noLoadWaitTime;
    }

    public void setNoLoadWaitTime(int noLoadWaitTime) {
        this.noLoadWaitTime = noLoadWaitTime;
    }

    public int getFullWaitTime() {
        return fullWaitTime;
    }

    public void setFullWaitTime(int fullWaitTime) {
        this.fullWaitTime = fullWaitTime;
    }

    public int getHeartTime() {
        return heartTime;
    }

    public void setHeartTime(int heartTime) {
        this.heartTime = heartTime;
    }

    public int getChargeDataReportTime() {
        return chargeDataReportTime;
    }

    public void setChargeDataReportTime(int chargeDataReportTime) {
        this.chargeDataReportTime = chargeDataReportTime;
    }

    public int getWorkVoltage() {
        return workVoltage;
    }

    public void setWorkVoltage(int workVoltage) {
        this.workVoltage = workVoltage;
    }

    public int getDeviceInfoUploadTime() {
        return deviceInfoUploadTime;
    }

    public void setDeviceInfoUploadTime(int deviceInfoUploadTime) {
        this.deviceInfoUploadTime = deviceInfoUploadTime;
    }

    public int getTemperatureAlarm() {
        return temperatureAlarm;
    }

    public void setTemperatureAlarm(int temperatureAlarm) {
        this.temperatureAlarm = temperatureAlarm;
    }

    public int getSmokeAlarmStatus() {
        return smokeAlarmStatus;
    }

    public void setSmokeAlarmStatus(int smokeAlarmStatus) {
        this.smokeAlarmStatus = smokeAlarmStatus;
    }
}
