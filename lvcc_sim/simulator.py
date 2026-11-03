"""TCP 客户端：连上后注册，随后周期心跳，并应答网关下发。"""

from __future__ import annotations

import logging
import queue
import socket
import threading
import time

from lvcc_sim.codec import describe_frame
from lvcc_sim.frame import FrameDecoder, hex_dump, pack_frame, parse_frame
from lvcc_sim.handlers import Device, Outbound, SimConfig

log = logging.getLogger("lvcc")


class SmokeDone(Exception):
    """``--smoke`` 在注册和一次心跳之后正常结束。"""


class Simulator:
    def __init__(self, cfg: SimConfig) -> None:
        self.cfg = cfg
        self.device = Device(cfg)
        self._stop = threading.Event()
        self._lines: queue.Queue[str] = queue.Queue()
        self._sock: socket.socket | None = None
        self._connected = False

    def run(self, stop_event: threading.Event | None = None) -> int:
        self._stop = stop_event or threading.Event()
        if self.cfg.interactive:
            threading.Thread(target=self._stdin_loop, name="lvcc-stdin", daemon=True).start()
            log.info("交互已打开，输入 help 查看命令")
        try:
            while not self._stop.is_set():
                reboot = False
                try:
                    self._session()
                except SmokeDone:
                    log.info("smoke 结束")
                    return 0
                except KeyboardInterrupt:
                    raise
                except (ConnectionAbortedError, ConnectionResetError, TimeoutError, OSError) as exc:
                    reboot = self.device.close_requested
                    if self._connected or reboot:
                        log.warning("连接中断: %s", exc)
                    else:
                        log.warning("连接 %s:%s 失败: %s", self.cfg.host, self.cfg.port, exc)
                if self._stop.is_set() or not self.cfg.reconnect:
                    if not self.cfg.reconnect and not self._stop.is_set():
                        log.info("未开启重连，退出")
                    break
                self.device.close_requested = False
                delay = 1.0 if reboot else self.cfg.retry_seconds
                if reboot:
                    log.info("模拟重启，%.0f 秒后重新注册", delay)
                else:
                    log.info("%.0f 秒后重连 %s:%s", delay, self.cfg.host, self.cfg.port)
                if self._sleep(delay):
                    break
            return 0 if self._connected or self.cfg.reconnect else 1
        except KeyboardInterrupt:
            log.info("已停止")
            self._stop.set()
            return 0

    def _session(self) -> None:
        log.info(
            "连接 %s:%s 设备号=%s 插座=%s 心跳=%.1f秒 功率=%.0fW",
            self.cfg.host,
            self.cfg.port,
            self.cfg.device_id,
            self.cfg.port_count,
            self.device.heartbeat_interval,
            self.cfg.power_w,
        )
        sock = socket.create_connection((self.cfg.host, self.cfg.port), timeout=5)
        self._connected = True
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        sock.settimeout(0.3)
        self._sock = sock
        decoder = FrameDecoder()
        self.device.close_requested = False
        next_heart = time.monotonic() + 0.3
        next_report = time.monotonic() + self.device.report_interval
        smoke_heart_at: float | None = None
        try:
            self._emit(self.device.build_register())
            while not self._stop.is_set():
                now = time.monotonic()
                for outbound in self.device.poll(now):
                    self._emit(outbound)
                if self.device.report_due or now >= next_report:
                    self.device.report_due = False
                    for outbound in self.device.build_reports(now):
                        self._emit(outbound)
                    if now >= next_report:
                        next_report = now + max(0.2, self.device.report_interval)
                if now >= next_heart:
                    self._emit(self.device.build_heartbeat())
                    next_heart = now + max(0.2, self.device.heartbeat_interval)
                    if self.cfg.smoke and smoke_heart_at is None:
                        smoke_heart_at = now
                self._drain()
                if self.device.quit_requested or self._stop.is_set():
                    self._stop.set()
                    break
                if self.device.close_requested:
                    raise ConnectionAbortedError("模拟重启")
                if smoke_heart_at is not None and now - smoke_heart_at >= 1.0:
                    raise SmokeDone()
                try:
                    data = sock.recv(4096)
                except socket.timeout:
                    continue
                if not data:
                    log.info("对端关闭连接")
                    break
                for frame in decoder.feed(data):
                    self._on_frame(frame)
                if self.device.close_requested:
                    raise ConnectionAbortedError("模拟重启")
        finally:
            self._sock = None
            try:
                sock.close()
            except OSError:
                pass

    def _on_frame(self, frame) -> None:
        self._log("RX", frame, "")
        if not frame.checksum_ok:
            log.warning("校验未通过，仍继续解析（服务端 BufferCheck 的异或判断是注释掉的）")
        if frame.version != 0x0001:
            log.warning("版本字段 0x%04X，协议组包固定为 0x0001", frame.version)
        for outbound in self.device.handle_frame(frame):
            self._emit(outbound)

    def _emit(self, outbound: Outbound) -> None:
        sock = self._sock
        if sock is None:
            raise ConnectionError("尚未连接")
        raw = pack_frame(self.device.device_id, outbound.cmd, outbound.msg_id, outbound.comm, outbound.payload)
        frame = parse_frame(raw)
        self._log("TX", frame, outbound.summary)
        sock.sendall(raw)

    def _log(self, direction: str, frame, extra: str) -> None:
        text = describe_frame(frame)
        if extra:
            text += " | " + extra
        log.info("%s %s", direction, text)
        log.info("  %s", hex_dump(frame.raw))

    def _drain(self) -> None:
        while True:
            try:
                line = self._lines.get_nowait()
            except queue.Empty:
                return
            if not line.strip():
                continue
            try:
                outbound = self.device.command_line(line)
            except ValueError as exc:
                log.warning("%s", exc)
                continue
            for item in outbound:
                self._emit(item)

    def _stdin_loop(self) -> None:
        while not self._stop.is_set():
            try:
                line = input()
            except EOFError:
                self._lines.put("quit")
                return
            except OSError:
                return
            self._lines.put(line)

    def _sleep(self, seconds: float) -> bool:
        """分段睡眠。返回 True 表示期间收到了停止信号。"""

        end = time.monotonic() + seconds
        while time.monotonic() < end:
            if self._stop.is_set():
                return True
            time.sleep(min(0.2, end - time.monotonic()))
        return self._stop.is_set()
