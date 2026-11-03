package com.choco.net.xiaoya.lvcc.vo;


import com.choco.net.basic.ex.ProtocolException;

/****
 * @file
 * @author hvail
 * @version 1.0.0
 * @date 2022/1/8 12:45
 * @note
 */
public interface IProtocolOutput {

    void inCommandString(String command) throws ProtocolException;

    byte[] outCommandBytes();

}
