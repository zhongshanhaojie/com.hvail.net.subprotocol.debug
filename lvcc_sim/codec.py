"""指令内容编解码。字段顺序只来自 Java 解析/组包代码和其中的完整抓包。

端口在线上是 0 起始。``StartChargeVo`` 把业务端口减 1 再写入；
``deviceStartCharge`` / ``deviceStopCharge`` / ``deviceEndCharge`` 读出后再加 1。
刷卡订单 ``uploadCardOrder`` 的端口字段本身不加 1，``ChargePileCardRequest`` 里才加。
"""

from __future__ import annotations

import time
from dataclasses import dataclass

from lvcc_sim.frame import (
    COMM_DEVICE_REPORT,
    COMM_DEVICE_REQ,
    COMM_DEVICE_RSP,
    COMM_GATEWAY_CMD,
    COMM_GATEWAY_RSP,
    Frame,
)

CMD_REGISTER = 0x0001
CMD_HEARTBEAT = 0x0002
CMD_START = 0x0005
CMD_QUERY_STATUS = 0x0006
CMD_STOP = 0x0007
CMD_CARD_QUERY = 0x0008
CMD_CARD_ORDER = 0x0009
CMD_END = 0x0010
CMD_QUERY_INFO = 0x0011
CMD_QUERY_ARGS = 0x0013
CMD_SET_ARGS = 0x0014
CMD_FAULT = 0x0016
CMD_REBOOT = 0x0017
CMD_QUERY_PORTS = 0x0019
CMD_PORT_STATUS = 0x0021
CMD_SET_VOLUME = 0x0027
CMD_QUERY_VOLUME = 0x0028
CMD_POWER_REPORT = 0x0030
CMD_FAULT_RESET = 0x0038
CMD_REALTIME = 0x0048

COMMAND_NAMES = {
    CMD_REGISTER: "注册",
    CMD_HEARTBEAT: "心跳",
    CMD_START: "开启充电",
    CMD_QUERY_STATUS: "查询充电状态",
    CMD_STOP: "停止充电",
    CMD_CARD_QUERY: "刷卡查询充电时间",
    CMD_CARD_ORDER: "上报刷卡订单",
    CMD_END: "充电结束",
    CMD_QUERY_INFO: "查询设备信息",
    CMD_QUERY_ARGS: "查询设备配置信息",
    CMD_SET_ARGS: "设置设备配置信息",
    CMD_FAULT: "故障上报",
    CMD_REBOOT: "设备重启",
    CMD_QUERY_PORTS: "查询设备插座状态",
    CMD_PORT_STATUS: "插座状态更改上报",
    CMD_SET_VOLUME: "设置喇叭音量",
    CMD_QUERY_VOLUME: "查询喇叭音量",
    CMD_POWER_REPORT: "充电状态定时上报",
    CMD_FAULT_RESET: "复位设备故障",
    CMD_REALTIME: "实时充电状态定时上报",
}

COMM_NAMES = {
    COMM_GATEWAY_CMD: "网关下发",
    COMM_GATEWAY_RSP: "网关响应",
    COMM_DEVICE_REQ: "设备请求",
    COMM_DEVICE_RSP: "设备应答",
    COMM_DEVICE_REPORT: "设备上报",
}

RESULT_OK = 1
RESULT_FAIL = 2

# 模拟器写入 0x0010 结束原因字节。服务端只 setEvent(buffer[0])，源码没有码表。
END_REASON_TIME = 1
END_REASON_NOLOAD = 2
END_REASON_FULL = 3
END_REASON_LOCAL = 4
END_REASON_TEXT = {
    END_REASON_TIME: "达到充电时长",
    END_REASON_NOLOAD: "空载",
    END_REASON_FULL: "充满",
    END_REASON_LOCAL: "本地结束",
}

# 0x0021 插座状态原值。服务端原样保存，Enum 里没有定义。
PORT_IDLE = 0
PORT_CHARGING = 1
PORT_FAULT = 2
PORT_STATUS_TEXT = {PORT_IDLE: "空闲", PORT_CHARGING: "充电", PORT_FAULT: "故障"}

CARD_RESULTS = {1: "成功", 2: "异常失败", 3: "余额不足", 4: "该卡未录入系统"}

# EnumLvccFaultCode：同一数字在整机（插座 0xFF）和通道下含义不同。
DEVICE_FAULTS = {
    1: "保险丝熔断(早期设备)",
    2: "RTC错误",
    3: "储存器错误",
    5: "烟感报警(整机)",
    6: "计量IC故障",
    7: "保险丝检测电路故障",
    8: "刷卡板故障",
    9: "电压过压",
    10: "充电机故障",
    11: "灭火报警触发",
    12: "漏电故障",
    13: "水浸报警",
    14: "温度报警",
}
PORT_FAULTS = {
    1: "继电器粘连",
    2: "保险丝烧断",
    3: "保险丝短路",
    4: "灭火器触发",
    5: "烟感报警(单通道)",
    10: "充电机故障",
    11: "灭火报警触发",
    14: "温度报警",
}

# EnumLvccDeviceType
DEVICE_TYPES = {
    1: "老款10路充电桩(IG1000、IG1001)",
    2: "老款10路充电柜(BG1000)",
    3: "投币改造模块(UG0000)",
    4: "老款双插小功率(PG0200)",
    5: "老款单路大功率(PH0100)",
    7: "新款10路充电桩(IG1031、IG1022、IG1050)",
    8: "新款10路充电桩-注塑-液晶(OG1020)",
    9: "新款双插大功率(PH0200、OH0200)",
    10: "新款单插大功率(PH0110)",
    11: "门禁设备(DOOR)",
    12: "新款20路充电桩(IG2020)",
    13: "无线433网关",
    14: "433设备2路大功率(PH0200-433)",
    15: "双路门禁道闸",
    16: "Air202双插(PH0210、OH0200、OH0210)",
    17: "Air720双插(PH0220、OH0220)",
    18: "单机版20路(IG2021)",
    19: "单机版10路(IG1032)",
    20: "汽车7KW交流桩(OH0100)",
    21: "直流6路充电桩(OH0601-D)",
    22: "8+2充电柜",
    23: "8路充电柜BG0811",
    24: "户外20无广告位，无刷卡板",
    25: "新投币改造升级模块-2G版",
    26: "M10_BL0939",
    28: "WIFI双插PH0240",
    29: "Air720双插(OH0230)",
    30: "8BC-1充电柜",
    32: "直流4路充电桩",
    34: "Air720-IG1080(10BL-6)",
    35: "汽车7KW交流桩(AA700130)",
    36: "换电柜BG0830",
    37: "汽车14KW交流桩（AA140210)",
    38: "8路充电柜BG0810",
    39: "10路广告机(IG1060、10BL-4)",
    40: "20路广告机(IG2030、20BL-2)",
    42: "IG1052-复旦微",
    43: "复旦微IG1052-单机",
    44: "OG1020-FM",
    45: "485双插主机",
    46: "485双插从机",
    47: "10BL-1",
    48: "汽车7KW交流桩(AA700140)",
    49: "1ZA7-2(汽车7KW交流桩)",
    50: "2ZA7-1/2(汽车桩14KW)",
    51: "1ZA7-2-OFFLINE",
    52: "2ZA7-1-OFFLINE",
    53: "汽车7KW交流桩(1ZA7-1)",
    54: "OG1020-FM-OFFLINE",
    55: "汽车11KW交流桩(1ZA11-1)",
    56: "汽车22KW交流桩(1ZA22-1)",
    57: "20BL-1",
    58: "MB02_4G门禁",
    59: "RS485组网双插-主机-复旦微方案",
    60: "RS485组网双插-从机-复旦微方案",
    61: "BG1010-FM 8+2充电柜",
    63: "4路充电柜4BC-1-FM",
    64: "10路广告机(10BL-3)",
    66: "10路(10BL-5-FM)",
    67: "1OA7_1_OFFLINE",
    68: "2路(OH0230_EC600N)",
    69: "双插大功率(OH0250-WIFI)",
    70: "OH0100-OFFLINE",
    71: "1ZA7_1-OFFLINE",
    72: "AA700130-OFFLINE",
    73: "AA1400210-OFFLINE",
    74: "1ZA11_1_OFFLINE",
    75: "换电柜8BH-2",
    76: "汽车交流42KW-JN",
    77: "1ZA22_1_OFFLINE",
    78: "汽车交流1OA11_1",
    79: "汽车交流1OA7_1",
    80: "充电柜(10BC-1)",
    81: "汽车交流1OA22_1",
    82: "汽车交流1OA11-1-OFFLINE",
    83: "汽车交流1OA22-1-OFFLINE",
    84: "换电柜12BH-1",
    85: "12路充电桩12BL-1",
    86: "12BC-1充电柜",
    87: "1MA7_2_OFFLINE",
    88: "1MA7_2",
    89: "10BL-1（10BL-7）MP3",
    90: "OH0260-ye",
    91: "OH0260-ye-Slave",
    92: "2ZA3-1",
    93: "10BL-8",
    94: "2ZA3-1-OFFLINE",
    95: "10BL-5-Basics",
    96: "20BL-1-MP3",
    97: "2BL-1",
}


def command_name(cmd: int) -> str:
    return COMMAND_NAMES.get(cmd, f"未定义类型(0x{cmd:04X})")


def u16(value: int) -> bytes:
    value = max(0, min(int(value), 0xFFFF))
    return value.to_bytes(2, "big")


def u32(value: int) -> bytes:
    value = max(0, min(int(value), 0xFFFFFFFF))
    return value.to_bytes(4, "big")


def result_name(code: int) -> str:
    return {RESULT_OK: "成功", RESULT_FAIL: "失败"}.get(code, str(code))


def fault_text(code: int, whole_device: bool) -> str:
    table = DEVICE_FAULTS if whole_device else PORT_FAULTS
    if code in table:
        return table[code]
    scope = "(整机)" if whole_device else "(通道)"
    return f"未知故障码:{code}{scope}"


def device_type_name(code: int) -> str:
    return DEVICE_TYPES.get(code, f"类型{code}")


def order_bytes(order: str | bytes) -> bytes:
    """20 字节 ASCII，不足左补 ``0``。对应 ``String.format("%020d", ...)``。"""

    raw = order.encode("ascii") if isinstance(order, str) else bytes(order)
    if len(raw) > 20:
        raise ValueError("订单号大于20")
    if len(raw) == 20:
        return raw
    return raw.rjust(20, b"0")


def order_text(raw: bytes) -> str:
    text = raw.decode("ascii", "replace").replace("\x00", "")
    stripped = text.lstrip("0")
    return stripped or "0"


def charge_minutes_from_seconds(seconds: int) -> int:
    """``StartChargeVo.inCommandString`` 把「时长(秒)」整除 60 后写入线上短整型。

    字段注释写的是「单位：10分钟」，与代码不符。模拟器按代码，把该短整型当作分钟。
    """

    if seconds < 0:
        return 0
    return int(seconds) // 60


def encode_register(
    signal: int,
    soft_version: int,
    hard_version: int,
    comm_version: int,
    ports: int,
    device_type: int,
    reserved: int = 0x01,
) -> bytes:
    """注册 / 查询设备信息应答的 10 字节指令内容。

    ``ProtocolCallDataResponse.deviceRegister`` 从偏移 1、3 读主控版本和刷卡板版本。
    被注释掉的代码继续读偏移 5 通讯模块版本、偏移 7 插座数、偏移 8 设备类型。
    偏移 0 与 ``0x0048`` 的「信号」以及心跳之外的定时上报首字节同一位置，抓包为 ``0x1D``。
    偏移 9 在 ``LvccProtoDecode.main`` 的注册抓包里是 ``0x01``，当前解析器不读。
    """

    body = bytearray(10)
    body[0] = signal & 0xFF
    body[1:3] = u16(soft_version)
    body[3:5] = u16(hard_version)
    body[5:7] = u16(comm_version)
    body[7] = ports & 0xFF
    body[8] = device_type & 0xFF
    body[9] = reserved & 0xFF
    return bytes(body)


def decode_register(body: bytes) -> dict:
    if len(body) < 5:
        raise ValueError(f"注册指令内容长度 {len(body)}，至少 5")
    info = {
        "signal": body[0],
        "soft_version": int.from_bytes(body[1:3], "big"),
        "hard_version": int.from_bytes(body[3:5], "big"),
    }
    if len(body) >= 7:
        info["comm_version"] = int.from_bytes(body[5:7], "big")
    if len(body) >= 8:
        info["ports"] = body[7]
    if len(body) >= 9:
        info["device_type"] = body[8]
    if len(body) >= 10:
        info["reserved"] = body[9]
    return info


def decode_unix_time(body: bytes) -> int | None:
    """注册/心跳的网关响应：``int2ByteArray`` 大端 Unix 秒。"""

    if len(body) < 4:
        return None
    return int.from_bytes(body[:4], "big")


def encode_unix_time(timestamp: int) -> bytes:
    return u32(timestamp)


@dataclass
class DeviceArgs:
    """``SetDeviceArgsVo.outCommandBytes`` 的 15 字节。查询配置按同一布局回。"""

    min_power: int = 15
    max_power: int = 750
    no_load_wait: int = 15
    full_wait: int = 1800
    heart_time: int = 30
    charge_report: int = 20
    work_voltage: int = 220
    info_upload_hours: int = 12
    temp_alarm: int = 80
    smoke: int = 0

    def to_bytes(self) -> bytes:
        return b"".join(
            (
                u16(self.min_power),
                u16(self.max_power),
                u16(self.no_load_wait),
                u16(self.full_wait),
                bytes((self.heart_time & 0xFF,)),
                u16(self.charge_report),
                bytes(
                    (
                        self.work_voltage & 0xFF,
                        self.info_upload_hours & 0xFF,
                        self.temp_alarm & 0xFF,
                        self.smoke & 0xFF,
                    )
                ),
            )
        )

    @classmethod
    def from_bytes(cls, data: bytes) -> DeviceArgs:
        if len(data) < 15:
            raise ValueError(f"配置长度 {len(data)}，需要 15")
        return cls(
            min_power=int.from_bytes(data[0:2], "big"),
            max_power=int.from_bytes(data[2:4], "big"),
            no_load_wait=int.from_bytes(data[4:6], "big"),
            full_wait=int.from_bytes(data[6:8], "big"),
            heart_time=data[8],
            charge_report=int.from_bytes(data[9:11], "big"),
            work_voltage=data[11],
            info_upload_hours=data[12],
            temp_alarm=data[13],
            smoke=data[14],
        )

    def summary(self) -> str:
        smoke = "开" if self.smoke else "关"
        return (
            f"最小功率={self.min_power}W 最大功率={self.max_power}W "
            f"空载等待={self.no_load_wait}秒 充满等待={self.full_wait}秒 "
            f"心跳={self.heart_time}秒 充电上报={self.charge_report}秒 "
            f"工作电压={self.work_voltage}V 信息上报={self.info_upload_hours}小时 "
            f"温度报警={self.temp_alarm} 烟感={smoke}"
        )


def encode_start_request(
    port: int,
    min_power: int,
    max_power: int,
    no_load_wait: int,
    full_wait: int,
    minutes: int,
    order: str | bytes,
    billing: int = 1,
    turn_on: int = 1,
    full_stop: int = 1,
) -> bytes:
    """``StartChargeVo.outCommandBytes``，固定 34 字节。"""

    body = bytearray(34)
    body[0] = port & 0xFF
    body[1:3] = u16(min_power)
    body[3:5] = u16(max_power)
    body[5:7] = u16(no_load_wait)
    body[7:9] = u16(full_wait)
    body[9:11] = u16(minutes)
    body[11:31] = order_bytes(order)
    body[31] = billing & 0xFF
    body[32] = turn_on & 0xFF
    body[33] = full_stop & 0xFF
    return bytes(body)


def decode_start_request(body: bytes) -> dict:
    if len(body) < 34:
        raise ValueError(f"开启充电指令内容长度 {len(body)}，需要 34")
    return {
        "port": body[0],
        "min_power": int.from_bytes(body[1:3], "big"),
        "max_power": int.from_bytes(body[3:5], "big"),
        "no_load_wait": int.from_bytes(body[5:7], "big"),
        "full_wait": int.from_bytes(body[7:9], "big"),
        "minutes": int.from_bytes(body[9:11], "big"),
        "order": body[11:31],
        "billing": body[31],
        "turn_on": body[32],
        "full_stop": body[33],
    }


def encode_start_response(result: int, port: int, remain_minutes: int) -> bytes:
    """``deviceStartCharge``：结果、0 起始插座、剩余分钟（大端 short）。"""

    return bytes((result & 0xFF, port & 0xFF)) + u16(remain_minutes)


def decode_start_response(body: bytes) -> dict:
    if len(body) < 4:
        raise ValueError(f"开启充电应答长度 {len(body)}，需要 4")
    return {
        "result": body[0],
        "port": body[1],
        "remain_minutes": int.from_bytes(body[2:4], "big"),
    }


def encode_stop_response(result: int, port: int, minutes: int, battery: int, order: str | bytes) -> bytes:
    """``deviceStopCharge`` 读前 25 字节。抓包在订单号后再带 2 个 0，总长 27。"""

    body = bytearray(27)
    body[0] = result & 0xFF
    body[1] = port & 0xFF
    body[2:4] = u16(minutes)
    body[4] = battery & 0xFF
    body[5:25] = order_bytes(order)
    return bytes(body)


def decode_stop_response(body: bytes) -> dict:
    if len(body) < 25:
        raise ValueError(f"停止充电应答长度 {len(body)}，需要 25")
    return {
        "result": body[0],
        "port": body[1],
        "minutes": int.from_bytes(body[2:4], "big"),
        "battery": body[4],
        "order": body[5:25],
    }


def encode_end(reason: int, port: int, order: str | bytes, electric: int, power: int, elapsed_seconds: int) -> bytes:
    """``deviceEndCharge``：原因、插座、20 字节订单、耗电 short、结束功率 short、4 字节。

    4 字节被 ``setChargeTime`` 接收，注释写「结束时间」。模拟器写入已经充电的秒数。
    """

    body = bytearray(30)
    body[0] = reason & 0xFF
    body[1] = port & 0xFF
    body[2:22] = order_bytes(order)
    body[22:24] = u16(electric)
    body[24:26] = u16(power)
    body[26:30] = u32(elapsed_seconds)
    return bytes(body)


def decode_end(body: bytes) -> dict:
    if len(body) < 30:
        raise ValueError(f"充电结束长度 {len(body)}，需要 30")
    return {
        "reason": body[0],
        "port": body[1],
        "order": body[2:22],
        "electric": int.from_bytes(body[22:24], "big"),
        "power": int.from_bytes(body[24:26], "big"),
        "elapsed": int.from_bytes(body[26:30], "big"),
    }


def encode_port_status(port: int, status: int) -> bytes:
    """``portStatusChangeUp``：插座、状态，各 1 字节。插座 0 起始。"""

    return bytes((port & 0xFF, status & 0xFF))


def decode_port_status(body: bytes) -> dict:
    if len(body) < 2:
        raise ValueError("插座状态上报至少 2 字节")
    return {"port": body[0], "status": body[1]}


def encode_power_report(signal: int, powers: list[int]) -> bytes:
    """``deviceChargeStatusUp``：首字节不参与端口循环，之后每个大端 short 是一路功率（瓦）。

    通道号是 short 的序号（从 1 计），所以必须按插座顺序带上功率为 0 的口。
    """

    body = bytearray((signal & 0xFF,))
    for power in powers:
        body += u16(power)
    return bytes(body)


def decode_power_report(body: bytes) -> dict:
    if not body:
        raise ValueError("充电状态定时上报为空")
    powers = []
    index = 1
    while index + 1 < len(body):
        powers.append(int.from_bytes(body[index : index + 2], "big"))
        index += 2
    return {"signal": body[0], "powers": powers}


def encode_realtime(signal: int, rows: list[tuple[int, int, int, int]], env_temp: int, machine_count: int) -> bytes:
    """``RealChargeStatusTimingUp`` 注释中的布局。

    信号、端口数、N 个耗电 short、N 个电压 short、N 个电流 short、N 个温度 short、
    环境温度 short、整机数据条数 short。

    电压原值 /100 = 伏，电流原值 /1000 = 安，功率由服务端用这两个数相乘得到。
    耗电 short 模拟器按 Wh 写入。服务端 ``contentLen = body.length - 1`` 会少看最后一个字节，
    因此「整机数据条数」这个结尾 short 经常被跳过；前面的端口数据不受影响。
    """

    count = len(rows)
    body = bytearray((signal & 0xFF, count & 0xFF))
    for energy, _voltage, _current, _temp in rows:
        body += u16(energy)
    for _energy, voltage, _current, _temp in rows:
        body += u16(voltage)
    for _energy, _voltage, current, _temp in rows:
        body += u16(current)
    for _energy, _voltage, _current, temp in rows:
        body += u16(temp)
    body += u16(env_temp)
    body += u16(machine_count)
    return bytes(body)


def decode_realtime(body: bytes) -> dict:
    if len(body) < 2:
        raise ValueError("实时充电状态过短")
    signal = body[0]
    count = body[1]
    power_base = 2
    voltage_base = power_base + count * 2
    current_base = voltage_base + count * 2
    temp_base = current_base + count * 2
    env_off = temp_base + count * 2
    machine_off = env_off + 2

    def read_array(base: int) -> list[int]:
        values = []
        for i in range(count):
            off = base + i * 2
            if off + 2 > len(body):
                break
            values.append(int.from_bytes(body[off : off + 2], "big"))
        return values

    return {
        "signal": signal,
        "ports": count,
        "energy": read_array(power_base),
        "voltage": read_array(voltage_base),
        "current": read_array(current_base),
        "temp": read_array(temp_base),
        "env_temp": int.from_bytes(body[env_off : env_off + 2], "big") if env_off + 2 <= len(body) else None,
        "machine_count": int.from_bytes(body[machine_off : machine_off + 2], "big")
        if machine_off + 2 <= len(body)
        else None,
    }


def encode_fault(socket: int, code: int, status: int, detail: int = 0) -> bytes:
    """``deviceFaultUpload``。详情给 4 字节时服务端按 int 读。插座 ``0xFF`` 表示整机。"""

    return bytes((socket & 0xFF, code & 0xFF, status & 0xFF)) + u32(detail)


def decode_fault(body: bytes) -> dict:
    if len(body) < 3:
        raise ValueError("故障上报至少 3 字节")
    socket = body[0]
    if len(body) >= 7:
        detail = int.from_bytes(body[3:7], "big")
    elif len(body) >= 5:
        detail = int.from_bytes(body[3:5], "big")
    else:
        detail = 0
    whole = socket == 0xFF
    return {
        "socket": socket,
        "code": body[1],
        "status": body[2],
        "detail": detail,
        "whole": whole,
        "text": fault_text(body[1], whole),
    }


def encode_fault_reset(enable: int, error_code: int) -> bytes:
    """``FaultResetVo``：开关、错误代码，各 1 字节。"""

    return bytes((enable & 0xFF, error_code & 0xFF))


def decode_fault_reset(body: bytes) -> dict:
    if len(body) < 2:
        raise ValueError("复位故障指令内容需要 2 字节")
    return {"enable": body[0], "error_code": body[1]}


def encode_result(ok: bool) -> bytes:
    return bytes((RESULT_OK if ok else RESULT_FAIL,))


def encode_volume_request(broadcast: int, no_disturb: int, level: int) -> bytes:
    """``SetDeviceVolumeVo``：播放开关、防打扰、音量。"""

    return bytes((broadcast & 0xFF, no_disturb & 0xFF, level & 0xFF))


def decode_volume_request(body: bytes) -> dict:
    if len(body) < 3:
        raise ValueError("设置音量需要 3 字节")
    return {"broadcast": body[0], "no_disturb": body[1], "level": body[2]}


def encode_volume_response(result: int, broadcast: int, no_disturb: int, level: int) -> bytes:
    """查询音量抓包 ``01 01 00 01``：结果 + 设置音量的 3 个字节。"""

    return bytes((result & 0xFF, broadcast & 0xFF, no_disturb & 0xFF, level & 0xFF))


def decode_volume_response(body: bytes) -> dict:
    if len(body) < 4:
        raise ValueError("音量应答需要 4 字节")
    return {"result": body[0], "broadcast": body[1], "no_disturb": body[2], "level": body[3]}


def encode_card_query(card: bytes, tail: int) -> bytes:
    """刷卡查询。``cardQueryChargeTime`` 把除最后 1 字节外的内容当卡号十六进制。

    抓包是 4 字节卡号 + 1 字节尾。尾字节解析器不计入卡号，但必须存在，否则 4 字节卡号会被吃掉 1 字节。
    """

    if len(card) != 4:
        raise ValueError("卡号按抓包和解析器固定为 4 字节")
    return bytes(card) + bytes((tail & 0xFF,))


def decode_card_query(body: bytes) -> dict:
    if not body:
        raise ValueError("刷卡查询为空")
    return {"card": bytes(body[:-1]), "tail": body[-1]}


def encode_card_order(port: int, minutes: int, order: str | bytes, card: bytes, tail: int) -> bytes:
    """``uploadCardOrder`` 读取 1+2+20+4。抓包在卡号后再有 1 字节，与查询帧的尾字节相同。"""

    if len(card) != 4:
        raise ValueError("卡号按抓包和解析器固定为 4 字节")
    body = bytearray(28)
    body[0] = port & 0xFF
    body[1:3] = u16(minutes)
    body[3:23] = order_bytes(order)
    body[23:27] = card
    body[27] = tail & 0xFF
    return bytes(body)


def decode_card_order(body: bytes) -> dict:
    if len(body) < 27:
        raise ValueError(f"刷卡订单长度 {len(body)}，至少 27")
    info = {
        "port": body[0],
        "minutes": int.from_bytes(body[1:3], "big"),
        "order": body[3:23],
        "card": body[23:27],
    }
    if len(body) >= 28:
        info["tail"] = body[27]
    return info


def decode_card_response(body: bytes) -> dict:
    """``CardQueryChargeTimeResponse.buildContent`` 的 39 字节；失败时只有 1 字节结果。"""

    if len(body) == 1:
        return {"result": body[0]}
    if len(body) < 39:
        raise ValueError(f"刷卡查询应答长度 {len(body)}")
    return {
        "result": body[0],
        "minutes": int.from_bytes(body[1:3], "big"),
        "flag": body[3],
        "amount": int.from_bytes(body[4:8], "big"),
        "min_power": int.from_bytes(body[8:10], "big"),
        "max_power": int.from_bytes(body[10:12], "big"),
        "no_load_wait": int.from_bytes(body[12:14], "big"),
        "full_wait": int.from_bytes(body[14:16], "big"),
        "order": body[16:36],
        "billing": body[36],
        "turn_on": body[37],
        "full_stop": body[38],
    }


def describe_frame(frame: Frame) -> str:
    comm = COMM_NAMES.get(frame.comm, f"通讯=0x{frame.comm:02X}")
    xor = "校验通过" if frame.checksum_ok else "校验失败"
    text = (
        f"{comm} {command_name(frame.cmd)} cmd=0x{frame.cmd:04X} "
        f"comm=0x{frame.comm:02X} msg={frame.msg_id} dev={frame.device_id} {xor}"
    )
    detail = describe_payload(frame.cmd, frame.comm, frame.payload)
    if detail:
        text += " | " + detail
    return text


def describe_payload(cmd: int, comm: int, payload: bytes) -> str:
    try:
        return _describe_payload(cmd, comm, payload)
    except ValueError as exc:
        return f"载荷与已知布局不符（{exc}） hex={payload.hex()}"


def _describe_payload(cmd: int, comm: int, payload: bytes) -> str:
    if comm == COMM_GATEWAY_RSP:
        if cmd in (CMD_REGISTER, CMD_HEARTBEAT):
            stamp = decode_unix_time(payload)
            if stamp is None:
                return f"时间字段不足 hex={payload.hex()}"
            clock = time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(stamp))
            return f"服务器时间={clock} UTC"
        if cmd == CMD_CARD_QUERY:
            info = decode_card_response(payload)
            name = CARD_RESULTS.get(info["result"], str(info["result"]))
            if "order" not in info:
                return f"结果={name}"
            return (
                f"结果={name} 可充={info['minutes']}分钟 金额原值={info['amount']} "
                f"订单={order_text(info['order'])} 功率={info['min_power']}-{info['max_power']}W"
            )
        if cmd == CMD_CARD_ORDER and payload:
            return f"结果={CARD_RESULTS.get(payload[0], result_name(payload[0]))}"
        if payload == bytes((RESULT_OK,)):
            return "结果=成功"
        if payload == bytes((RESULT_FAIL,)):
            return "结果=失败"
        return f"响应 hex={payload.hex()}" if payload else "空响应"

    if comm == COMM_GATEWAY_CMD:
        return _describe_downlink(cmd, payload)
    return _describe_device(cmd, payload)


def _describe_downlink(cmd: int, payload: bytes) -> str:
    if cmd == CMD_START:
        info = decode_start_request(payload)
        return (
            f"插座={info['port'] + 1} 订单={order_text(info['order'])} "
            f"时长={info['minutes']}分钟 功率={info['min_power']}-{info['max_power']}W "
            f"空载等待={info['no_load_wait']}秒 充满等待={info['full_wait']}秒 "
            f"计费={info['billing']} 开电={info['turn_on']} 充满断电={info['full_stop']}"
        )
    if cmd in (CMD_STOP, CMD_QUERY_STATUS):
        if not payload:
            return "缺少插座号"
        return f"插座={payload[0] + 1}"
    if cmd == CMD_SET_ARGS:
        return DeviceArgs.from_bytes(payload).summary()
    if cmd == CMD_SET_VOLUME:
        info = decode_volume_request(payload)
        return f"播放={info['broadcast']} 防打扰={info['no_disturb']} 音量={info['level']}"
    if cmd == CMD_FAULT_RESET:
        info = decode_fault_reset(payload)
        switch = "开" if info["enable"] else "关"
        return f"开关={switch} 错误代码={info['error_code']}"
    if cmd in (CMD_QUERY_INFO, CMD_QUERY_ARGS, CMD_QUERY_PORTS, CMD_QUERY_VOLUME, CMD_REBOOT):
        return "无指令内容" if not payload else f"hex={payload.hex()}"
    if not payload:
        return "无指令内容"
    return f"hex={payload.hex()}"


def _describe_device(cmd: int, payload: bytes) -> str:
    if cmd in (CMD_REGISTER, CMD_QUERY_INFO) and len(payload) >= 5:
        info = decode_register(payload)
        kind = info.get("device_type")
        kind_text = device_type_name(kind) if kind is not None else "?"
        return (
            f"信号={info['signal']} 主控={info['soft_version']} 刷卡板={info['hard_version']} "
            f"通讯模块={info.get('comm_version', '?')} 插座={info.get('ports', '?')} "
            f"类型={kind if kind is not None else '?'}({kind_text})"
        )
    if cmd == CMD_HEARTBEAT:
        return "无指令内容" if not payload else f"hex={payload.hex()}"
    if cmd == CMD_START:
        info = decode_start_response(payload)
        return (
            f"结果={result_name(info['result'])} 插座={info['port'] + 1} "
            f"剩余={info['remain_minutes']}分钟"
        )
    if cmd == CMD_STOP and len(payload) >= 25:
        info = decode_stop_response(payload)
        return (
            f"结果={result_name(info['result'])} 插座={info['port'] + 1} "
            f"已充={info['minutes']}分钟 电瓶电量={info['battery']} 订单={order_text(info['order'])}"
        )
    if cmd == CMD_END:
        info = decode_end(payload)
        reason = END_REASON_TEXT.get(info["reason"], str(info["reason"]))
        return (
            f"原因={info['reason']}({reason}) 插座={info['port'] + 1} "
            f"订单={order_text(info['order'])} 耗电={info['electric']} 功率={info['power']}W "
            f"充电秒数={info['elapsed']}"
        )
    if cmd == CMD_PORT_STATUS:
        info = decode_port_status(payload)
        state = PORT_STATUS_TEXT.get(info["status"], str(info["status"]))
        return f"插座={info['port'] + 1} 状态={info['status']}({state})"
    if cmd == CMD_POWER_REPORT:
        info = decode_power_report(payload)
        active = [f"{i + 1}:{w}W" for i, w in enumerate(info["powers"]) if w]
        shown = " ".join(active) if active else "全部低于上报阈值或为0"
        return f"信号={info['signal']} 路数={len(info['powers'])} {shown}"
    if cmd == CMD_REALTIME:
        info = decode_realtime(payload)
        return (
            f"信号={info['signal']} 端口数={info['ports']} "
            f"耗电={info['energy']} 电压原值={info['voltage']} 电流原值={info['current']} "
            f"温度={info['temp']} 环境={info['env_temp']} 条数={info['machine_count']}"
        )
    if cmd == CMD_FAULT:
        info = decode_fault(payload)
        where = "整机" if info["whole"] else f"插座={info['socket'] + 1}"
        state = "产生" if info["status"] else "解除"
        return f"{where} 故障={info['code']}({info['text']}) {state} 详情={info['detail']}"
    if cmd == CMD_CARD_QUERY:
        info = decode_card_query(payload)
        return f"卡号={info['card'].hex()} 尾字节=0x{info['tail']:02X}"
    if cmd == CMD_CARD_ORDER:
        info = decode_card_order(payload)
        tail = info.get("tail")
        tail_text = f" 尾字节=0x{tail:02X}" if tail is not None else ""
        return (
            f"插座={info['port'] + 1} 已充={info['minutes']}分钟 "
            f"订单={order_text(info['order'])} 卡号={info['card'].hex()}{tail_text}"
        )
    if cmd == CMD_QUERY_ARGS and len(payload) >= 15:
        return DeviceArgs.from_bytes(payload).summary()
    if cmd in (CMD_SET_VOLUME, CMD_QUERY_VOLUME) and len(payload) >= 4:
        info = decode_volume_response(payload)
        return (
            f"结果={result_name(info['result'])} 播放={info['broadcast']} "
            f"防打扰={info['no_disturb']} 音量={info['level']}"
        )
    if cmd == CMD_FAULT_RESET and len(payload) >= 2 and payload[0] in (0, 1):
        info = decode_fault_reset(payload)
        switch = "开" if info["enable"] else "关"
        return f"开关={switch} 错误代码={info['error_code']}"
    if len(payload) == 1:
        return f"结果={result_name(payload[0])}"
    if not payload:
        return "无指令内容"
    return f"hex={payload.hex()}"
