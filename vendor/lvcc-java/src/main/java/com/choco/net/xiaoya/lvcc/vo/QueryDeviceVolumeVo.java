package com.choco.net.xiaoya.lvcc.vo;

import com.choco.net.basic.ex.DeviceCommandArgsCountException;
import com.choco.net.basic.ex.ProtocolException;

/**
 * @Author: tanning
 * @Date: 2024/06/20/14:35
 * @Description:查询喇叭音量
 */
public class QueryDeviceVolumeVo implements IProtocolOutput{
    @Override
    public void inCommandString(String command) throws ProtocolException {
        String[] args = command.split(":");
        if (args.length != 1) throw new DeviceCommandArgsCountException("查询喇叭音量 参数数量错误");
    }

    @Override
    public byte[] outCommandBytes() {
        return new byte[0];
    }
}
