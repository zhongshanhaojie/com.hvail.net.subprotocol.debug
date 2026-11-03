package com.choco.net.xiaoya.lvcc.vo;

import com.choco.net.basic.ex.DeviceCommandArgsCountException;
import com.choco.net.basic.ex.ProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 复位设备故障下行组包（协议 {@code 0x0038}）。
 * <p>
 * 【审核点】平台指令格式：{@code pile_fault_reset:{开关0关/1开}:{错误代码}}
 * 对应协议请求字段：开-关(Byte) + 错误代码(Byte)。
 */
public class FaultResetVo implements IProtocolOutput {

    private final static Logger log = LoggerFactory.getLogger(FaultResetVo.class);

    /** 0=关，1=开 */
    private int enable;
    /** 要复位的故障/错误代码 */
    private int errorCode;

    @Override
    public void inCommandString(String command) throws ProtocolException {
        // 【审核点】args[0]=pile_fault_reset，args[1]=开关，args[2]=错误代码
        String[] args = command.split(":");
        if (args.length != 3) {
            throw new DeviceCommandArgsCountException("复位设备故障 参数数量错误，期望 pile_fault_reset:{0|1}:{错误代码}");
        }
        this.enable = Integer.parseInt(args[1]);
        this.errorCode = Integer.parseInt(args[2]);
        if (this.enable != 0 && this.enable != 1) {
            throw new DeviceCommandArgsCountException("复位设备故障 开关只能为 0 或 1");
        }
        log.debug("FaultResetVo enable={} errorCode={}", this.enable, this.errorCode);
    }

    @Override
    public byte[] outCommandBytes() {
        // 【审核点】指令内容仅 2 字节，无其它扩展字段
        return new byte[]{(byte) this.enable, (byte) this.errorCode};
    }
}
