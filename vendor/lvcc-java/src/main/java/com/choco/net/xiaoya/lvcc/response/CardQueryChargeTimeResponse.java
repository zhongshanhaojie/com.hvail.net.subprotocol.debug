package com.choco.net.xiaoya.lvcc.response;

import com.choco.net.xiaoya.lvcc.models.ChargePileCard;
import com.choco.net.xiaoya.lvcc.utils.LvccByteFactory;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * @Author: tanning
 * @Date: 2024/06/13/13:58
 * @Description: 刷卡查询可充电时间：设备->网关(响应)
 */
public final class CardQueryChargeTimeResponse {

    private static final Logger log = LoggerFactory.getLogger(CardQueryChargeTimeResponse.class);
    private static final Gson gson = new Gson();

    private CardQueryChargeTimeResponse() {
    }

    public static byte[] buildContent(ChargePileCard chargePileCard) {
        if (chargePileCard == null) {
            return AckResponse.failure();
        }
        chargePileCard.setResult(4);
        log.debug("刷卡查询可充电时间 指令内容,入参：{}", gson.toJson(chargePileCard));

        byte[] cmdContent = new byte[39];
        cmdContent[0] = (byte) chargePileCard.getResult();

        byte[] chargeTimeBytes = LvccByteFactory.ShortToBytes(chargePileCard.getChargeTime());
        cmdContent[1] = chargeTimeBytes[0];
        cmdContent[2] = chargeTimeBytes[1];
        cmdContent[3] = 1;

        int totalAmount = chargePileCard.getTotalBalance().divide(BigDecimal.TEN).intValue();
        System.arraycopy(LvccByteFactory.int2ByteArray(totalAmount), 0, cmdContent, 4, 4);

        byte[] minPowerBytes = LvccByteFactory.ShortToBytes(1);
        cmdContent[8] = minPowerBytes[0];
        cmdContent[9] = minPowerBytes[1];

        byte[] maxPowerBytes = LvccByteFactory.ShortToBytes(800);
        cmdContent[10] = maxPowerBytes[0];
        cmdContent[11] = maxPowerBytes[1];

        byte[] noLoadWaitTimeBytes = LvccByteFactory.ShortToBytes(30);
        cmdContent[12] = noLoadWaitTimeBytes[0];
        cmdContent[13] = noLoadWaitTimeBytes[1];

        byte[] fullWaitTimeBytes = LvccByteFactory.ShortToBytes(30);
        cmdContent[14] = fullWaitTimeBytes[0];
        cmdContent[15] = fullWaitTimeBytes[1];

        String orderNo = String.format("%020d", new BigInteger(chargePileCard.getOrderNo()));
        System.arraycopy(orderNo.getBytes(StandardCharsets.US_ASCII), 0, cmdContent, 16, orderNo.length());

        cmdContent[36] = 1;
        cmdContent[37] = 1;
        cmdContent[38] = 1;
        return cmdContent;
    }
}
