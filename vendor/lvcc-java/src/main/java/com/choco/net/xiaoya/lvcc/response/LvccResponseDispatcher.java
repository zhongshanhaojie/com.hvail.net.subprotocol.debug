package com.choco.net.xiaoya.lvcc.response;

import com.choco.net.basic.dto.DeviceBasicDTO;
import com.choco.net.basic.dto.DeviceCardGetBalanceDTO;
import com.choco.net.basic.dto.DeviceRFICChargeUpDTO;
import com.choco.net.basic.utils.AppHttpFactory;
import com.choco.net.xiaoya.lvcc.models.ChargePileCard;
import com.choco.net.xiaoya.lvcc.models.ChargePileCardRequest;
import com.choco.net.xiaoya.lvcc.models.DeviceNetHeartLvccModel;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 驴充充设备上行报文的网关响应分发。
 * <p>
 * 协议通讯标识（帧内第 9 字节低四位）决定网关是否回包：
 * <ul>
 *   <li>{@code 0x11} 设备响应（对网关下发的应答）→ 网关不再回包，见 {@code LvccProtoDecode.IgnoreCommands}</li>
 *   <li>{@code 0x12} 设备主动上报 → 网关须回 {@code 0x01}，多数仅需 {@code 结果=1}</li>
 *   <li>{@code 0x10} 设备请求（如刷卡）→ 网关须回业务数据</li>
 * </ul>
 * 本类仅负责「需要网关回包」时的组帧；未列入 {@link #ENCODER_MAP} 的指令走 {@link AckResponse} 兜底。
 * <p>
 * 协议中尚有大量主动上报未接入（如 {@code 0x0034/0x0044/0x0050/0x0052} 等），
 * 告警相关已接入：{@code 0x0016} 故障上报、{@code 0x0038} 故障复位、{@code 0x0057} 换电柜故障仓提取。
 * 待 {@code HandlerMap} 增加解析后会自动获得通用 ACK。
 */
public final class LvccResponseDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LvccResponseDispatcher.class);
    private static final Gson gson = new Gson();

    private static final String QUERY_CARD_CHARGE_TIME_URL = System.getenv("charge_pile_card_url") == null
            ? "http://192.168.1.17:801/order/chargePile/query/card/charge/time"
            : System.getenv("charge_pile_card_url");
    private static final String UPLOAD_CARD_ORDER_URL = System.getenv("upload_card_order_url") == null
            ? "http://192.168.1.17:801/order/chargePile/upload/card/order"
            : System.getenv("upload_card_order_url");

    /** 需专用响应体的指令；其余走 {@link #encode} 中的通用 ACK 兜底 */
    private static final Map<Integer, IResponseEncoder> ENCODER_MAP = new HashMap<>();

    static {
        // --- 设备发起、网关须回专用数据 ---
        // 0x0001 注册：上电注册，响应含服务器时间与计费参数（非周期主动上报）
        ENCODER_MAP.put(0x0001, (d, c, m, b) -> LvccProtocolFrame.packResponse(d, c, m, DeviceRegisterResponse.buildContent()));
        // 0x0002 心跳：主动上报 0x12，响应 4 字节 Unix 时间戳（秒）
        ENCODER_MAP.put(0x0002, (d, c, m, b) -> LvccProtocolFrame.packResponse(d, c, m, DeviceHeatResponse.buildContent()));
        // 0x0008 刷卡查询：设备请求 0x10，须 HTTP 查卡后回充电时间/余额等
        ENCODER_MAP.put(0x0008, (d, c, m, b) -> LvccProtocolFrame.packResponse(d, c, m,
                CardQueryChargeTimeResponse.buildContent(b instanceof ChargePileCard card ? card : null)));
        // 0x0009 上报刷卡订单：设备请求 0x10，响应 结果=1/2
        ENCODER_MAP.put(0x0009, (d, c, m, b) -> LvccProtocolFrame.packResponse(d, c, m,
                UploadCardOrderResponse.buildContent(b instanceof ChargePileCard card ? card : null)));

        // --- 设备主动上报 / 事件上报，协议仅要求 结果=1（通用 ACK）---
        // 0x0010 充电结束
        ENCODER_MAP.put(0x0010, (d, c, m, b) -> LvccProtocolFrame.packResponse(d, c, m, AckResponse.success()));
        // 【审核点-告警】0x0016 故障上报（主动上报 0x12）→ ACK 结果=1
        ENCODER_MAP.put(0x0016, (d, c, m, b) -> LvccProtocolFrame.packResponse(d, c, m, AckResponse.success()));
        // 0x0021 插座状态更改上报（主动上报 0x12）
        ENCODER_MAP.put(0x0021, (d, c, m, b) -> LvccProtocolFrame.packResponse(d, c, m, AckResponse.success()));
        // 0x0030 充电状态定时上报（主动上报 0x12）
        ENCODER_MAP.put(0x0030, (d, c, m, b) -> LvccProtocolFrame.packResponse(d, c, m, AckResponse.success()));
        // 0x0048 实时充电状态定时上报（主动上报 0x12，0x0030 扩展版）
        ENCODER_MAP.put(0x0048, (d, c, m, b) -> LvccProtocolFrame.packResponse(d, c, m, AckResponse.success()));
        // 【审核点-告警】0x0057 换电柜状态主动上报 → ACK 结果=1
        ENCODER_MAP.put(0x0057, (d, c, m, b) -> LvccProtocolFrame.packResponse(d, c, m, AckResponse.success()));
    }

    private LvccResponseDispatcher() {
    }

    /**
     * 按指令组网关响应帧（通讯标识 {@code 0x01}）。
     * 命中 {@link #ENCODER_MAP} 用专用编码器；否则回包体 {@code [0x01]} 表示成功。
     */
    public static byte[] encode(String deviceNo, int cmd, int msgId, Object bean) {
        IResponseEncoder encoder = ENCODER_MAP.get(cmd);
        if (encoder != null) {
            return encoder.encode(deviceNo, cmd, msgId, bean);
        }
        log.debug("cmd 0x{} 无专用编码器，使用通用 ACK", Integer.toHexString(cmd));
        return LvccProtocolFrame.packResponse(deviceNo, cmd, msgId, AckResponse.success());
    }

//    /**
//     * 实时回包（需 HTTP 等外部数据时使用）。
//     * 当前主流程走 {@link com.choco.net.xiaoya.lvcc.LvccProtoDecode#BasicDTOEncode}，本方法保留供恢复 RealtimeDTOEncode 时调用。
//     */
//    public static byte[] encodeRealtime(DeviceBasicDTO dto) {
//        if (dto instanceof DeviceNetHeartLvccModel) {
//            log.debug("实时->基础协议实时返回->心跳包");
//            return LvccProtocolFrame.packResponse(
//                    dto.getDeviceNo(), dto.getDataBuffer().getDeviceCmd(), dto.getDataBuffer().getNetOrder(),
//                    DeviceHeatResponse.buildContent());
//        }
//        if (dto instanceof DeviceCardGetBalanceDTO obj) {
//            log.debug("实时->基础协议实时返回->刷卡查询返回可充电时间 {} 查询余额", obj.getCardNo());
//            try {
//                ChargePileCardRequest req = new ChargePileCardRequest(obj);
//                ChargePileCard chargePileCard = AppHttpFactory._postSingle(QUERY_CARD_CHARGE_TIME_URL, req, ChargePileCard.class);
//                log.debug("查询 {} 余额信息 {}", obj.getCardNo(), gson.toJson(chargePileCard));
//                return encode(dto.getDeviceNo(), dto.getDataBuffer().getDeviceCmd(), dto.getDataBuffer().getNetOrder(), chargePileCard);
//            } catch (Exception e) {
//                log.warn("刷卡查询返回可充电时间失败: {}", e.getMessage());
//                return LvccProtocolFrame.packResponse(
//                        dto.getDeviceNo(), dto.getDataBuffer().getDeviceCmd(), dto.getDataBuffer().getNetOrder(),
//                        AckResponse.failure());
//            }
//        } else if (dto instanceof DeviceRFICChargeUpDTO obj) {
//            log.debug("实时->基础协议实时返回->上报刷卡订单 {} 查询余额", obj.getCardNo());
//            try {
//                ChargePileCardRequest req = new ChargePileCardRequest(obj);
//                log.debug("实时->基础协议实时返回->上报刷卡订单 {} 查询余额,传达参数：{}", obj.getCardNo(), gson.toJson(req));
//                ChargePileCard chargePileCard = AppHttpFactory._postSingle(UPLOAD_CARD_ORDER_URL, req, ChargePileCard.class);
//                log.debug("查询 {} 余额信息 {}", obj.getCardNo(), gson.toJson(chargePileCard));
//                return encode(dto.getDeviceNo(), dto.getDataBuffer().getDeviceCmd(), dto.getDataBuffer().getNetOrder(), chargePileCard);
//            } catch (Exception e) {
//                log.warn("上报刷卡订单失败: {}", e.getMessage());
//                return LvccProtocolFrame.packResponse(
//                        dto.getDeviceNo(), dto.getDataBuffer().getDeviceCmd(), dto.getDataBuffer().getNetOrder(),
//                        AckResponse.success());
//            }
//        }
//        return new byte[0];
//    }

    @FunctionalInterface
    interface IResponseEncoder {
        byte[] encode(String deviceNo, int cmd, int msgId, Object bean);
    }
}
