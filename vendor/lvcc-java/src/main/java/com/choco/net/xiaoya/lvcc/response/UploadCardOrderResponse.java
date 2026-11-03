package com.choco.net.xiaoya.lvcc.response;

import com.choco.net.xiaoya.lvcc.models.ChargePileCard;

/**
 * @Author: tanning
 * @Date: 2024/06/15/14:22
 * @Description:上报刷卡订单
 */
public final class UploadCardOrderResponse {

    private UploadCardOrderResponse() {
    }

    public static byte[] buildContent(ChargePileCard chargePileCard) {
        if (chargePileCard == null) {
            return AckResponse.success();
        }
        return new byte[]{(byte) chargePileCard.getResult()};
    }
}
