package com.choco.net.xiaoya.lvcc.vo;

import com.choco.net.basic.ex.DeviceCommandArgsCountException;
import com.choco.net.basic.ex.ProtocolException;

/**
 * @Author: tanning
 * @Date: 2024/06/07/19:59
 * @Description:停止充电
 */
public class StopChargeVo implements IProtocolOutput{
    //插座或端口
    private int port;

    @Override
    public void inCommandString(String command) throws ProtocolException {
        String[] args = command.split(":");
        if (args.length != 3) throw new DeviceCommandArgsCountException("停止充电 参数数量错误");
        this.port=args[2].equals("0")?Integer.parseInt(args[2]):Integer.parseInt(args[2])-1;

    }

    @Override
    public byte[] outCommandBytes() {
        byte[] result=new byte[1];
        result[0]= (byte) this.port;
        return result;
    }
}
