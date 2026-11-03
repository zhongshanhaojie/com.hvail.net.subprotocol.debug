package com.choco.net.xiaoya.lvcc.enums;

import com.choco.net.basic.dto.DeviceDynamicPropsDTO;
import com.choco.net.basic.pojo.SUPPORT_TYPE;
import com.choco.net.basic.protocol.DeviceSupportProps;
import com.choco.net.basic.utils.ByteArrayUtil;

import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * @author hvail
 * @version 1.0.0
 * @file EnumProtocolField.java
 * @date 2022/4/15 17:40
 * @note
 */
public enum EnumProtocolField {

    /****
     * 设备注册
     ***/
    DeviceRegister(0x0114, "DeviceRegister", SUPPORT_TYPE.BYTE, 0x04),
    NULL(0xFFFF, "NotHandlerData", SUPPORT_TYPE.STRING);

    private final static SimpleDateFormat bcdFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    private int code;
    private String fieldName;
    private String type;
    private int defLen;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getDefLen() {
        return defLen;
    }

    public void setDefLen(int defLen) {
        this.defLen = defLen;
    }

    EnumProtocolField(int code, String field, String type) {
        this.code = code;
        this.fieldName = field;
        this.type = type;
    }

    EnumProtocolField(int code, String field, String type, int len) {
        this.code = code;
        this.fieldName = field;
        this.type = type;
        this.defLen = len;
    }


    public byte[] parseToBytes(byte port, String msg, int len) {
        byte[] result = new byte[len];
        result[0] = (byte) (len & 0xFF);
        result[1] = (byte) (this.code & 0xFF);
        result[2] = (byte) ((this.code >> 8) & 0xFF);
        result[3] = port;
        byte[] data;
        switch (this.type) {
            case SUPPORT_TYPE.HEX:
                data = ByteArrayUtil.hexString2ByteArray(msg);
                break;
            case SUPPORT_TYPE.STRING:
                data = msg.getBytes(StandardCharsets.US_ASCII);
                break;
            default:
                data = new byte[len - 4];
                break;
        }
        if (data != null) {
            System.arraycopy(data, 0, result, 4, Math.min(data.length, (len - 4)));
        }
        return result;
    }

    /****
     * 将字符串转为协议字节组
     * @param msg 字符串
     * @param len 此条总长度
     * @return 所有字节码
     ***/
    public byte[] parseToBytes(String msg, int len) {
        byte[] result = new byte[len];
        result[0] = (byte) (len & 0xFF);
        result[1] = (byte) (this.code & 0xFF);
        result[2] = (byte) ((this.code >> 8) & 0xFF);
        byte[] data;
        switch (this.type) {
            case SUPPORT_TYPE.HEX:
                data = ByteArrayUtil.hexString2ByteArray(msg);
                break;
            case SUPPORT_TYPE.STRING:
                data = msg.getBytes(StandardCharsets.US_ASCII);
                break;
            default:
                data = new byte[len - 3];
                break;
        }
        if (data != null) {
            System.arraycopy(data, 0, result, 3, Math.min(data.length, (len - 3)));
        }
        return result;
    }

    public byte[] getDefaultBytes() {
        byte[] result = new byte[this.defLen];
        result[0] = (byte) (this.defLen & 0xFF);
        result[1] = (byte) (this.code & 0xFF);
        result[2] = (byte) ((this.code >> 8) & 0xFF);
        return result;
    }

    /****
     * 将字符串转为协议字节组
     * @param msg 字符串
     * @return 所有字节码
     ***/
    public byte[] parseToBytes(String msg) {
        return parseToBytes(msg, this.defLen);
    }

    /****
     * 将字节状态码转为协议字节组
     * @param state 状态码
     * @return 所有字节码
     ***/
    public byte[] parseToBytes(byte state) {
        byte[] result = getDefaultBytes();
        result[3] = (byte) (state & 0xFF);
        return result;
    }

    /****
     * 将Short状态码转为协议字节组
     * @param state 四字节数字
     * @return 所有字节码
     ***/
    public byte[] parseToBytes(int state) {
        byte[] result = getDefaultBytes();
        result[3] = (byte) (state & 0xFF);
        result[4] = (byte) ((state >> 8) & 0xFF);
        result[5] = (byte) ((state >> 16) & 0xFF);
        result[6] = (byte) ((state >> 24) & 0xFF);
        return result;
    }

    /****
     * 将Short状态码转为协议字节组
     * @param state 双字节数字
     * @return 所有字节码
     ***/
    public byte[] parseToBytes(short state) {
        byte[] result = getDefaultBytes();
        result[3] = (byte) (state & 0xFF);
        result[4] = (byte) ((state >> 8) & 0xFF);
        return result;
    }

    public byte[] parseToBytes(Byte[] buf, int len) {
        byte[] result = getDefaultBytes();
        for (int i = 0; i < len; i++) {
            if (i + 3 >= result.length) break;
            result[i + 3] = buf[i];
        }
        return result;
    }

    public byte[] parseToBytes(DeviceDynamicPropsDTO.DeviceDynamicPropsItem item) {
        switch (item.getValType()) {
            case SUPPORT_TYPE.HEX:
            case SUPPORT_TYPE.STRING:
                return parseToBytes((String) item.getPropsValue(), this.defLen);
            case SUPPORT_TYPE.BYTE:
                return parseToBytes((byte) item.getPropsValue());
            case SUPPORT_TYPE.INTEGER:
                return parseToBytes((int) item.getPropsValue());
            case SUPPORT_TYPE.SHORT:
                return parseToBytes((short) item.getPropsValue());
            case SUPPORT_TYPE.DATE_BCD:
                return parseToBCDBytes(bcdFormat.parse(item.getPropsValue().toString(), new ParsePosition(0)));
            default:
                return new byte[0];
        }
    }

    /****
     * 将时间格式转为BCD模式
     * @param date 日期时间
     * @return 所有字节码
     ***/
    public byte[] parseToBCDBytes(Date date) {
        String dtStr = bcdFormat.format(date);
        return ByteArrayUtil.hexString2ByteArray(dtStr);
    }

    public DeviceDynamicPropsDTO.DeviceDynamicPropsItem getItem(String str) {
        DeviceDynamicPropsDTO.DeviceDynamicPropsItem item = new DeviceDynamicPropsDTO.DeviceDynamicPropsItem(this.fieldName, this.type);
        try {
            switch (this.type) {
                case SUPPORT_TYPE.STRING:
                case SUPPORT_TYPE.DATE_BCD:
                case SUPPORT_TYPE.HEX:
                    item.setPropsValue(str);
                    break;
                case SUPPORT_TYPE.INTEGER:
                case SUPPORT_TYPE.SHORT:
                    item.setPropsValue(Integer.parseInt(str));
                    break;
                case SUPPORT_TYPE.BYTE:
                    item.setPropsValue(Byte.valueOf(str));
                    break;
            }
        } catch (Exception e) {
            System.out.printf("DeviceDynamicPropsItem String : %s Error \r\n", str);
        }
        return item;
    }

    public DeviceDynamicPropsDTO.DeviceDynamicPropsItem getItem(byte[] bytes) {
        DeviceDynamicPropsDTO.DeviceDynamicPropsItem item = new DeviceDynamicPropsDTO.DeviceDynamicPropsItem(this.fieldName, this.type);
        switch (this.type) {
            case SUPPORT_TYPE.STRING:
                item.setPropsValue(new String(bytes, 3, bytes.length - 3, StandardCharsets.US_ASCII).replaceAll("\\u0000", ""));
                break;
            case SUPPORT_TYPE.HEX:
                item.setPropsValue(ByteArrayUtil.byteArray2HexString(bytes, 3, bytes.length - 3));
                break;
            case SUPPORT_TYPE.DATE_BCD:
                String bcdStr = ByteArrayUtil.byteArray2HexString(bytes, 3, 7);
                item.setPropsValue(bcdFormat.parse(bcdStr, new ParsePosition(0)));
                break;
            case SUPPORT_TYPE.INTEGER:
                item.setPropsValue(ByteArrayUtil.byteArray2IntLE(bytes, 3));
                break;
            case SUPPORT_TYPE.SHORT:
                item.setPropsValue(ByteArrayUtil.byteArray2ShortLE(bytes, 3));
                break;
            case SUPPORT_TYPE.BYTE:
                item.setPropsValue(bytes[3] & 0xFF);
                break;
        }
        item.setProCode((bytes[1]) + (bytes[2] << 8));
        return item;
    }

    public static EnumProtocolField getEnumProtocolField(int index) {
        return Arrays.stream(values()).filter(e -> (e.code == index)).findAny().orElse(EnumProtocolField.NULL);
    }

    public static EnumProtocolField getEnumProtocolField(String field) {
        return Arrays.stream(values()).filter(e -> (e.getFieldName().equals(field)))
                .findAny()
                .orElse(EnumProtocolField.NULL);
    }

}
