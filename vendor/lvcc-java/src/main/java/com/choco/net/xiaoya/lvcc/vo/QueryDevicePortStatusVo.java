package com.choco.net.xiaoya.lvcc.vo;

import com.choco.net.basic.ex.DeviceCommandArgsCountException;
import com.choco.net.basic.ex.ProtocolException;

/**
 * @Author: tanning
 * @Date: 2024/06/14/16:58
 * @Description: 查询设备插座状态
 */
public class QueryDevicePortStatusVo implements IProtocolOutput{

    @Override
    public void inCommandString(String command) throws ProtocolException {
        String[] args = command.split(":");
        if (args.length != 1) throw new DeviceCommandArgsCountException("查询设备插座状态 参数数量错误");

    }

    @Override
    public byte[] outCommandBytes() {
        return new byte[0];
    }
}
