package com.choco.net.xiaoya.lvcc.response;


import com.choco.net.basic.data.ProtocolByteEntity;
import com.choco.net.basic.data.ProtocolDynamicAttribute;
import com.choco.net.basic.dto.*;
import com.choco.net.basic.utils.AppHttpFactory;
import com.choco.net.xiaoya.lvcc.enums.EnumLvccCabinetFault;
import com.choco.net.xiaoya.lvcc.enums.EnumLvccFaultCode;
import com.choco.net.xiaoya.lvcc.models.DeviceNetHeartLvccModel;
import com.choco.net.xiaoya.lvcc.models.DeviceRunningOrder;
import com.choco.net.xiaoya.lvcc.utils.LvccByteFactory;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * @author hvail
 * @version 1.0.0
 * @file ProtocolCallDataResponse.java
 * @date 2022/4/15 16:45
 * @note 基于 Profile 进行协议解析
 */
public class ProtocolCallDataResponse {

    final static String DEVICE_RUNNING_ORDER = System.getenv("device_running_order") == null ? "https://device.hunanxiaoya.com/device/task/order/run/" : System.getenv("device_running_order");

    private final static Logger log = LoggerFactory.getLogger(ProtocolCallDataResponse.class);

    private final static Gson gson = new Gson();

    // 注册
    public static Collection<DeviceBasicDTO> deviceRegister(String deviceNo, byte[] buffer) {
        ProtocolByteEntity entity = new ProtocolByteEntity(deviceNo);
        entity.setType("DEVICE.REGISTER");

        // DeviceRegisterDTO dto = new DeviceRegisterDTO(deviceNo);

        // todo 主控版本
        int masterControlVersion = LvccByteFactory.ParseShort(buffer, 1);
        ProtocolDynamicAttribute _master = new ProtocolDynamicAttribute("SoftVersion", String.valueOf(masterControlVersion), "INT");

        //todo 刷卡板版本
        int swipeCardVersion = LvccByteFactory.ParseShort(buffer, 3);
        ProtocolDynamicAttribute swipe = new ProtocolDynamicAttribute("HardVersion", String.valueOf(swipeCardVersion), "INT");

        entity.setAttributes(Arrays.asList(_master, swipe));
        return Collections.singletonList(entity);

        // //todo 通信模块版本
        // int communicateVersion = LvccByteFactory.ParseShort(buffer, 5);
        // dto.setVersion(masterControlVersion + "." + swipeCardVersion + "." + communicateVersion);
        // //todo 插座数
        // int port = buffer[7];
        // dto.setPortNum(port);
        // //todo 设备类型
        // int deviceType = buffer[8];
        // dto.setType(EnumLvccDeviceType.getNameByCode(deviceType));
        // dto.setICCID("测试");
        // log.debug("设备注册 dto callback:{},设备类型：{}", gson.toJson(dto), deviceType);
        // return Collections.singletonList(dto);

    }

    // 心跳
    public static Collection<DeviceBasicDTO> deviceHeat(String deviceNo, byte[] buffer) {
        DeviceNetHeartLvccModel dto = new DeviceNetHeartLvccModel(deviceNo);
        return Collections.singletonList(dto);
    }

    //开启充电
    public static Collection<DeviceBasicDTO> deviceStartCharge(String deviceNo, byte[] buffer) {
        System.out.println("ProtocolCallDataResponse 开启充电->" + LvccByteFactory.bytesToString(buffer));
        DeviceChargeCallBeginDTO dto = new DeviceChargeCallBeginDTO(deviceNo);
        // todo 结果
        dto.setResult(buffer[0]);
        // todo 插座
        dto.setChannelId(buffer[1] + 1);
        // todo 剩余充电时长
        dto.setChargeTime(LvccByteFactory.ParseShort(buffer, 2));
        System.out.println("开启充电->" + gson.toJson(dto));
        return Collections.singletonList(dto);
    }

    //查询充电状态
    public static Collection<DeviceBasicDTO> deviceQueryChargeStatus(String deviceNo, byte[] buffer) {
        DeviceBasicDTO result = new DeviceBasicDTO(deviceNo);
        return Collections.singletonList(result);
    }

    //停止充电
    public static Collection<DeviceBasicDTO> deviceStopCharge(String deviceNo, byte[] buffer) {
        DeviceChargeCallEndDTO result = new DeviceChargeCallEndDTO(deviceNo);
        // todo 结果
        result.setResult(buffer[0]);
        // todo 插座
        result.setChannelId(buffer[1] + 1);
        // todo 已充电时间(分钟)
        int chargeTime = LvccByteFactory.ParseShort(buffer, 2);
        result.setChargeTime(chargeTime * 60);
        // todo 电瓶电量
        result.setSumElectric(buffer[4]);
        // todo 订单号
        String order = LvccByteFactory.ParseString(buffer, 5, 20);
        result.setOrderId(order.replaceAll("^0*", ""));
        log.debug("停止充电-> {}", gson.toJson(result));
        return Collections.singletonList(result);
    }

    //结束充电
    public static Collection<DeviceBasicDTO> deviceEndCharge(String deviceNo, byte[] buffer) {
        DeviceChargeCallEndDTO result = new DeviceChargeCallEndDTO(deviceNo);
        // todo 结束原因
        result.setEvent(buffer[0]);
        // todo 插座号
        result.setChannelId(buffer[1] + 1);
        // todo 订单号
        String order = LvccByteFactory.ParseString(buffer, 2, 20);
        result.setOrderId(order.replaceAll("^0*", ""));
        // todo 耗电量
        int electric = LvccByteFactory.ParseShort(buffer, 22);
        result.setSumElectric(electric);
        // todo 结束时功率
        result.setCapacity(LvccByteFactory.ParseShort(buffer, 24));
        // todo 结束时间
        result.setChargeTime(LvccByteFactory.ParseInt(buffer, 26));
        log.debug("结束充电-> 指令内容：{}", LvccByteFactory.bytesToString(buffer));
        log.debug("结束充电-> {} ", gson.toJson(result));
        return Collections.singletonList(result);
    }

    //设备重启
    public static Collection<DeviceBasicDTO> deviceResatrt(String deviceNo, byte[] buffer) {
        DeviceResultDTO result = new DeviceResultDTO(deviceNo);
        result.setResult(buffer[0]);
        return Collections.singletonList(result);
    }

    //查询设备参数信息
    public static Collection<DeviceBasicDTO> queryDeviceArgs(String deviceNo, byte[] buffer) {
        DeviceBasicDTO result = new DeviceBasicDTO(deviceNo);
        log.debug("查询设备参数信息->指令内容:{}", LvccByteFactory.bytesToString(buffer));
        return Collections.singletonList(result);
    }

    //修改设备参数信息
    public static Collection<DeviceBasicDTO> setDeviceArgs(String deviceNo, byte[] buffer) {
        DeviceBasicDTO result = new DeviceBasicDTO(deviceNo);
        return Collections.singletonList(result);
    }

    //刷卡查询返回可充电时间
    public static Collection<DeviceBasicDTO> cardQueryChargeTime(String deviceNo, byte[] buffer) {
        DeviceCardGetBalanceDTO result = new DeviceCardGetBalanceDTO(deviceNo);
        result.setCardNo(LvccByteFactory.ParseHexString(buffer, 0, buffer.length - 1));
        log.debug("刷卡查询返回可充电时间:卡号->" + result.getCardNo());
        return Collections.singletonList(result);
    }

    //上报刷卡订单
    public static Collection<DeviceBasicDTO> uploadCardOrder(String deviceNo, byte[] buffer) {
        DeviceRFICChargeUpDTO result = new DeviceRFICChargeUpDTO(deviceNo);
        //todo 插座
        result.setPortId(buffer[0]);
        //todo 充电时间
        int chargeTime = LvccByteFactory.ParseShort(buffer, 1);
        result.setChargeTime(chargeTime * 60);
        //todo 订单号
        String order = LvccByteFactory.ParseString(buffer, 3, 20);
        result.setOrderId(order.replaceAll("^0*", ""));
        //todo 卡号
        result.setCardNo(LvccByteFactory.ParseHexString(buffer, 23, 4));
        return Collections.singletonList(result);
    }

    // 充电状态定时上报
    public static Collection<DeviceBasicDTO> deviceChargeStatusUp(String deviceNo, byte[] buffer) {
        List<DeviceBasicDTO> list = new ArrayList<>();
        try {
//            var runningOrder = AppHttpFactory._getSingle(DEVICE_RUNNING_ORDER + deviceNo, JsonObject.class);
//            List<DeviceRunningOrder> orders = gson.fromJson(runningOrder.getAsJsonArray("data"), new TypeToken<List<DeviceRunningOrder>>() {
//            }.getType());
//            log.info("充电状态定时上报 query集合：{}", gson.toJson(orders));
            int count = 0;
            for (int i = 1; i < buffer.length; i += 2) {
                count++;
                DeviceChannelChargeElectricDTO result = new DeviceChannelChargeElectricDTO(deviceNo);
                result.setChannelId(count);
//                List<DeviceRunningOrder> running = orders.stream().filter(x -> x.getPort().equals((result.getChannelId()) + "")).toList();
//                result.setOrderId(running == null || running.size() == 0 ? "0" : running.get(0).getOrderId());
                int capacity = LvccByteFactory.ParseShort(buffer, i);
                result.setCapacity(capacity);
                if (capacity >= 5) {
                    list.add(result);
                }
            }
            log.trace("充电状态定时上报(功率) ： {}", gson.toJson(list));
//            log.info("充电状态定时上报：{}", LvccByteFactory.bytesToString(buffer));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    //插座状态更改上报
    public static Collection<DeviceBasicDTO> portStatusChangeUp(String deviceNo, byte[] buffer) {
        DevicePortStatusSingleDTO result = new DevicePortStatusSingleDTO(deviceNo);
        //todo 插座
        result.setPort(buffer[0] + 1);
        //todo 插座状态
        result.setPortStatus(buffer[1]);
        log.info("插座状态更改上报:{}", gson.toJson(result));
        return Collections.singletonList(result);
    }

    //实时充电状态定时上报
    public static Collection<DeviceBasicDTO> RealChargeStatusTimingUp(String deviceNo, byte[] buffer) {
        List<DeviceBasicDTO> list = new ArrayList<>();
        if (buffer == null || buffer.length < 4) {
            return list;
        }
        try {

            // 末字节为异或校验，指令内容不含校验位
            int contentLen = buffer.length - 1;
            int signal = buffer[0] & 0xFF;
            int portNum = buffer[1] & 0xFF;
            if (portNum == 0) {
                log.warn("实时充电状态定时上报 端口数异常:{}", portNum);
                return list;
            }

            // 0x0048 指令内容布局：信号(1) + 端口数(1) + N*耗电 + N*电压 + N*电流 + N*温度 + 环境温度 + 整机数据条数
            int powerBase = 2;
            int voltageBase = powerBase + portNum * 2;
            int currentBase = voltageBase + portNum * 2;
            int tempBase = currentBase + portNum * 2;
            log.debug("实时充电状态定时上报 信号:{} 端口数:{}", signal, portNum);

            for (int i = 0; i < portNum; i++) {
                int powerOff = powerBase + i * 2;
                if (powerOff + 2 > contentLen) {
                    break;
                }
                int voltageOff = voltageBase + i * 2;
                int currentOff = currentBase + i * 2;

                int electric = LvccByteFactory.ParseShort(buffer, powerOff);
                int voltageRaw = voltageOff + 2 <= contentLen ? LvccByteFactory.ParseShort(buffer, voltageOff) : 0;
                int currentRaw = currentOff + 2 <= contentLen ? LvccByteFactory.ParseShort(buffer, currentOff) : 0;

                int capacity = 0;
                if (voltageRaw > 0 && currentRaw > 0) {
                    BigDecimal voltage = BigDecimal.valueOf(voltageRaw).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    BigDecimal current = BigDecimal.valueOf(currentRaw).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
                    capacity = voltage.multiply(current).intValue();
                }
                if (!isChargingPort(electric, currentRaw, capacity)) {
                    continue;
                }

                if (electric > 0) {
                    log.trace("实时充电状态定时上报 端口:{} 耗电:{} 电压:{} 电流:{} 功率:{}W",
                            i + 1, electric, voltageRaw, currentRaw, capacity);
                }

                DeviceChannelChargeElectricDTO result = new DeviceChannelChargeElectricDTO(deviceNo);
                result.setChannelId(i + 1);
                result.setElectric(electric);
                result.setCapacity(capacity);

                list.add(result);
                log.debug("实时充电状态定时上报 端口:{} 订单:{} 耗电:{} 电压:{} 电流:{} 功率:{}W",
                        result.getChannelId(), result.getOrderId(), electric, voltageRaw, currentRaw, capacity);
            }

            int tempOff = tempBase;
            for (int i = 0; i < portNum && tempOff + 2 <= contentLen; i++, tempOff += 2) {
                log.debug("实时充电状态定时上报 端口{}温度:{}", i + 1, LvccByteFactory.ParseShort(buffer, tempOff));
            }
            if (tempOff + 2 <= contentLen) {
                log.debug("实时充电状态定时上报 环境温度:{}", LvccByteFactory.ParseShort(buffer, tempOff));
                tempOff += 2;
            }
            if (tempOff + 2 <= contentLen) {
                log.debug("实时充电状态定时上报 整机数据条数:{}", LvccByteFactory.ParseShort(buffer, tempOff));
            }

//            log.trace("充电状态定时上报(完整) {}", gson.toJson(list));
//            log.info("实时充电状态定时上报：{}", LvccByteFactory.bytesToString(buffer));
        } catch (Exception e) {
            log.warn("实时充电状态定时上报 解析异常 设备:{} 错误:{}", deviceNo, e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 【审核点-告警主路径】0x0016 故障上报（设备主动上报，通讯标识 0x12）。
     * <pre>
     * 协议字段：
     *   [0] 插座 byte     — 0xFF=整机故障，否则为端口号(0-based)
     *   [1] 故障码 byte   — 整机/通道码表见 {@link EnumLvccFaultCode}
     *   [2] 故障状态 byte — 0=故障解除，1=故障产生
     *   [3..] 故障详细    — Short 或 Int（漏电/计量位图为 32bit）
     * </pre>
     * DTO 映射（无独立 faultStatus 字段，供业务层消费）：
     * <ul>
     *   <li>channelId = 0xFF 保持 255；端口则 +1 转为业务 1-based（与开电/插座上报一致）</li>
     *   <li>eventType = 故障码原值</li>
     *   <li>remainTime = 故障状态(0/1) —— 复用字段，不是剩余时长</li>
     *   <li>message = faultStatus;detail;scope;desc 便于平台解析</li>
     * </ul>
     */
    public static Collection<DeviceBasicDTO> deviceFaultUpload(String deviceNo, byte[] buffer) {
        // 【审核点】最短需含 插座+故障码+故障状态；详情可缺省（早期设备）
        if (buffer == null || buffer.length < 3) {
            log.warn("故障上报 body 过短 device={} len={}", deviceNo, buffer == null ? -1 : buffer.length);
            return Collections.emptyList();
        }

        int socketRaw = buffer[0] & 0xFF;
        int faultCode = buffer[1] & 0xFF;
        int faultStatus = buffer[2] & 0xFF; // 0 解除 / 1 产生
        boolean wholeDevice = socketRaw == 0xFF;

        // 【审核点】整机保持 0xFF；端口 +1。漏电协议也要求上送 0xFF，勿对 0xFF 做 +1
        int channelId = wholeDevice ? 0xFF : (socketRaw + 1);

        // 【审核点】详情长度：>=4 字节按 Int（位图），否则按 Short；再短则 detail=0
        long detail;
        if (buffer.length >= 7) {
            detail = LvccByteFactory.ParseInt(buffer, 3) & 0xFFFFFFFFL;
        } else if (buffer.length >= 5) {
            detail = LvccByteFactory.ParseShort(buffer, 3);
        } else {
            detail = 0L;
        }

        String desc = EnumLvccFaultCode.describe(faultCode, wholeDevice);
        DeviceChannelEventDTO result = new DeviceChannelEventDTO(deviceNo);
        result.setChannelId(channelId);
        result.setEventType(faultCode);
        // 【审核点】remainTime 承载故障状态，业务切勿当剩余充电秒数
        result.setRemainTime(faultStatus);
        result.setMessage(String.format(
                "faultStatus=%d;detail=%d;scope=%s;desc=%s",
                faultStatus, detail, wholeDevice ? "device" : "port", desc));

        log.info("故障上报 device={} channel={} code={} status={} detail={} desc={}",
                deviceNo, channelId, faultCode, faultStatus, detail, desc);
        return Collections.singletonList(result);
    }

    /**
     * 【审核点-告警】0x0038 复位设备故障 —— 设备对网关下发的应答（通讯标识 0x11）。
     * 协议响应仅 1 字节：结果 1=成功 / 其它=失败。网关不再回包。
     */
    public static Collection<DeviceBasicDTO> deviceFaultReset(String deviceNo, byte[] buffer) {
        DeviceResultDTO result = new DeviceResultDTO(deviceNo);
        if (buffer == null || buffer.length < 1) {
            log.warn("复位故障应答 body 为空 device={}", deviceNo);
            result.setResult(2);
            return Collections.singletonList(result);
        }
        // 【审核点】与重启等下行应答一致，原样透传结果码
        result.setResult(buffer[0] & 0xFF);
        log.info("复位设备故障应答 device={} result={}", deviceNo, result.getResult());
        return Collections.singletonList(result);
    }

    /**
     * 【审核点-告警】0x0057 换电柜设备状态主动上报（通讯标识 0x12）。
     * <p>
     * 本方法<strong>只提取故障仓</strong>（故障详情!=0）上抛 {@link DeviceChannelEventDTO}；
     * 完整仓位属性（SOC/电压等）不在本版本展开，避免非换电业务被噪音淹没。
     * <pre>
     * 布局：数据条数(1) + 属性条数(1) + N * 仓位块
     * 属性条数=12 时单仓 39 字节：
     *   柜门号(1)+仓位状态(1)+柜门状态(1)+电池状态(1)+SOC(2)+预计充满(1)
     *   +故障详情(1)+规格(1)+电压(2)+电流(2)+温度(2)+电池编号(24)
     * </pre>
     */
    public static Collection<DeviceBasicDTO> cabinetStatusUp(String deviceNo, byte[] buffer) {
        List<DeviceBasicDTO> list = new ArrayList<>();
        if (buffer == null || buffer.length < 2) {
            log.warn("换电柜状态上报 body 过短 device={}", deviceNo);
            return list;
        }

        int slotCount = buffer[0] & 0xFF;
        int attrCount = buffer[1] & 0xFF;
        // 【审核点】当前协议版本属性条数=12 → 39 字节/仓；其它 attrCount 暂不解析，避免错位
        final int SLOT_BYTES_V12 = 39;
        if (attrCount != 12) {
            log.warn("换电柜状态上报 attrCount={} 非预期12，跳过故障提取 device={} slots={}",
                    attrCount, deviceNo, slotCount);
            return list;
        }

        int offset = 2;
        for (int i = 0; i < slotCount; i++) {
            if (offset + SLOT_BYTES_V12 > buffer.length) {
                log.warn("换电柜状态上报仓位截断 device={} index={} offset={}", deviceNo, i, offset);
                break;
            }
            int doorNo = buffer[offset] & 0xFF;
            int slotStatus = buffer[offset + 1] & 0xFF;
            int faultDetail = buffer[offset + 7] & 0xFF; // 【审核点】故障详情在仓位块第 8 字节（0-based offset+7）

            // 【审核点】仅故障详情非 0 时上抛告警；柜门号 0 表示网关仓，原样保留
            if (faultDetail != 0) {
                DeviceChannelEventDTO event = new DeviceChannelEventDTO(deviceNo);
                event.setChannelId(doorNo);
                event.setEventType(faultDetail);
                // 仓位状态 4=故障；此处 remainTime 放仓位状态，便于业务区分
                event.setRemainTime(slotStatus);
                event.setMessage(String.format(
                        "cabinetFault=%d;slotStatus=%d;desc=%s;scope=cabinet",
                        faultDetail, slotStatus, EnumLvccCabinetFault.describe(faultDetail)));
                list.add(event);
                log.info("换电柜故障仓 device={} door={} fault={} slotStatus={} desc={}",
                        deviceNo, doorNo, faultDetail, slotStatus, EnumLvccCabinetFault.describe(faultDetail));
            }
            offset += SLOT_BYTES_V12;
        }
        return list;
    }


    /**
     * 判定充电中的最小电流（毫安），用于过滤 1mA 级空载噪声
     */
    private static final int CHARGE_CURRENT_THRESHOLD_MA = 50;

    /**
     * 0x0048 端口是否符合充电条件：累计耗电、有效输出电流或计算功率任一满足即视为在充。
     */
    private static boolean isChargingPort(int electric, int currentMa, int capacityW) {
        return electric > 0 || currentMa >= CHARGE_CURRENT_THRESHOLD_MA || capacityW > 0;
    }

    private static Optional<DeviceDynamicPropsDTO.DeviceDynamicPropsItem> findAnyByList(List<DeviceDynamicPropsDTO.DeviceDynamicPropsItem> list, String field) {
        return list.stream().filter(d -> d.getPropsName().equals(field)).findAny();
    }
}
