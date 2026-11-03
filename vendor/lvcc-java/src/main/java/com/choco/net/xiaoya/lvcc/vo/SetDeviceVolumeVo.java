package com.choco.net.xiaoya.lvcc.vo;

import com.choco.net.basic.ex.DeviceCommandArgsCountException;
import com.choco.net.basic.ex.ProtocolException;

/**
 * @Author: tanning
 * @Date: 2024/06/20/14:35
 * @Description: 设置喇叭音量 power_bank_volume_level_set:{level}
 */
public class SetDeviceVolumeVo implements IProtocolOutput{
    //播放开关(默认1：播放语言)
    private int broadcast;
    //防打扰开关(默认0：禁防打扰)
    private int noDicturb;
    //播放音量(0-7)
    private int level;
    @Override
    public void inCommandString(String command) throws ProtocolException {
        String[] args = command.split(":");
        if (args.length != 2) throw new DeviceCommandArgsCountException("设置喇叭音量 参数数量错误");
        this.broadcast=1;
        this.noDicturb=0;
        this.level=Integer.parseInt(args[1]);
    }

    @Override
    public byte[] outCommandBytes() {
        byte[] result=new byte[3];
        result[0]=(byte)this.broadcast;
        result[1]=(byte) this.noDicturb;
        result[2]=(byte) this.level;
        return result;
    }
}
