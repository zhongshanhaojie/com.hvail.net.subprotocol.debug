package com.choco.net.xiaoya.lvcc.vo;

import com.choco.net.basic.ex.DeviceCommandArgsCountException;
import com.choco.net.basic.ex.ProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author: tanning
 * @Date: 2024/06/14/10:11
 * @Description: 设备重启
 */
public class DeviceRestartVo implements IProtocolOutput {
    private final static Logger log = LoggerFactory.getLogger(DeviceRestartVo.class);


    @Override
    public void inCommandString(String command) throws ProtocolException {
        String[] args = command.split(":");
        if (args.length != 1) throw new DeviceCommandArgsCountException("设备重启 参数数量错误");
    }

    @Override
    public byte[] outCommandBytes() {
        return new byte[0];
    }
}
