package com.choco.net.xiaoya.lvcc.models;

import com.choco.net.basic.dto.DeviceNetBasicDTO;
import com.choco.net.basic.protocol.RealtimeCall;

@RealtimeCall
public class DeviceNetHeartLvccModel extends DeviceNetBasicDTO {

    public DeviceNetHeartLvccModel() {
    }

    public DeviceNetHeartLvccModel(String deviceNo) {
        super(deviceNo);
    }

    @Override
    public String getDataTypeIdentifier() {
        return "com.hvail.net.basic.data.DeviceNetHeartDTO";
    }

}
