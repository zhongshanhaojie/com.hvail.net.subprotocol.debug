package com.choco.net.xiaoya.lvcc.response;

import com.choco.net.xiaoya.lvcc.utils.LvccByteFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * 驴充充协议封包：帧头 + 指令内容 + 异或校验
 */
public final class LvccProtocolFrame {

    private static final Logger log = LoggerFactory.getLogger(LvccProtocolFrame.class);
    private static final byte[] FRAME_HEADER = {0x7e, 0x5d, 0x7d, 0x7f};

    private LvccProtocolFrame() {
    }

    /** 设备上行后的网关响应（通讯标识 0x01） */
    public static byte[] packResponse(String deviceNo, int cmd, int msgId, byte[] cmdContent) {
        return pack(deviceNo, cmd, msgId, (byte) 0x01, cmdContent);
    }

    /** 网关主动下发指令（通讯标识 0x00） */
    public static byte[] packCommand(String deviceNo, int msgId, int cmd, byte[] cmdContent) {
        return pack(deviceNo, cmd, msgId, (byte) 0x00, cmdContent);
    }

    private static byte[] pack(String deviceNo, int cmd, int msgId, byte commFlag, byte[] cmdContent) {
        if (cmdContent == null) {
            cmdContent = new byte[0];
        }
        int deviceLen = deviceNo.length();
        int contentStart = 14 + deviceLen;
        int totalLen = contentStart + cmdContent.length + 1;
        byte[] result = new byte[totalLen];

        System.arraycopy(FRAME_HEADER, 0, result, 0, FRAME_HEADER.length);
        byte[] lenBytes = LvccByteFactory.ShortToBytes(totalLen - 6);
        result[4] = lenBytes[0];
        result[5] = lenBytes[1];
        result[6] = 0x00;
        result[7] = 0x01;
        result[8] = commFlag;
        result[9] = (byte) (deviceLen & 0xFF);
        System.arraycopy(deviceNo.getBytes(StandardCharsets.US_ASCII), 0, result, 10, deviceLen);

        byte[] msgIdBytes = LvccByteFactory.ShortToBytes(msgId);
        result[contentStart - 4] = msgIdBytes[0];
        result[contentStart - 3] = msgIdBytes[1];
        byte[] cmdBytes = LvccByteFactory.ShortToBytes(cmd);
        result[contentStart - 2] = cmdBytes[0];
        result[contentStart - 1] = cmdBytes[1];

        System.arraycopy(cmdContent, 0, result, contentStart, cmdContent.length);
        result[totalLen - 1] = LvccByteFactory.getXor(result, 6, totalLen - 7);
        log.debug("pack comm=0x{} cmd=0x{} bytes: {}", Integer.toHexString(commFlag & 0xFF),
                Integer.toHexString(cmd), LvccByteFactory.bytesToString(result));
        return result;
    }
}
