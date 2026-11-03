package com.choco.net.xiaoya.lvcc.vo;

import com.choco.net.basic.ex.DeviceCommandArgsCountException;
import com.choco.net.basic.ex.ProtocolException;

/**
 * @Author: tanning
 * @Date: 2024/06/13/14:39
 * @Description:查询设备信息
 */
public class QueryDeviceInfoVo implements IProtocolOutput{
    @Override
    public void inCommandString(String command) throws ProtocolException {
        String[] args = command.split(":");
        if (args.length != 1) throw new DeviceCommandArgsCountException("查询设备信息 参数数量错误");
    }

    @Override
    public byte[] outCommandBytes() {
        return new byte[0];
    }
}
