"""设备状态与上下行处理。模拟器是 TCP 客户端，收到的是网关下发或网关响应。"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field

from lvcc_sim.codec import (
    CARD_RESULTS,
    CMD_CARD_ORDER,
    CMD_CARD_QUERY,
    CMD_END,
    CMD_FAULT,
    CMD_FAULT_RESET,
    CMD_HEARTBEAT,
    CMD_PORT_STATUS,
    CMD_POWER_REPORT,
    CMD_QUERY_ARGS,
    CMD_QUERY_INFO,
    CMD_QUERY_PORTS,
    CMD_QUERY_STATUS,
    CMD_QUERY_VOLUME,
    CMD_REALTIME,
    CMD_REBOOT,
    CMD_REGISTER,
    CMD_SET_ARGS,
    CMD_SET_VOLUME,
    CMD_START,
    CMD_STOP,
    END_REASON_FULL,
    END_REASON_LOCAL,
    END_REASON_NOLOAD,
    END_REASON_TEXT,
    END_REASON_TIME,
    PORT_CHARGING,
    PORT_FAULT,
    PORT_IDLE,
    PORT_STATUS_TEXT,
    RESULT_FAIL,
    RESULT_OK,
    DeviceArgs,
    decode_card_response,
    decode_fault_reset,
    decode_start_request,
    decode_unix_time,
    decode_volume_request,
    encode_card_order,
    encode_card_query,
    encode_end,
    encode_fault,
    encode_port_status,
    encode_power_report,
    encode_realtime,
    encode_register,
    encode_result,
    encode_start_response,
    encode_stop_response,
    encode_volume_response,
    order_text,
    result_name,
)
from lvcc_sim.frame import (
    COMM_DEVICE_REPORT,
    COMM_DEVICE_REQ,
    COMM_DEVICE_RSP,
    COMM_GATEWAY_CMD,
    COMM_GATEWAY_RSP,
    Frame,
)

log = logging.getLogger("lvcc")


@dataclass
class SimConfig:
    device_id: str = "868089057672499"
    host: str = "127.0.0.1"
    port: int = 29001
    port_count: int = 10
    heartbeat: float = 30.0
    report_interval: float = 20.0
    power_w: float = 220.0
    voltage: float = 220.0
    signal: int = 0x1D
    soft_version: int = 0x011E
    hard_version: int = 0x0001
    comm_version: int = 0x0001
    device_type: int = 0x2F
    register_tail: int = 0x01
    temperature: int = 35
    env_temperature: int = 28
    reconnect: bool = True
    retry_seconds: float = 5.0
    interactive: bool = False
    smoke: bool = False


@dataclass
class Session:
    port: int
    order: bytes
    started: float
    duration_min: int
    min_power: int
    max_power: int
    no_load_wait: int
    full_wait: int
    full_stop: int
    power_w: float
    source: str
    energy_wh: float = 0.0
    last_touch: float | None = None
    low_since: float | None = None
    full_since: float | None = None
    card: bytes = b""
    card_tail: int = 0

    def remain_minutes(self, now: float) -> int:
        if self.duration_min <= 0:
            return 0
        used = int(max(0.0, now - self.started) // 60)
        return max(0, self.duration_min - used)


@dataclass
class Outbound:
    comm: int
    cmd: int
    payload: bytes
    msg_id: int
    summary: str = ""


@dataclass
class Device:
    cfg: SimConfig
    sessions: dict[int, Session] = field(default_factory=dict)
    fault_ports: dict[int, set[int]] = field(default_factory=dict)
    device_faults: set[int] = field(default_factory=set)
    pending_swipes: dict[int, dict] = field(default_factory=dict)
    volume_broadcast: int = 1
    volume_disturb: int = 0
    volume_level: int = 5
    close_requested: bool = False
    quit_requested: bool = False
    report_due: bool = False
    _seq: int = 0

    def __post_init__(self) -> None:
        if not self.cfg.device_id:
            raise ValueError("设备号不能为空")
        if not 1 <= self.cfg.port_count <= 255:
            raise ValueError("插座数量必须在 1..255")
        self.device_id = self.cfg.device_id
        self.port_count = self.cfg.port_count
        self.heartbeat_interval = float(self.cfg.heartbeat)
        self.report_interval = float(self.cfg.report_interval)
        self.voltage = float(self.cfg.voltage)
        self.args = DeviceArgs(
            heart_time=_clamp(round(self.heartbeat_interval), 1, 255),
            charge_report=_clamp(round(self.report_interval), 1, 65535),
            work_voltage=_clamp(round(self.voltage), 0, 255),
            temp_alarm=_clamp(self.cfg.temperature, 0, 255),
        )

    def alloc_msg(self) -> int:
        self._seq = 1 if self._seq >= 0xFFFF else self._seq + 1
        return self._seq

    def build_register(self) -> Outbound:
        payload = encode_register(
            signal=self.cfg.signal,
            soft_version=self.cfg.soft_version,
            hard_version=self.cfg.hard_version,
            comm_version=self.cfg.comm_version,
            ports=self.port_count,
            device_type=self.cfg.device_type,
            reserved=self.cfg.register_tail,
        )
        return self._request(CMD_REGISTER, payload, "")

    def build_heartbeat(self) -> Outbound:
        # 抓包心跳指令内容为空，通讯标识 0x12。网关回 4 字节 Unix 时间。
        return self._report(CMD_HEARTBEAT, b"", "")

    def handle_frame(self, frame: Frame) -> list[Outbound]:
        if frame.device_id and frame.device_id != self.device_id:
            log.warning("帧内设备号 %s 与本机 %s 不一致，仍按指令处理", frame.device_id, self.device_id)
        if frame.comm == COMM_GATEWAY_CMD:
            return self._on_command(frame)
        if frame.comm == COMM_GATEWAY_RSP:
            return self._on_response(frame)
        log.warning("收到非网关方向的帧 comm=0x%02X cmd=0x%04X", frame.comm, frame.cmd)
        return []

    def poll(self, now: float) -> list[Outbound]:
        expired = [msg for msg, item in self.pending_swipes.items() if now - item["at"] > 30]
        for msg in expired:
            item = self.pending_swipes.pop(msg)
            log.warning("刷卡请求超时 插座=%s msg=%s", item["port"] + 1, msg)

        finished: list[tuple[int, int]] = []
        for port, session in self.sessions.items():
            self._touch(session, now)
            reason = self._expiry_reason(session, now)
            if reason is not None:
                finished.append((port, reason))
        outbound: list[Outbound] = []
        for port, reason in finished:
            outbound.extend(self._finish(port, reason, now))
        return outbound

    def build_reports(self, now: float, force: bool = False) -> list[Outbound]:
        for session in self.sessions.values():
            self._touch(session, now)
        if not self.sessions and not force:
            return []
        powers = []
        rows: list[tuple[int, int, int, int]] = []
        voltage_raw = _clamp(round(self.voltage * 100), 0, 0xFFFF)
        for port in range(self.port_count):
            session = self.sessions.get(port)
            if session is None:
                powers.append(0)
                rows.append((0, voltage_raw, 0, _clamp(self.cfg.temperature, 0, 0xFFFF)))
                continue
            watts = _clamp(round(session.power_w), 0, 0xFFFF)
            energy = _clamp(round(session.energy_wh), 0, 0xFFFF)
            current = 0
            if self.voltage > 0:
                current = _clamp(round(session.power_w / self.voltage * 1000), 0, 0xFFFF)
            powers.append(watts)
            rows.append((energy, voltage_raw, current, _clamp(self.cfg.temperature, 0, 0xFFFF)))
        active = sum(1 for power in powers if power)
        summary = f"在充={active}/{self.port_count}"
        return [
            self._report(CMD_POWER_REPORT, encode_power_report(self.cfg.signal, powers), summary),
            self._report(
                CMD_REALTIME,
                encode_realtime(self.cfg.signal, rows, self.cfg.env_temperature, active),
                summary,
            ),
        ]

    def command_line(self, line: str) -> list[Outbound]:
        parts = line.strip().split()
        if not parts:
            return []
        op = parts[0].lower()
        if op in {"help", "?"}:
            log.info("%s", INTERACTIVE_HELP)
            return []
        if op in {"ports", "status", "p"}:
            log.info("插座状态\n%s", self.format_ports())
            return []
        if op == "quit" or op == "exit":
            self.quit_requested = True
            return []
        if op == "report":
            return self.build_reports(time.monotonic(), force=True)
        if op == "swipe":
            return self._cmd_swipe(parts)
        if op == "fault":
            return self._cmd_fault(parts)
        if op == "end":
            return self._cmd_end(parts)
        raise ValueError(f"未知命令 {op}，输入 help 查看")

    def format_ports(self) -> str:
        lines = []
        now = time.monotonic()
        for port in range(self.port_count):
            session = self.sessions.get(port)
            if session is not None:
                self._touch(session, now)
            state = PORT_STATUS_TEXT.get(self._port_status(port), str(self._port_status(port)))
            if session is None:
                lines.append(f"  插座 {port + 1}: {state}")
                continue
            remain = "不限时" if session.duration_min <= 0 else f"剩余 {session.remain_minutes(now)} 分钟"
            lines.append(
                f"  插座 {port + 1}: {state} 订单={order_text(session.order)} "
                f"功率={session.power_w:.0f}W 电量={session.energy_wh:.2f}Wh {remain} 来源={session.source}"
            )
        if self.device_faults:
            lines.append("  整机故障: " + ",".join(str(code) for code in sorted(self.device_faults)))
        return "\n".join(lines)

    def _on_command(self, frame: Frame) -> list[Outbound]:
        handler = {
            CMD_START: self._on_start,
            CMD_QUERY_STATUS: self._on_query_status,
            CMD_STOP: self._on_stop,
            CMD_QUERY_INFO: self._on_query_info,
            CMD_QUERY_ARGS: self._on_query_args,
            CMD_SET_ARGS: self._on_set_args,
            CMD_REBOOT: self._on_reboot,
            CMD_QUERY_PORTS: self._on_query_ports,
            CMD_SET_VOLUME: self._on_set_volume,
            CMD_QUERY_VOLUME: self._on_query_volume,
            CMD_FAULT_RESET: self._on_fault_reset,
        }.get(frame.cmd)
        if handler is None:
            log.warning("未实现的下行指令 0x%04X，回结果=失败", frame.cmd)
            return [self._reply(frame, encode_result(False), "未实现")]
        try:
            return handler(frame)
        except ValueError as exc:
            log.warning("下行 0x%04X 无法解析: %s", frame.cmd, exc)
            return [self._reply(frame, encode_result(False), str(exc))]

    def _on_response(self, frame: Frame) -> list[Outbound]:
        if frame.cmd == CMD_CARD_QUERY:
            return self._on_card_response(frame)
        if frame.cmd == CMD_REGISTER:
            stamp = decode_unix_time(frame.payload)
            if stamp is None:
                log.warning("注册响应没有 4 字节时间")
        return []

    def _on_start(self, frame: Frame) -> list[Outbound]:
        req = decode_start_request(frame.payload)
        port = req["port"]
        if port >= self.port_count:
            return [self._reply(frame, encode_start_response(RESULT_FAIL, port, 0), "插座号超出范围")]
        now = time.monotonic()
        if port in self.sessions:
            remain = self.sessions[port].remain_minutes(now)
            return [self._reply(frame, encode_start_response(RESULT_FAIL, port, remain), "插座正在充电")]
        if req["minutes"] == 0:
            log.warning("开启充电的线上时长为 0 分钟（平台若按秒下发且不足 60 秒，Java 会整除成 0）。本口保持到停止指令")
        power = min(max(0.0, self.cfg.power_w), float(req["max_power"]))
        self.sessions[port] = Session(
            port=port,
            order=bytes(req["order"]),
            started=now,
            duration_min=req["minutes"],
            min_power=req["min_power"],
            max_power=req["max_power"],
            no_load_wait=req["no_load_wait"],
            full_wait=req["full_wait"],
            full_stop=req["full_stop"],
            power_w=power,
            source="remote",
            last_touch=now,
        )
        self.report_due = True
        remain = req["minutes"]
        summary = f"开始充电 插座={port + 1} 订单={order_text(req['order'])} 功率={power:.0f}W"
        return [
            self._reply(frame, encode_start_response(RESULT_OK, port, remain), summary),
            self._port_report(port, summary),
        ]

    def _on_query_status(self, frame: Frame) -> list[Outbound]:
        # deviceQueryChargeStatus 不读取指令内容。应答只回结果字节，细节写在日志里。
        if not frame.payload:
            return [self._reply(frame, encode_result(False), "缺少插座号")]
        port = frame.payload[0]
        if port >= self.port_count:
            return [self._reply(frame, encode_result(False), f"插座={port + 1} 超出范围")]
        session = self.sessions.get(port)
        if session is None:
            return [self._reply(frame, encode_result(True), f"插座={port + 1} 空闲")]
        now = time.monotonic()
        self._touch(session, now)
        remain = "不限时" if session.duration_min <= 0 else f"剩余={session.remain_minutes(now)}分钟"
        summary = (
            f"插座={port + 1} 充电中 {remain} 功率={session.power_w:.0f}W "
            f"电量={session.energy_wh:.2f}Wh 订单={order_text(session.order)}"
        )
        return [self._reply(frame, encode_result(True), summary)]

    def _on_stop(self, frame: Frame) -> list[Outbound]:
        if not frame.payload:
            return [self._reply(frame, encode_stop_response(RESULT_FAIL, 0, 0, 0, b"0"), "缺少插座号")]
        port = frame.payload[0]
        now = time.monotonic()
        session = self.sessions.pop(port, None)
        if session is None:
            body = encode_stop_response(RESULT_FAIL, port, 0, 0, b"0")
            return [self._reply(frame, body, f"插座={port + 1} 未在充电")]
        self._touch(session, now)
        elapsed = max(0, int(now - session.started))
        minutes = elapsed // 60
        body = encode_stop_response(RESULT_OK, port, minutes, _soc(session, now), session.order)
        summary = (
            f"停止充电 插座={port + 1} 已充={minutes}分钟 "
            f"电量={session.energy_wh:.2f}Wh 订单={order_text(session.order)}"
        )
        # 平台把 0x0007 应答本身解析成结束订单，这里不再另发 0x0010，避免同一订单结束两次。
        return [self._reply(frame, body, summary), self._port_report(port, summary)]

    def _on_query_info(self, frame: Frame) -> list[Outbound]:
        # 服务端用 deviceRegister 解析 0x0011，指令内容与注册相同，通讯标识是设备应答。
        payload = encode_register(
            signal=self.cfg.signal,
            soft_version=self.cfg.soft_version,
            hard_version=self.cfg.hard_version,
            comm_version=self.cfg.comm_version,
            ports=self.port_count,
            device_type=self.cfg.device_type,
            reserved=self.cfg.register_tail,
        )
        return [self._reply(frame, payload, "")]

    def _on_query_args(self, frame: Frame) -> list[Outbound]:
        return [self._reply(frame, self.args.to_bytes(), "")]

    def _on_set_args(self, frame: Frame) -> list[Outbound]:
        updated = DeviceArgs.from_bytes(frame.payload)
        self.args = updated
        if updated.heart_time > 0:
            self.heartbeat_interval = float(updated.heart_time)
        if updated.charge_report > 0:
            self.report_interval = float(updated.charge_report)
        if updated.work_voltage:
            self.voltage = float(updated.work_voltage)
        return [self._reply(frame, encode_result(True), updated.summary())]

    def _on_reboot(self, frame: Frame) -> list[Outbound]:
        dropped = len(self.sessions)
        self.sessions.clear()
        self.pending_swipes.clear()
        self.close_requested = True
        log.info("收到重启，丢弃进行中的订单 %s 笔，应答后断开并重新注册", dropped)
        return [self._reply(frame, encode_result(True), "结果=成功")]

    def _on_query_ports(self, frame: Frame) -> list[Outbound]:
        # deviceResatrt 只读第 1 个字节。各口状态放在日志，不另造线上字段。
        bits = []
        for port in range(self.port_count):
            state = self._port_status(port)
            bits.append(f"{port + 1}:{PORT_STATUS_TEXT.get(state, state)}")
        return [self._reply(frame, encode_result(True), " ".join(bits))]

    def _on_set_volume(self, frame: Frame) -> list[Outbound]:
        req = decode_volume_request(frame.payload)
        self.volume_broadcast = req["broadcast"]
        self.volume_disturb = req["no_disturb"]
        self.volume_level = req["level"]
        body = encode_volume_response(RESULT_OK, self.volume_broadcast, self.volume_disturb, self.volume_level)
        return [self._reply(frame, body, "")]

    def _on_query_volume(self, frame: Frame) -> list[Outbound]:
        body = encode_volume_response(RESULT_OK, self.volume_broadcast, self.volume_disturb, self.volume_level)
        return [self._reply(frame, body, "")]

    def _on_fault_reset(self, frame: Frame) -> list[Outbound]:
        req = decode_fault_reset(frame.payload)
        if req["enable"] not in (0, 1):
            return [self._reply(frame, encode_result(False), "开关只能是 0 或 1")]
        changed: list[int] = []
        if req["enable"] == 1:
            code = req["error_code"]
            if code == 0:
                changed = list(self.fault_ports)
                self.device_faults.clear()
                self.fault_ports.clear()
            else:
                self.device_faults.discard(code)
                for port in list(self.fault_ports):
                    if code in self.fault_ports[port]:
                        changed.append(port)
                        self.fault_ports[port].discard(code)
                        if not self.fault_ports[port]:
                            del self.fault_ports[port]
        else:
            log.info("复位故障开关为关，不清除已有故障")
        summary = f"开关={req['enable']} 错误代码={req['error_code']}"
        outbound = [self._reply(frame, encode_result(True), summary)]
        for port in changed:
            outbound.append(self._port_report(port, summary))
        return outbound

    def _on_card_response(self, frame: Frame) -> list[Outbound]:
        try:
            info = decode_card_response(frame.payload)
        except ValueError as exc:
            log.warning("刷卡应答无法解析: %s", exc)
            return []
        pending = self.pending_swipes.pop(frame.msg_id, None)
        if pending is None and len(self.pending_swipes) == 1:
            _msg, pending = self.pending_swipes.popitem()
        result = info["result"]
        if pending is None:
            log.info("刷卡应答结果=%s，没有对应的本地刷卡", CARD_RESULTS.get(result, result))
            return []
        if result != RESULT_OK or "order" not in info:
            log.info(
                "刷卡未开始充电 插座=%s 结果=%s",
                pending["port"] + 1,
                CARD_RESULTS.get(result, result),
            )
            return []
        port = pending["port"]
        if port in self.sessions:
            log.warning("刷卡成功但插座 %s 已在充电，忽略", port + 1)
            return []
        now = time.monotonic()
        power = min(max(0.0, self.cfg.power_w), float(info["max_power"]))
        self.sessions[port] = Session(
            port=port,
            order=bytes(info["order"]),
            started=now,
            duration_min=info["minutes"],
            min_power=info["min_power"],
            max_power=info["max_power"],
            no_load_wait=info["no_load_wait"],
            full_wait=info["full_wait"],
            full_stop=info["full_stop"],
            power_w=power,
            source="card",
            last_touch=now,
            card=bytes(pending["card"]),
            card_tail=pending["tail"],
        )
        self.report_due = True
        summary = f"刷卡开电 插座={port + 1} 订单={order_text(info['order'])} 时长={info['minutes']}分钟"
        return [self._port_report(port, summary)]

    def _cmd_swipe(self, parts: list[str]) -> list[Outbound]:
        if len(parts) not in (3, 4):
            raise ValueError("用法: swipe <插座1起> <8位或10位十六进制卡号> [尾字节]")
        port = int(parts[1]) - 1
        if not 0 <= port < self.port_count:
            raise ValueError("插座超出范围")
        if port in self.sessions:
            raise ValueError("插座正在充电")
        if any(item["port"] == port for item in self.pending_swipes.values()):
            raise ValueError("该插座已有未完成的刷卡请求")
        card, tail = _parse_card(parts[2])
        if len(parts) == 4:
            tail = _parse_byte(parts[3])
        msg = self.alloc_msg()
        self.pending_swipes[msg] = {"port": port, "card": card, "tail": tail, "at": time.monotonic()}
        summary = f"插座={port + 1} 卡号={card.hex()} 尾字节=0x{tail:02X}"
        return [Outbound(COMM_DEVICE_REQ, CMD_CARD_QUERY, encode_card_query(card, tail), msg, summary)]

    def _cmd_fault(self, parts: list[str]) -> list[Outbound]:
        if len(parts) not in (3, 4):
            raise ValueError("用法: fault <插座1起|ff> <故障码> [0解除|1产生]")
        status = 1 if len(parts) < 4 else int(parts[3])
        if status not in (0, 1):
            raise ValueError("故障状态只能是 0 或 1")
        code = int(parts[2], 0) & 0xFF
        token = parts[1].lower()
        socket = 0xFF if token in {"ff", "all", "device"} else int(token) - 1
        if socket != 0xFF and not 0 <= socket < self.port_count:
            raise ValueError("插座超出范围")
        if socket == 0xFF:
            if status == 1:
                self.device_faults.add(code)
            else:
                self.device_faults.discard(code)
        else:
            bucket = self.fault_ports.setdefault(socket, set())
            if status == 1:
                bucket.add(code)
            else:
                bucket.discard(code)
                if not bucket:
                    del self.fault_ports[socket]
        where = "整机" if socket == 0xFF else f"插座={socket + 1}"
        state = "产生" if status else "解除"
        summary = f"{where} 故障码={code} {state}"
        outbound = [self._report(CMD_FAULT, encode_fault(socket, code, status, 0), summary)]
        if socket != 0xFF:
            outbound.append(self._port_report(socket, summary))
        return outbound

    def _cmd_end(self, parts: list[str]) -> list[Outbound]:
        if len(parts) != 2:
            raise ValueError("用法: end <插座1起>")
        port = int(parts[1]) - 1
        if port not in self.sessions:
            raise ValueError("该插座没有进行中的订单")
        return self._finish(port, END_REASON_LOCAL, time.monotonic())

    def _finish(self, port: int, reason: int, now: float) -> list[Outbound]:
        session = self.sessions.get(port)
        if session is None:
            return []
        self._touch(session, now)
        self.sessions.pop(port, None)
        elapsed = max(0, int(now - session.started))
        minutes = elapsed // 60
        energy = _clamp(round(session.energy_wh), 0, 0xFFFF)
        power = _clamp(round(session.power_w), 0, 0xFFFF)
        reason_text = END_REASON_TEXT.get(reason, str(reason))
        summary = (
            f"充电结束 原因={reason_text} 插座={port + 1} 订单={order_text(session.order)} "
            f"耗电={energy} 功率={power}W 秒数={elapsed}"
        )
        if session.source == "card":
            payload = encode_card_order(port, minutes, session.order, session.card, session.card_tail)
            notice: Outbound = self._request(CMD_CARD_ORDER, payload, summary)
        else:
            payload = encode_end(reason, port, session.order, energy, power, elapsed)
            notice = self._report(CMD_END, payload, summary)
        return [notice, self._port_report(port, summary)]

    def _expiry_reason(self, session: Session, now: float) -> int | None:
        if session.duration_min > 0 and now - session.started >= session.duration_min * 60:
            return END_REASON_TIME
        if session.power_w < session.min_power:
            if session.low_since is None:
                session.low_since = now
            if now - session.low_since >= max(0, session.no_load_wait):
                return END_REASON_NOLOAD
        else:
            session.low_since = None
        if session.full_stop == 1 and _soc(session, now) >= 100:
            if session.full_since is None:
                session.full_since = now
            if now - session.full_since >= max(0, session.full_wait):
                return END_REASON_FULL
        else:
            session.full_since = None
        return None

    def _port_status(self, port: int) -> int:
        if self.fault_ports.get(port):
            return PORT_FAULT
        if port in self.sessions:
            return PORT_CHARGING
        return PORT_IDLE

    def _port_report(self, port: int, summary: str) -> Outbound:
        status = self._port_status(port)
        return self._report(CMD_PORT_STATUS, encode_port_status(port, status), summary)

    def _touch(self, session: Session, now: float) -> None:
        if session.last_touch is None:
            session.last_touch = now
            return
        dt = now - session.last_touch
        if dt > 0:
            session.energy_wh += session.power_w * dt / 3600.0
            session.last_touch = now

    def _request(self, cmd: int, payload: bytes, summary: str) -> Outbound:
        return Outbound(COMM_DEVICE_REQ, cmd, payload, self.alloc_msg(), summary)

    def _report(self, cmd: int, payload: bytes, summary: str) -> Outbound:
        return Outbound(COMM_DEVICE_REPORT, cmd, payload, self.alloc_msg(), summary)

    def _reply(self, frame: Frame, payload: bytes, summary: str) -> Outbound:
        return Outbound(COMM_DEVICE_RSP, frame.cmd, payload, frame.msg_id, summary)


def _soc(session: Session, now: float) -> int:
    """停止应答里的 1 字节「电瓶电量」。源码没有换算公式，这里用 50% 起、每分钟 +1、上限 100。"""

    minutes = max(0, int(now - session.started) // 60)
    return _clamp(50 + minutes, 0, 100)


def _clamp(value: int, low: int, high: int) -> int:
    return max(low, min(int(value), high))


def _parse_byte(text: str) -> int:
    token = text.strip()
    try:
        value = int(token, 0)
    except ValueError:
        value = int(token, 16)
    if not 0 <= value <= 255:
        raise ValueError("字节超出 0..255")
    return value


def _parse_card(text: str) -> tuple[bytes, int]:
    raw = text.strip().replace(" ", "")
    if raw.lower().startswith("0x"):
        raw = raw[2:]
    if len(raw) not in (8, 10) or any(ch not in "0123456789abcdefABCDEF" for ch in raw):
        raise ValueError("卡号需要 8 或 10 个十六进制字符（4 字节，或 4 字节加 1 字节尾）")
    blob = bytes.fromhex(raw)
    if len(blob) == 4:
        return blob, 0
    return blob[:4], blob[4]


INTERACTIVE_HELP = """\
命令（插座号从 1 开始，与业务口一致；线上字节仍是 0 起始）:
  ports                         查看各口状态
  report                        立刻上报 0x0030 和 0x0048
  swipe <口> <卡号hex> [尾字节]  发送 0x0008，卡号 4 字节
  fault <口|ff> <故障码> [0|1]   上报 0x0016，ff 为整机
  end <口>                      本地结束：平台单上报 0x0010，刷卡单上报 0x0009
  quit                          退出
"""
