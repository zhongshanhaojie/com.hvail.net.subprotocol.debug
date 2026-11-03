package com.choco.net.xiaoya.lvcc.vo;

import com.choco.net.basic.ex.DeviceCommandArgsCountException;
import com.choco.net.basic.ex.ProtocolException;

/**
 * @Author: tanning
 * @Date: 2024/06/24/10:07
 * @Description:查询设备配置参数
 */
public class QueryDeviceArgsVo implements IProtocolOutput{

    @Override
    public void inCommandString(String command) throws ProtocolException {
        String[] args = command.split(":");
        if (args.length != 1) throw new DeviceCommandArgsCountException("查询设备配置参数 参数数量错误");
    }

    @Override
    public byte[] outCommandBytes() {
        return new byte[0];
    }
}
