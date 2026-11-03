package com.choco.net.xiaoya.lvcc.utils;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * @file
 * @author hvail
 * @version 1.0.0
 * @date 2024/5/25 11:13
 * @note 数据类型说明
 * _____________________________________
 * | String | N个字符使用ASCII码表示      |
 * | Long   | 8个字节，高位在前，低0位在后   |
 * | Int    | 4个字节，高位在前，低位在后   |
 * | Short  | 2个字节，高位在前，低位在后   |
 * | Byte   | 1个字节，高位在前，低位在后   |
 * -------------------------------------
 */
public final class LvccByteFactory {

    public static String ParseString(byte[] buffer, int pos, int len) {
        return new String(buffer, pos, len, StandardCharsets.US_ASCII);
    }

    /****
     * 字节数组转 HexString
     ***/
    public static String byteArray2HexString(byte[] array, int pos, int len, String split) {
        StringBuilder builder = new StringBuilder();
        int end = pos + len;
        if (end > array.length) end = array.length;
        for (int i = pos; i < end; i++) {
            byte b = array[i];
            String s = Integer.toHexString(b & 0xff);
            if (s.length() < 2) {
                builder.append("0");
            }
            builder.append(s);
            builder.append(split);
        }
        return builder.toString().toUpperCase();
    }
    /****
     * 字节数组转 HexString
     ***/
    public static String ParseHexString(byte[] array, int pos, int len) {
        return byteArray2HexString(array, pos, len, "");
    }


    private static Number ParseNumber(byte[] buffer, int pos, int len) {
        if (buffer.length < (pos + len)) return -1L;
        long result = 0L;
        int index = 0;
        while (index < len) {
            int current = pos + index;
            result <<= 8;
            result += buffer[current] & 0xFF;
            index++;
        }
        return result;
    }

    public static long ParseLong(byte[] buffer, int pos) {
        // 长度8个字节
        if (buffer.length < pos + 8) return -1L;
        return ParseNumber(buffer, pos, 8).longValue();
    }

    public static int ParseInt(byte[] buffer, int pos) {
        // 长度8个字节
        if (buffer.length < pos + 4) return -1;
        return ParseNumber(buffer, pos, 4).intValue();
    }

    public static int ParseShort(byte[] buffer, int pos) {
        // 长度8个字节
        if (buffer.length < pos + 2) return -1;
        return ParseNumber(buffer, pos, 2).intValue();
    }

    public static int ParseByte(byte[] buffer, int pos) {
        // 长度8个字节
        if (buffer.length < pos + 1) return -1;
        return buffer[pos] & 0xFF;
    }

    public static byte[] ShortToBytes(int x) {
        byte[] res = new byte[2];
        res[0] = (byte) ((x >> 8) & 0xFF);
        res[1] = (byte) (x & 0xFF);
        return res;
    }

    /**
     * int 转字节数组
     */
    public static byte[] int2ByteArray(long s) {
        byte[] array = new byte[4];
        for (int i = 0; i < array.length; i++) {
            array[i] = (byte) ((s >> (i * 8)) & 0xFF);
        }
        System.out.println("入test:" + s);
        System.out.println("出test:" + bytesToString(array));
        return array;
    }

    public static byte[] int2ByteArray(int s) {

        byte[] array = new byte[4];

        for (int i = 0; i < array.length; i++) {
            array[array.length - 1 - i] = (byte) (s >> (i * 8));
        }
        return array;
    }


    public static byte[] long2ByteArray(long l) {

        byte[] array = new byte[8];

        for (int i = 0; i < array.length; i++) {
            array[array.length - 1 - i] = (byte) (l >> (i * 8));
        }
        return array;
    }

    public static byte[] hexString2ByteArray(String hexString) {

        // 两个十六进制字符一个 byte，单数则有误
        if (hexString.length() % 2 != 0) {
            return new byte[0];
        }

        byte[] array = new byte[hexString.length() / 2];

        int value = 0;
        for (int i = 0; i < hexString.length(); i++) {
            char s = hexString.charAt(i);
            // 前半个字节
            if (i % 2 == 0) {
                value = Integer.parseInt(String.valueOf(s), 16) * 16;
            } else {
                // 后半个字节
                value += Integer.parseInt(String.valueOf(s), 16);
                array[i / 2] = (byte) value;
                value = 0;
            }
        }
        return array;
    }

    public static String stringToHexString(String s) {
        String str = "";
        for (int i = 0; i < s.length(); i++) {
            int ch = s.charAt(i);
            String s4 = Integer.toHexString(ch);
            str = str + s4;
        }
        return str;
    }
    //16进制hex 转ASIC
    public static String hex2Str(String hex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length() - 1; i += 2) {
            String h = hex.substring(i, (i + 2));
            int decimal = Integer.parseInt(h, 16);
            sb.append((char) decimal);
        }
        return sb.toString();
    }

        public static String bytesToString(byte[] buffer) {
        StringBuilder sb = new StringBuilder();
        for (byte b : buffer) {
            sb.append(String.format("%02x ", b));
        }
        return sb.toString();
    }

    public static byte getXor(ByteBuf buffer, int start, int len) {
        byte temp = buffer.getByte(start);
        for (int i = start; i < start + len; i++) {
            temp ^= buffer.getByte(i);
        }
        return temp;
    }

    public static byte getXor(byte[] buffer, int start, int len) {
        byte temp = buffer[start];
        for (int i = start ; i < start + len; i++) {
            temp ^= buffer[i];
        }
        return temp;
    }

}
