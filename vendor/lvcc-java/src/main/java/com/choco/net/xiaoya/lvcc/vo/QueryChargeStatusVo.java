package com.choco.net.xiaoya.lvcc.vo;

import com.choco.net.basic.ex.DeviceCommandArgsCountException;
import com.choco.net.basic.ex.ProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author: tanning
 * @Date: 2024/06/13/14:24
 * @Description: 查询充电状态
 */
public class QueryChargeStatusVo implements IProtocolOutput {
    private Logger log = LoggerFactory.getLogger(QueryChargeStatusVo.class);
    //插座或端口
    private int port;

    @Override
    public void inCommandString(String command) throws ProtocolException {
        String[] args = command.split(":");
        if (args.length != 2) throw new DeviceCommandArgsCountException("查询充电状态 参数数量错误");
        this.port = args[1].equals("0") ? Integer.parseInt(args[1]) : Integer.parseInt(args[1]) - 1;
    }

    @Override
    public byte[] outCommandBytes() {
        byte[] result = new byte[1];
        result[0] = (byte) this.port;
        return result;
    }
}
