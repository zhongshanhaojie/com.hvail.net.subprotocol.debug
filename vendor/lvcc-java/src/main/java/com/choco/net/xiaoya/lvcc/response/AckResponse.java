package com.choco.net.xiaoya.lvcc.response;

/**
 * 协议通用 ACK（Lvcc-Protocol.md 中「结果 byte：1 成功 / 2 失败」）。
 * 用于主动上报类指令的网关响应，以及 {@link LvccResponseDispatcher} 的兜底回包。
 */
public final class AckResponse {

    private AckResponse() {
    }

    public static byte[] success() {
        return new byte[]{1};
    }

    public static byte[] failure() {
        return new byte[]{2};
    }
}
