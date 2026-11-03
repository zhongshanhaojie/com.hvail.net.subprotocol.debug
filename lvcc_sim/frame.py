"""LVCC 帧：与 LvccProtocolFrame / LvccByteFactory 对齐。

线上布局（大端，设备号为 ASCII）::

    0        4        6        8    9    10          10+n       12+n     14+n
    7E5D7D7F | 长度   | 版本   | 标识 | 号长 | 设备号 n 字节 | 消息号 | 指令 | 指令内容 | 异或

- 长度字段 = 整帧字节数 - 6（不含帧头 4 字节和长度自身 2 字节）
- 版本固定 ``00 01``
- 异或与 ``LvccByteFactory.getXor(buf, 6, totalLen-7)`` 相同

通讯标识（帧内第 9 字节，下标 8）来自服务端注释和 ``LvccProtoDecode.main`` 抓包，
不是 ``pack()`` 里网关使用的 ``0x00/0x01``：

- ``0x00`` 网关下发，``0x01`` 网关响应（``LvccProtocolFrame.pack``）
- ``0x10`` 设备请求，``0x11`` 设备应答，``0x12`` 设备主动上报
"""

from __future__ import annotations

from dataclasses import dataclass

HEADER = bytes((0x7E, 0x5D, 0x7D, 0x7F))
VERSION = 0x0001

COMM_GATEWAY_CMD = 0x00
COMM_GATEWAY_RSP = 0x01
COMM_DEVICE_REQ = 0x10
COMM_DEVICE_RSP = 0x11
COMM_DEVICE_REPORT = 0x12

_MIN_FRAME = 15
_MAX_FRAME = 65535 + 6


def java_xor(buf: bytes | bytearray) -> int:
    """复刻 ``LvccByteFactory.getXor(byte[], 6, totalLen-7)``。

    Java 先把 ``buf[start]`` 放进累加器，循环又从 ``start`` 开始异或，
    因此下标 6 被异或两次、结果抵消。该字节是版本高字节 ``0x00``，
    与「从下标 7 异或到校验位前一字节」数值相同。这里按 Java 的循环原样实现。
    """

    start = 6
    length = len(buf) - 7
    if length < 1 or start >= len(buf):
        raise ValueError("帧太短，无法计算异或校验")
    end = start + length
    if end > len(buf):
        raise ValueError("异或范围超出帧长")
    temp = buf[start]
    for i in range(start, end):
        temp ^= buf[i]
    return temp & 0xFF


def hex_dump(data: bytes) -> str:
    """小写、空格分隔，与 ``LvccByteFactory.bytesToString`` 的 ``%02x`` 一致。"""

    return " ".join(f"{b:02x}" for b in data)


def parse_hex(text: str) -> bytes:
    """解析抓包十六进制。空格或连字符都接受，与 Java main 里的两种写法一致。"""

    cleaned = text.replace("-", " ").replace("\n", " ").replace("\t", " ")
    parts = [part for part in cleaned.split(" ") if part]
    return bytes(int(part, 16) for part in parts)


@dataclass(frozen=True)
class Frame:
    device_id: str
    cmd: int
    msg_id: int
    comm: int
    payload: bytes
    checksum_ok: bool
    version: int
    raw: bytes


def pack_frame(device_id: str, cmd: int, msg_id: int, comm: int, payload: bytes = b"") -> bytes:
    """组一帧。``payload`` 只含指令内容，不含校验字节。"""

    try:
        device = device_id.encode("ascii")
    except UnicodeEncodeError as exc:
        raise ValueError("设备号必须是 ASCII") from exc
    if not device or len(device) > 255:
        raise ValueError("设备号长度必须在 1..255")
    if len(payload) > 65535:
        raise ValueError("指令内容过长")
    total = 15 + len(device) + len(payload)
    buf = bytearray(total)
    buf[0:4] = HEADER
    buf[4:6] = (total - 6).to_bytes(2, "big")
    buf[6] = (VERSION >> 8) & 0xFF
    buf[7] = VERSION & 0xFF
    buf[8] = comm & 0xFF
    buf[9] = len(device)
    buf[10 : 10 + len(device)] = device
    base = 10 + len(device)
    buf[base : base + 2] = (msg_id & 0xFFFF).to_bytes(2, "big")
    buf[base + 2 : base + 4] = (cmd & 0xFFFF).to_bytes(2, "big")
    if payload:
        buf[base + 4 : base + 4 + len(payload)] = payload
    buf[-1] = java_xor(buf)
    return bytes(buf)


def parse_frame(raw: bytes) -> Frame:
    """拆帧。校验失败仍返回帧，``checksum_ok`` 为假。

    服务端 ``BufferCheck`` 里的异或判断是注释掉的，设备侧同样不能因校验失败丢帧。
    """

    if len(raw) < _MIN_FRAME or raw[:4] != HEADER:
        raise ValueError("不是 LVCC 帧")
    declared = int.from_bytes(raw[4:6], "big") + 6
    if declared != len(raw):
        raise ValueError(f"长度字段 {declared} 与实际 {len(raw)} 不一致")
    version = int.from_bytes(raw[6:8], "big")
    comm = raw[8]
    dev_len = raw[9]
    if 14 + dev_len > len(raw) - 1:
        raise ValueError("设备号长度超出帧边界")
    device_id = raw[10 : 10 + dev_len].decode("ascii", "replace")
    base = 10 + dev_len
    msg_id = int.from_bytes(raw[base : base + 2], "big")
    cmd = int.from_bytes(raw[base + 2 : base + 4], "big")
    payload = raw[base + 4 : -1]
    return Frame(
        device_id=device_id,
        cmd=cmd,
        msg_id=msg_id,
        comm=comm,
        payload=bytes(payload),
        checksum_ok=java_xor(raw) == raw[-1],
        version=version,
        raw=bytes(raw),
    )


class FrameDecoder:
    """从 TCP 字节流切帧，处理粘包和半包。找帧方式对应 ``LvccProtoDecode.BufferCheck``。"""

    def __init__(self) -> None:
        self.buf = bytearray()

    def feed(self, data: bytes) -> list[Frame]:
        if data:
            self.buf.extend(data)
        frames: list[Frame] = []
        while True:
            idx = self.buf.find(HEADER)
            if idx < 0:
                if len(self.buf) > 3:
                    del self.buf[:-3]
                break
            if idx:
                del self.buf[:idx]
            if len(self.buf) < 6:
                break
            total = int.from_bytes(self.buf[4:6], "big") + 6
            if total < _MIN_FRAME or total > _MAX_FRAME:
                del self.buf[0]
                continue
            if len(self.buf) < total:
                break
            raw = bytes(self.buf[:total])
            try:
                frame = parse_frame(raw)
            except ValueError:
                del self.buf[0]
                continue
            del self.buf[:total]
            frames.append(frame)
        return frames
