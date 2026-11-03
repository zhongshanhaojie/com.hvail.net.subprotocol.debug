package com.choco.net.xiaoya.lvcc;

import com.choco.net.basic.dto.*;
import com.choco.net.basic.ex.ProtocolException;
import com.choco.net.basic.pojo.DeviceCommandRequest;
import com.choco.net.basic.pojo.DeviceCommandSupport;
import com.choco.net.basic.utils.ByteArrayUtil;
import com.choco.net.netty.basic.protocol.IProtocolDataHandler;
import com.choco.net.xiaoya.lvcc.enums.EnumCallCommand;
import com.choco.net.xiaoya.lvcc.response.LvccProtocolFrame;
import com.choco.net.xiaoya.lvcc.response.LvccResponseDispatcher;
import com.choco.net.xiaoya.lvcc.response.ProtocolCallDataResponse;
import com.choco.net.xiaoya.lvcc.utils.LvccByteFactory;
import com.choco.net.xiaoya.lvcc.vo.*;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * @author hvail
 * @version 1.0.0
 * @file
 * @date 2024/5/24 9:41
 * @note 智能充电桩通讯协议 - 驴充充
 */
public class LvccProtoDecode implements IProtocolDataHandler {

    private final static Logger log = LoggerFactory.getLogger(LvccProtoDecode.class);

    /**
     * 设备上行子协议解析（发出方：设备）。
     * 与 {@link com.choco.net.xiaoya.lvcc.response.LvccResponseDispatcher} 的回包映射配合使用：
     * 此处能解析 ≠ 一定会回包，见 {@link #IgnoreCommands} 与 {@link #CheckCommandCallPix}。
     */
    final static Map<EnumCallCommand, IProtocolHandler> HandlerMap = new HashMap<>();

    /**
     * 网关主动下发指令（发出方：网关），见 {@link #ParseDTOCommand}
     */
    final static Map<String, Integer> ParseCommandMap = new HashMap<>();
    final static Map<String, Class<? extends IProtocolOutput>> ParseClazzMap = new HashMap<>();

    /**
     * 平台下发「复位设备故障」指令前缀（basic 包暂无常量）。
     * 【审核点】完整格式：{@code pile_fault_reset:{0关|1开}:{错误代码}} → 协议 0x0038
     */
    public static final String PILE_FAULT_RESET = "pile_fault_reset";

    /**
     * 设备对网关下发的应答（通讯标识 {@code 0x11}）指令列表。
     * <p>
     * 【审核点-修正注释】命中本列表且<strong>同时在 HandlerMap</strong>时：仍会解析上抛业务，
     * 仅 {@link #BasicDTOEncode} 返回 null（不再向设备回包）。
     * 仅当指令不在 HandlerMap 时，parseProtocolData 才会因本列表而跳过解析。
     * <p>
     * 典型：{@code 0x0005/0x0006/0x0007} 开启·查询·停止充电，{@code 0x0013/0x0014} 配置查询·设置，
     * {@code 0x0017/0x0019} 重启·查插座，{@code 0x0027/0x0028} 喇叭音量，{@code 0x0038} 故障复位，{@code 0x0025} OTA 等。
     */
    final static List<Integer> IgnoreCommands = Arrays.asList(
            0x0003, 0x0004, 0x0005, 0x0006, 0x0007, 0x000C,
            0x0011, 0x0013, 0x0014, 0x0017, 0x0019, 0x0025, 0x0027, 0x0028,
            0x0038, // 【审核点-告警】复位故障应答，不回包
            0x0063);

    static {
        // --- 设备上行：已实现的子协议解析（协议文档 60+ 指令中仅覆盖以下）---

        // 设备发起，网关须回专用数据
        // 注册(0x0001)
        HandlerMap.put(EnumCallCommand.REGISTER, ProtocolCallDataResponse::deviceRegister);
        // 心跳(0x0002) 主动上报 0x12
        HandlerMap.put(EnumCallCommand.HEART_KEEP, ProtocolCallDataResponse::deviceHeat);
        // 刷卡查询(0x0008) 设备请求 0x10
        HandlerMap.put(EnumCallCommand.CARD_QUERY_CHARGE_TIME, ProtocolCallDataResponse::cardQueryChargeTime);
        // 上报刷卡订单(0x0009) 设备请求 0x10
        HandlerMap.put(EnumCallCommand.UPLOAD_CARD_ORDER, ProtocolCallDataResponse::uploadCardOrder);

        // 设备对网关下发的应答（0x11），解析供业务层消费，网关不再回包（见 IgnoreCommands）
        // 开启充电 0x0005
        HandlerMap.put(EnumCallCommand.START_CHARGE, ProtocolCallDataResponse::deviceStartCharge);
        // 查看充电状态 0x0006
        HandlerMap.put(EnumCallCommand.QUERY_CHARGE_STATUS, ProtocolCallDataResponse::deviceQueryChargeStatus);
        // 停止充电 0x0007
        HandlerMap.put(EnumCallCommand.STOP_CHARGE, ProtocolCallDataResponse::deviceStopCharge);
        // 查询设备信息 0x0011
        HandlerMap.put(EnumCallCommand.QUERY_DEVICE_INFO, ProtocolCallDataResponse::deviceRegister);
        // 查询设备配置信息 0x0013
        HandlerMap.put(EnumCallCommand.QUERY_DEVICE_ARGS_INFO, ProtocolCallDataResponse::queryDeviceArgs);
        // 设置设备配置信息 0x0014
        HandlerMap.put(EnumCallCommand.SET_DEVICE_ARGS_INFO, ProtocolCallDataResponse::setDeviceArgs);
        // 设备重启 0x0017
        HandlerMap.put(EnumCallCommand.DEVICE_REBOOT, ProtocolCallDataResponse::deviceResatrt);
        // 查询设备插座状态 0x0019
        HandlerMap.put(EnumCallCommand.QUERY_DEVICE_PORT_STATUS, ProtocolCallDataResponse::deviceResatrt);

        // 设备主动上报 / 事件上报（网关须 ACK，见 LvccResponseDispatcher.ENCODER_MAP）
        // 充电结束 0x0010
        HandlerMap.put(EnumCallCommand.END_CHARGE, ProtocolCallDataResponse::deviceEndCharge);
        // 【审核点-告警】故障上报 0x0016 主动上报 0x12（烟感/温度/漏电/保险丝等）
        HandlerMap.put(EnumCallCommand.FAULT_UPLOAD, ProtocolCallDataResponse::deviceFaultUpload);
        // 插座状态更改上报 0x0021 主动上报 0x12
        HandlerMap.put(EnumCallCommand.PORT_STATUS_CHANGE_UP, ProtocolCallDataResponse::portStatusChangeUp);
        // 充电状态定时上报 0x0030 主动上报 0x12
        HandlerMap.put(EnumCallCommand.DEVICE_CHARGE_STATUS_UP, ProtocolCallDataResponse::deviceChargeStatusUp);
        // 【审核点-告警】复位故障应答 0x0038（设备响应 0x11，解析结果后不回包）
        HandlerMap.put(EnumCallCommand.FAULT_RESET, ProtocolCallDataResponse::deviceFaultReset);
        // 实时充电状态定时上报 0x0048 主动上报 0x12
        HandlerMap.put(EnumCallCommand.REAL_CHARGE_STATUS_timing_up, ProtocolCallDataResponse::RealChargeStatusTimingUp);
        // 【审核点-告警】换电柜状态主动上报 0x0057：仅提取故障仓上抛
        HandlerMap.put(EnumCallCommand.CABINET_STATUS_UP, ProtocolCallDataResponse::cabinetStatusUp);

        // 未实现解析的主动上报示例：0x0034 设备状态定时、0x0044 门禁刷卡、0x0050 端口状态定时、0x0052 客户定制等

        // --- 网关下行：平台指令 → 设备 ---
        //开启充电
        ParseCommandMap.put(DeviceCommandSupport.CHARGE_START, 0x0005);
        //查看充电状态
        ParseCommandMap.put(DeviceCommandSupport.QUERY_CHARGE_STATUS, 0x0006);
        //停止充电
        ParseCommandMap.put(DeviceCommandSupport.CHARGE_STOP, 0x0007);
        //查询设备信息
        ParseCommandMap.put(DeviceCommandSupport.QUERY_DEVICE_INFO, 0x0011);
        //查询设备配置信息
        ParseCommandMap.put(DeviceCommandSupport.PILE_ARGS_QUERY, 0x0013);
        //设置设备配置信息
        ParseCommandMap.put(DeviceCommandSupport.PILE_ARGS_DYNAMIC, 0x0014);
        //设备重启
        //ParseCommandMap.put(DeviceCommandSupport.DEVICE_REBOOT, 0x0017);
        ParseCommandMap.put(DeviceCommandSupport.PILE_REBOOT, 0x0017);
        //查询设备插座状态
        ParseCommandMap.put(DeviceCommandSupport.QUERY_DEVICE_PORT_STATUS, 0x0019);
        //设置喇叭音量
        ParseCommandMap.put(DeviceCommandSupport.POWER_BANK_VOLUME_LEVEL_SET, 0x0027);
        //查询喇叭音量
        ParseCommandMap.put(DeviceCommandSupport.POWER_BANK_VOLUME_LEVEL_GET, 0x0028);
        // 【审核点-告警】复位设备故障 → 0x0038
        ParseCommandMap.put(PILE_FAULT_RESET, 0x0038);


        ParseClazzMap.put(DeviceCommandSupport.CHARGE_START, StartChargeVo.class);
        ParseClazzMap.put(DeviceCommandSupport.CHARGE_STOP, StopChargeVo.class);
        ParseClazzMap.put(DeviceCommandSupport.QUERY_CHARGE_STATUS, QueryChargeStatusVo.class);
        ParseClazzMap.put(DeviceCommandSupport.QUERY_DEVICE_INFO, QueryDeviceInfoVo.class);
        //查询设备配置信息
        ParseClazzMap.put(DeviceCommandSupport.PILE_ARGS_QUERY, QueryDeviceArgsVo.class);
        //设置设备配置信息
        ParseClazzMap.put(DeviceCommandSupport.PILE_ARGS_DYNAMIC, SetDeviceArgsVo.class);
        ParseClazzMap.put(DeviceCommandSupport.PILE_REBOOT, DeviceRestartVo.class);
        ParseClazzMap.put(DeviceCommandSupport.QUERY_DEVICE_PORT_STATUS, QueryDevicePortStatusVo.class);
        ParseClazzMap.put(DeviceCommandSupport.POWER_BANK_VOLUME_LEVEL_SET, SetDeviceVolumeVo.class);
        ParseClazzMap.put(DeviceCommandSupport.POWER_BANK_VOLUME_LEVEL_GET, QueryDeviceVolumeVo.class);
        // 【审核点-告警】复位故障下行组包
        ParseClazzMap.put(PILE_FAULT_RESET, FaultResetVo.class);
    }

    /**
     * 基础协议解析
     *
     * @param buffer    获取的网络流数据
     * @param channelId 连接Id
     * @return 驴充充基础协议对象
     */
    @Override
    public ProtocolMicroDTO BasicDTODecode(byte[] buffer, String channelId) {
        log.debug("================================================================================================");
        log.debug("基础协议解析->butter:{}", LvccByteFactory.bytesToString(buffer));
        if (buffer.length == 0) {
            log.error("基础协议解析->the channel {} send buffer length is 0, throw Runtime Error", channelId);
            throw new RuntimeException("buffer数据为空");
        }
        //进行第一层基础协议转码
        ProtocolMicroDTO dto = new ProtocolMicroDTO();
        dto.setChannel(channelId);
        //数据包长
        int length = LvccByteFactory.ParseShort(buffer, 4) + 6;
        log.debug("基础协议解析->this package length is  {}", length);

        //设备 id长度
        int deviceLen = LvccByteFactory.ParseByte(buffer, 9);
        log.debug("基础协议解析->设备id长度：{}", deviceLen);
        String deviceId = LvccByteFactory.ParseString(buffer, 10, deviceLen);
        dto.setDeviceId(deviceId);

        int msgId = LvccByteFactory.ParseShort(buffer, 10 + deviceLen);
        dto.setNetOrder(msgId);
        log.debug("基础协议解析->消息id:{}", msgId);
        //设备指令
        int deviceCmd = LvccByteFactory.ParseShort(buffer, 12 + deviceLen);
        dto.setDeviceCmd(deviceCmd);
        //指令内容
        int bodyLen = length - deviceLen - 15;
        // 防止帧长声明超出实际buffer（TCP粘包截断场景）
        int maxBodyLen = buffer.length - 14 - deviceLen - 1;
        if (bodyLen < 0 || bodyLen > maxBodyLen) {
            log.warn("基础协议解析->bodyLen({})异常, 截断为maxBodyLen({})", bodyLen, maxBodyLen);
            bodyLen = Math.max(0, maxBodyLen);
        }
        byte[] body = new byte[bodyLen];
        System.arraycopy(buffer, 14 + deviceLen, body, 0, bodyLen);
        dto.setNetData(body);
        dto.setSource(LvccByteFactory.bytesToString(buffer));
        log.debug("基础协议解析->the byte body json : {}", new Gson().toJson(dto));
        return dto;
    }

    /**
     * 子协议解析
     *
     * @param dto 微电基础协议对象
     * @return 解析的子协议对象
     */
    @Override
    public Collection<? extends DeviceBasicDTO> parseProtocolData(ProtocolMicroDTO dto) {
        log.debug("解析的子协议对象开始 -> 指令：{}", String.format("%02x", dto.getDeviceCmd()));
        EnumCallCommand key = EnumCallCommand.getEnumByIndex(dto.getDeviceCmd() & 0xFF);
        if (HandlerMap.containsKey(key)) {
            try {
                // 这里进行子协议解析，返回结果集合
                Collection<DeviceBasicDTO> result = HandlerMap.get(key).handlerProtocolData(dto.getDeviceId(), dto.getNetData());
                return result != null ? result : Collections.emptyList();
            } catch (Exception e) {
                log.warn("解析子协议异常->设备:{} 指令:0x{} 错误:{}", dto.getDeviceId(), String.format("%04x", dto.getDeviceCmd()), e.getMessage());
                return Collections.emptyList();
            }

            /*
            if (basicDTO == null) {
                log.info("解析的子协议对象->无法解析或已经失败的数据 {} : 0x{}", dto.getDeviceId(), String.format("%02x", dto.getDeviceCmd()));
                return null;
            }
            log.info("解析的子协议对象->子协议解析成功->" + String.format("%02x", key.getCode()));
            basicDTO.setDataBuffer(dto);
            log.info("解析的子协议对象->" + new Gson().toJson(basicDTO));
            return Collections.singletonList(basicDTO);
            */

        } else {
            if (IgnoreCommands.contains(dto.getDeviceCmd() & 0xFF)) {
                // 设备对网关下发的应答（0x11），仅业务消费，不触发网关回包
                log.debug("解析的子协议对象->指令 {} 已忽略，跳过处理", String.format("0x%04x", dto.getDeviceCmd()));
                return Collections.emptyList();
            }
            log.info("解析的子协议对象->指令 {} - {} 未找到对应的子协议解析程序", String.format("%02x", key.getCode()), key.getTitle());
            return Collections.emptyList();
        }
    }

    @Override
    public byte[] AddBufPackage(ProtocolMicroDTO protocolMicroDTO, byte[] bytes) {
        return LvccProtocolFrame.packCommand(
                protocolMicroDTO.getDeviceId(),
                protocolMicroDTO.getNetOrder(),
                protocolMicroDTO.getDeviceCmd(),
                bytes);
    }

    /**
     * 数据检测
     *
     * @param in 数据流
     * @param x  位置
     * @return 是否验证通过
     */
    @Override
    public int BufferCheck(ByteBuf in, int x) {

        // 过滤首字节不为7E
        if ((in.getByte(x) & 0xFF) != 0x7E) {
            log.debug("buffer check index 1 != 0x7E data error, content:{} ASCII:{}", Integer.toHexString(0xFF & in.getByte(x)), LvccByteFactory.hex2Str(Integer.toHexString(0xFF & in.getByte(x))));
            return -1;
        }

        // 过滤第二个字节不为5D
        if ((in.getByte(x + 1) & 0xFF) != 0x5D) {
            log.debug("buffer check index 2 != 0x5D data error, content:{} ASCII:{}", Integer.toHexString(0xFF & in.getByte(x + 1)), LvccByteFactory.hex2Str(Integer.toHexString(0xFF & in.getByte(x + 1))));
            return -1;
        }

        // 过滤第三个字节不为7D
        if ((in.getByte(x + 2) & 0xFF) != 0x7D) {
            log.debug("buffer check index 3 != 0x7D data error, content:{} ASCII:{}", Integer.toHexString(0xFF & in.getByte(x + 2)), LvccByteFactory.hex2Str(Integer.toHexString(0xFF & in.getByte(x + 2))));
            return -1;
        }

        // 过滤第四个字节不为7F
        if ((in.getByte(x + 3) & 0xFF) != 0x7F) {
            log.debug("buffer check index 4 != 0x7F data error, content:{} ASCII:{}", Integer.toHexString(0xFF & in.getByte(x + 3)), LvccByteFactory.hex2Str(Integer.toHexString(0xFF & in.getByte(x + 3))));
            return -1;
        }

/*
//        // todo 异或校验值检验
//        int checkLen = in.readableBytes() - 7;
//        byte checkNum = LvccByteFactory.getXor(in, 6, checkLen);
//        if (checkNum != in.getByte(in.readableBytes() - 1)) return -1;
//        log.info("数据检验->异或校验值：{}", checkNum);
*/

        int pkgLen = in.getUnsignedShort(x + 4) + 6;
        int readableBytes = in.readableBytes() - x;
        if (pkgLen > readableBytes) {
            log.debug("数据检测->帧长({})超出可读字节数({}), 等待更多数据", pkgLen, readableBytes);
            return -1;
        }
        log.debug("数据检测->this package length is {}", pkgLen);
        return pkgLen;
    }

    /**
     * 网关标准回包。设备响应类指令（{@link #CheckCommandCallPix} 为 true）返回 {@code null} 不发包；
     * 其余委托 {@link LvccResponseDispatcher#encode}（含通用 ACK 兜底）。
     */
    @Override
    public byte[] BasicDTOEncode(DeviceBasicDTO dto) {
        // 设备对网关下发的应答（0x11）等：无需再回包
        if (CheckCommandCallPix(dto.getDataBuffer().getDeviceCmd() & 0xFF)) return null;

//        if (dto.getClass().isAnnotationPresent(RealtimeCall.class)) {
//            String identity = dto.getDataTypeIdentifier();
//            log.debug("基础协议标准返回->实时返回指令：{}", identity);
//            return RealtimeDTOEncode(dto);
//        }
//        log.debug("基础协议标准返回->开始");

        try {
            var bytes = LvccResponseDispatcher.encode(dto.getDeviceNo(), dto.getDataBuffer().getDeviceCmd(), dto.getDataBuffer().getNetOrder(), null);
            log.info("Data Rece : {}", dto.getDataBuffer().getSource());
            log.info("Data Call : {}", LvccByteFactory.bytesToString(bytes));
            return bytes;
        } catch (Exception e) {
            log.info("基础协议标准返回:异常 {}", e.getMessage());
            log.info("基础协议标准返回:接收 {}", new Gson().toJson(dto));
            return new byte[0];
        }
    }

//    /**
//     * 基础协议实时返回
//     *
//     * @param dto 微电基础协议对象
//     * @return 返回的网络流字节
//     */
//    @Override
//    public byte[] RealtimeDTOEncode(DeviceBasicDTO dto) {
//        return LvccResponseDispatcher.encodeRealtime(dto);
//    }

    /**
     * 由指令转换成字节
     *
     * @param req       请求符
     * @param order     序列
     * @param isPackage 是否打包
     * @return 跟据指令返回的字节组
     */
    @Override
    public byte[] ParseDTOCommand(DeviceCommandRequest req, int order, boolean isPackage) {
        String cmdPrefix = req.getCommand().split(":")[0];
        if (ParseCommandMap.containsKey(cmdPrefix)) {
            log.debug("由指令转换成字节->指令前缀：{}", cmdPrefix);
            // 获取操作指令
            int cmd = ParseCommandMap.get(cmdPrefix);
            log.debug("由指令转换成字节->指令：{}", String.format("%02x", cmd));
            if (!ParseClazzMap.containsKey(cmdPrefix)) return new byte[0];
            Class<? extends IProtocolOutput> outputClass = ParseClazzMap.get(cmdPrefix);
            try {
                IProtocolOutput output = outputClass.getConstructor().newInstance();
                output.inCommandString(req.getCommand());
                log.debug("由指令转换成字节->输入指令转换：{}", new Gson().toJson(output));
                byte[] cmdBytes = output.outCommandBytes();
                log.debug("由指令转换成字节->输出指令转换：{}", LvccByteFactory.bytesToString(cmdBytes));
                short msgId = (short) new Random().nextInt(Short.MAX_VALUE + 1);
                byte[] result = LvccProtocolFrame.packCommand(req.getDeviceId(), msgId, cmd, cmdBytes);
                log.info("由指令转换成字节->callback bytes：{}", LvccByteFactory.bytesToString(result));
                return result;
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                log.info("由指令转换成字节->指令转换异常 {}", e.getMessage());
            } catch (ProtocolException e) {
                log.info("协议层出错 {}", e.getMessage());
            }
        }

        return new byte[0];
    }

    /**
     * 是否跳过网关回包。
     *
     * @return {@code true} 表示无需向设备发送回执（{@link #BasicDTOEncode} 返回 null）
     */
    static boolean CheckCommandCallPix(int cmd) {
        return ParseCommandMap.containsValue(cmd) || IgnoreCommands.contains(cmd);
    }

    public static void main(String[] args) throws ProtocolException {
        String data = "7e 5d 7d 7f 00 22 00 01 10 0f 38 36 38 30 38 39 30 35 37 36 37 32 34 39 39 00 01 00 01 1d 01 1e 00 01 00 01 0a 2f 01 0a";
        data = "7e 5d 7d 7f 00 33 00 01 11 0f 38 36 33 30 36 39 30 35 39 33 39 33 34 38 39 03 d7 00 07 01 01 00 00 00 30 30 30 30 30 30 30 30 30 30 30 30 31 32 33 34 35 36 00 00 00 00 f9";
        data = "7e 5d 7d 7f 00 1b 00 01 12 0f 38 36 36 32 36 32 30 34 35 38 31 34 36 31 32 00 05 00 30 1a 00 00 04";
        data = "7e 5d 7d 7f 00 1d 00 01 10 0f 38 36 36 37 31 34 30 34 30 30 33 34 35 31 33 00 31 00 08 7d 0e 65 31 27 19";
        data = "7e 5d 7d 7f 00 1b 00 01 12 0f 38 36 36 32 36 32 30 34 35 38 31 34 36 31 32 00 05 00 30 1a 00 00 04";
        data = "7e 5d 7d 7f 00 1d 00 01 10 0f 38 36 36 37 31 34 30 34 30 30 33 34 35 31 33 00 31 00 08 7d 0e 65 31 27 19";
        data = "7e 5d 7d 7f 00 1d 00 01 10 0f 38 36 30 30 34 38 30 37 31 33 37 35 36 36 39 00 1a 00 08 cc 76 60 62 b8 30";
        data = "7e 5d 7d 7f 00 34 00 01 10 0f 38 36 30 30 34 38 30 37 31 33 37 35 36 36 39 00 58 00 09 01 02 58 30 31 38 30 33 33 32 34 39 33 38 34 38 39 31 36 37 38 37 32 cc 76 60 62 b8 2d";
        data = "7e 5d 7d 7f 00 1c 00 01 11 0f 38 36 36 37 31 34 30 34 30 30 33 34 35 31 33 f3 6a 00 28 01 01 00 01 91";
        data = "7e 5d 7d 7f 00 34 00 01 12 0f 38 36 36 37 31 34 30 34 30 30 33 34 35 31 33 00 35 00 09 05 00 32 32 30 32 30 30 34 31 36 31 31 34 37 35 38 33 36 37 33 39 39 7d 0e 65 31 27 25";
        data = "7e 5d 7d 7f 00 22 00 01 10 0f 38 36 38 30 38 39 30 35 37 36 37 32 34 39 39 00 01 00 01 1d 01 1e 00 01 00 01 0a 2f 01 0a";
        data = "7E-5D-7D-7F-00-5A-00-01-12-0F-38-36-32-39-36-36-30-37-33-33-32-31-38-34-35-04-06-00-48-16-0A-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-4E-5B-04-5B-04-5B-04-5B-04-5B-04-5B-04-5B-";
        data = "7E-5D-7D-7F-00-5A-00-01-12-0F-38-36-32-39-36-36-30-37-33-33-32-31-38-34-35-07-22-00-48-17-0A-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-00-59-10-59-10-59-74-59-74-59-74-59-74-59-10-59-10-59-10-59-10-00-01-00-00-00-00-00-02-00-04-06-7F-00-00-00-00-00-00-00-01-01-4A-00-01-61-";
        data = "7E-5D-7D-7F-00-18-00-01-12-0F-38-36-32-39-36-36-30-37-33-33-32-31-38-34-35-07-20-00-02-01";
        data = "7e 5d 7d 7f 00 1c 00 01 11 0f 38 36 34 39 37 39 30 37 35 36 35 34 37 36 34 06 a9 00 05 04 09 01 2d a9";
        //将16进制字符串转成字节数组
        byte[] buffer = ByteArrayUtil.hexString2ByteArray(data, " ");
        LvccProtoDecode dec = new LvccProtoDecode();
        ProtocolMicroDTO dto = dec.BasicDTODecode(buffer, "test");
        Collection<? extends DeviceBasicDTO> dbs = dec.parseProtocolData(dto);
        DeviceBasicDTO db = dbs.iterator().next();
        db.setDataBuffer(dto);
        var bytes = dec.BasicDTOEncode(db);
        log.debug("RealtimeDTOEncode -> callback bytes：{}", LvccByteFactory.bytesToString(bytes));
        DeviceCommandRequest re = new DeviceCommandRequest();
        re.setFlag(1);
        re.setDeviceId("863069059393489");
        re.setCommand("charge_start:123:1:1:60");
        dec.ParseDTOCommand(re, 0, false);
    }

    interface IProtocolHandler {
        Collection<DeviceBasicDTO> handlerProtocolData(String deviceNo, byte[] buffer);
    }

}
