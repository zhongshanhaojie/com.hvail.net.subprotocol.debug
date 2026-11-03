"""对照 LvccProtoDecode.main 里的完整抓包，以及 Java 组包字段顺序。"""

from __future__ import annotations

import socket
import subprocess
import sys
import threading
import time
import unittest
from pathlib import Path

from lvcc_sim.codec import (
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
    END_REASON_TIME,
    DeviceArgs,
    charge_minutes_from_seconds,
    decode_card_order,
    decode_card_query,
    decode_end,
    decode_fault,
    decode_power_report,
    decode_realtime,
    decode_register,
    decode_start_request,
    decode_stop_response,
    encode_fault_reset,
    encode_realtime,
    encode_register,
    encode_start_request,
    encode_volume_response,
)
from lvcc_sim.frame import (
    COMM_DEVICE_REPORT,
    COMM_DEVICE_REQ,
    COMM_DEVICE_RSP,
    COMM_GATEWAY_CMD,
    COMM_GATEWAY_RSP,
    FrameDecoder,
    java_xor,
    pack_frame,
    parse_frame,
    parse_hex,
)
from lvcc_sim.handlers import Device, SimConfig
from lvcc_sim.simulator import Simulator

ROOT = Path(__file__).resolve().parents[1]

# LvccProtoDecode.main 中长度完整、校验可复核的抓包。短横线那两条 0x0048 在源码里被截断，不作为夹具。
SAMPLES = {
    "register": "7e 5d 7d 7f 00 22 00 01 10 0f 38 36 38 30 38 39 30 35 37 36 37 32 34 39 39 00 01 00 01 1d 01 1e 00 01 00 01 0a 2f 01 0a",
    "stop": "7e 5d 7d 7f 00 33 00 01 11 0f 38 36 33 30 36 39 30 35 39 33 39 33 34 38 39 03 d7 00 07 01 01 00 00 00 30 30 30 30 30 30 30 30 30 30 30 30 31 32 33 34 35 36 00 00 00 00 f9",
    "power": "7e 5d 7d 7f 00 1b 00 01 12 0f 38 36 36 32 36 32 30 34 35 38 31 34 36 31 32 00 05 00 30 1a 00 00 04",
    "card": "7e 5d 7d 7f 00 1d 00 01 10 0f 38 36 36 37 31 34 30 34 30 30 33 34 35 31 33 00 31 00 08 7d 0e 65 31 27 19",
    "card2": "7e 5d 7d 7f 00 1d 00 01 10 0f 38 36 30 30 34 38 30 37 31 33 37 35 36 36 39 00 1a 00 08 cc 76 60 62 b8 30",
    "card_order": "7e 5d 7d 7f 00 34 00 01 10 0f 38 36 30 30 34 38 30 37 31 33 37 35 36 36 39 00 58 00 09 01 02 58 30 31 38 30 33 33 32 34 39 33 38 34 38 39 31 36 37 38 37 32 cc 76 60 62 b8 2d",
    "volume": "7e 5d 7d 7f 00 1c 00 01 11 0f 38 36 36 37 31 34 30 34 30 30 33 34 35 31 33 f3 6a 00 28 01 01 00 01 91",
    "card_order_report": "7e 5d 7d 7f 00 34 00 01 12 0f 38 36 36 37 31 34 30 34 30 30 33 34 35 31 33 00 35 00 09 05 00 32 32 30 32 30 30 34 31 36 31 31 34 37 35 38 33 36 37 33 39 39 7d 0e 65 31 27 25",
    "start": "7e 5d 7d 7f 00 1c 00 01 11 0f 38 36 34 39 37 39 30 37 35 36 35 34 37 36 34 06 a9 00 05 04 09 01 2d a9",
    "heartbeat": "7E-5D-7D-7F-00-18-00-01-12-0F-38-36-32-39-36-36-30-37-33-33-32-31-38-34-35-07-20-00-02-01",
}


class FrameGoldenTest(unittest.TestCase):
    def test_captures_round_trip_and_match_java_xor(self) -> None:
        for name, text in SAMPLES.items():
            raw = parse_hex(text)
            self.assertEqual(java_xor(raw), raw[-1], name)
            frame = parse_frame(raw)
            self.assertTrue(frame.checksum_ok, name)
            self.assertEqual(frame.version, 0x0001, name)
            again = pack_frame(frame.device_id, frame.cmd, frame.msg_id, frame.comm, frame.payload)
            self.assertEqual(again, raw, name)

    def test_register_fields_and_device_builder(self) -> None:
        frame = parse_frame(parse_hex(SAMPLES["register"]))
        self.assertEqual(frame.cmd, CMD_REGISTER)
        self.assertEqual(frame.comm, COMM_DEVICE_REQ)
        self.assertEqual(frame.device_id, "868089057672499")
        self.assertEqual(frame.msg_id, 1)
        info = decode_register(frame.payload)
        self.assertEqual(info["signal"], 0x1D)
        self.assertEqual(info["soft_version"], 0x011E)
        self.assertEqual(info["hard_version"], 1)
        self.assertEqual(info["comm_version"], 1)
        self.assertEqual(info["ports"], 10)
        self.assertEqual(info["device_type"], 0x2F)
        self.assertEqual(info["reserved"], 0x01)
        self.assertEqual(frame.payload, encode_register(0x1D, 0x011E, 1, 1, 10, 0x2F, 0x01))

        device = Device(SimConfig())
        outbound = device.build_register()
        packed = pack_frame(device.device_id, outbound.cmd, outbound.msg_id, outbound.comm, outbound.payload)
        self.assertEqual(packed, parse_hex(SAMPLES["register"]))

    def test_heartbeat_body_is_empty(self) -> None:
        frame = parse_frame(parse_hex(SAMPLES["heartbeat"]))
        self.assertEqual(frame.cmd, CMD_HEARTBEAT)
        self.assertEqual(frame.comm, COMM_DEVICE_REPORT)
        self.assertEqual(frame.device_id, "862966073321845")
        self.assertEqual(frame.msg_id, 0x0720)
        self.assertEqual(frame.payload, b"")
        device = Device(SimConfig(device_id=frame.device_id))
        outbound = device.build_heartbeat()
        self.assertEqual(outbound.cmd, CMD_HEARTBEAT)
        self.assertEqual(outbound.comm, COMM_DEVICE_REPORT)
        self.assertEqual(outbound.payload, b"")

    def test_stream_splits_and_sticky_packets(self) -> None:
        first = parse_hex(SAMPLES["register"])
        second = parse_hex(SAMPLES["heartbeat"])
        blob = b"\x00\x11" + first + second
        decoder = FrameDecoder()
        found = []
        for index in range(0, len(blob), 5):
            found.extend(decoder.feed(blob[index : index + 5]))
        found.extend(decoder.feed(b""))
        self.assertEqual([item.raw for item in found], [first, second])

    def test_bad_checksum_is_flagged_not_dropped(self) -> None:
        raw = bytearray(parse_hex(SAMPLES["heartbeat"]))
        raw[-1] ^= 0xFF
        frame = parse_frame(bytes(raw))
        self.assertFalse(frame.checksum_ok)
        self.assertEqual(frame.cmd, CMD_HEARTBEAT)
        self.assertEqual(frame.payload, b"")


class PayloadLayoutTest(unittest.TestCase):
    def test_start_charge_matches_vo_byte_order(self) -> None:
        # charge_start:123:1:1:60 → 端口 0，最小 25W，最大 800W，空载/充满等待 30 秒，时长 60/60=1 分钟
        self.assertEqual(charge_minutes_from_seconds(60), 1)
        self.assertEqual(charge_minutes_from_seconds(59), 0)
        body = encode_start_request(
            port=0,
            min_power=25,
            max_power=800,
            no_load_wait=30,
            full_wait=30,
            minutes=charge_minutes_from_seconds(60),
            order="123",
            billing=1,
            turn_on=1,
            full_stop=1,
        )
        self.assertEqual(len(body), 34)
        self.assertEqual(body[0], 0)
        self.assertEqual(int.from_bytes(body[1:3], "big"), 25)
        self.assertEqual(int.from_bytes(body[3:5], "big"), 800)
        self.assertEqual(int.from_bytes(body[5:7], "big"), 30)
        self.assertEqual(int.from_bytes(body[7:9], "big"), 30)
        self.assertEqual(int.from_bytes(body[9:11], "big"), 1)
        self.assertEqual(body[11:31], b"00000000000000000123")
        self.assertEqual(body[31:34], bytes((1, 1, 1)))
        self.assertEqual(
            body.hex(),
            "0000190320001e001e0001" + "30" * 17 + "313233" + "010101",
        )
        decoded = decode_start_request(body)
        self.assertEqual(decoded["order"], b"00000000000000000123")

    def test_stop_capture_and_fault_reset_bytes(self) -> None:
        frame = parse_frame(parse_hex(SAMPLES["stop"]))
        self.assertEqual(frame.cmd, CMD_STOP)
        self.assertEqual(frame.comm, COMM_DEVICE_RSP)
        info = decode_stop_response(frame.payload)
        self.assertEqual(info["result"], 1)
        self.assertEqual(info["port"], 1)
        self.assertEqual(info["minutes"], 0)
        self.assertEqual(info["battery"], 0)
        self.assertEqual(info["order"], b"000000000000123456\x00\x00")
        self.assertEqual(len(frame.payload), 27)
        self.assertEqual(encode_fault_reset(1, 12), bytes((1, 12)))

    def test_card_captures(self) -> None:
        query = parse_frame(parse_hex(SAMPLES["card"]))
        card = decode_card_query(query.payload)
        self.assertEqual(card["card"].hex(), "7d0e6531")
        self.assertEqual(card["tail"], 0x27)
        order = decode_card_order(parse_frame(parse_hex(SAMPLES["card_order"])).payload)
        self.assertEqual(order["port"], 1)
        self.assertEqual(order["minutes"], 0x0258)
        self.assertEqual(order["order"].decode("ascii"), "01803324938489167872")
        self.assertEqual(order["card"].hex(), "cc766062")
        self.assertEqual(order["tail"], 0xB8)

    def test_power_and_realtime_use_java_indexes(self) -> None:
        power = decode_power_report(parse_frame(parse_hex(SAMPLES["power"])).payload)
        self.assertEqual(power["signal"], 0x1A)
        self.assertEqual(power["powers"], [0])

        rows = [(10, 22000, 1000, 35), (0, 22000, 0, 36)]
        body = encode_realtime(0x16, rows, 28, 1)
        count = 2
        power_base = 2
        voltage_base = power_base + count * 2
        current_base = voltage_base + count * 2
        temp_base = current_base + count * 2
        env_off = temp_base + count * 2
        machine_off = env_off + 2
        self.assertEqual(body[0], 0x16)
        self.assertEqual(body[1], count)
        self.assertEqual(int.from_bytes(body[power_base : power_base + 2], "big"), 10)
        self.assertEqual(int.from_bytes(body[voltage_base : voltage_base + 2], "big"), 22000)
        self.assertEqual(int.from_bytes(body[current_base : current_base + 2], "big"), 1000)
        self.assertEqual(int.from_bytes(body[temp_base + 2 : temp_base + 4], "big"), 36)
        self.assertEqual(int.from_bytes(body[env_off : env_off + 2], "big"), 28)
        self.assertEqual(int.from_bytes(body[machine_off : machine_off + 2], "big"), 1)
        decoded = decode_realtime(body)
        self.assertEqual(decoded["energy"], [10, 0])
        self.assertEqual(decoded["voltage"], [22000, 22000])
        self.assertEqual(decoded["current"], [1000, 0])
        self.assertEqual(decoded["machine_count"], 1)

    def test_fault_upload_int_detail_and_device_args(self) -> None:
        body = bytes((0xFF, 5, 1)) + (0x01020304).to_bytes(4, "big")
        info = decode_fault(body)
        self.assertTrue(info["whole"])
        self.assertEqual(info["code"], 5)
        self.assertEqual(info["status"], 1)
        self.assertEqual(info["detail"], 0x01020304)
        self.assertIn("烟感", info["text"])

        args = DeviceArgs(
            min_power=15,
            max_power=750,
            no_load_wait=15,
            full_wait=1800,
            heart_time=30,
            charge_report=90,
            work_voltage=230,
            info_upload_hours=12,
            temp_alarm=80,
            smoke=1,
        )
        self.assertEqual(len(args.to_bytes()), 15)
        self.assertEqual(DeviceArgs.from_bytes(args.to_bytes()), args)

    def test_volume_capture_repack(self) -> None:
        frame = parse_frame(parse_hex(SAMPLES["volume"]))
        self.assertEqual(frame.cmd, CMD_QUERY_VOLUME)
        self.assertEqual(frame.msg_id, 0xF36A)
        self.assertEqual(frame.payload, encode_volume_response(1, 1, 0, 1))


class DeviceBehaviorTest(unittest.TestCase):
    def _device(self) -> Device:
        return Device(
            SimConfig(
                device_id="868089057672499",
                port_count=2,
                power_w=220,
                voltage=220,
                heartbeat=30,
                report_interval=20,
            )
        )

    def _down(self, device: Device, cmd: int, msg: int, payload: bytes):
        raw = pack_frame(device.device_id, cmd, msg, COMM_GATEWAY_CMD, payload)
        return device.handle_frame(parse_frame(raw))

    def test_start_query_stop_and_reports(self) -> None:
        device = self._device()
        body = encode_start_request(0, 25, 800, 30, 30, 60, "123")
        outs = self._down(device, CMD_START, 7, body)
        self.assertEqual(outs[0].comm, COMM_DEVICE_RSP)
        self.assertEqual(outs[0].msg_id, 7)
        self.assertEqual(outs[0].payload[0], 1)
        self.assertEqual(outs[0].payload[1], 0)
        self.assertEqual(int.from_bytes(outs[0].payload[2:4], "big"), 60)
        self.assertEqual(outs[1].cmd, CMD_PORT_STATUS)
        self.assertEqual(outs[1].comm, COMM_DEVICE_REPORT)
        self.assertEqual(outs[1].payload, bytes((0, 1)))

        busy = self._down(device, CMD_START, 8, body)
        self.assertEqual(busy[0].payload[0], 2)

        status = self._down(device, CMD_QUERY_STATUS, 9, bytes((0,)))
        self.assertEqual(status[0].payload, bytes((1,)))
        self.assertIn("充电中", status[0].summary)
        self.assertIn("123", status[0].summary)

        now = time.monotonic()
        session = device.sessions[0]
        session.last_touch = now - 3600
        session.power_w = 220
        reports = device.build_reports(now)
        power = decode_power_report(next(item.payload for item in reports if item.cmd == CMD_POWER_REPORT))
        self.assertEqual(power["powers"], [220, 0])
        realtime = decode_realtime(next(item.payload for item in reports if item.cmd == CMD_REALTIME))
        self.assertEqual(realtime["ports"], 2)
        self.assertEqual(realtime["energy"][0], 220)
        self.assertEqual(realtime["voltage"][0], 22000)
        self.assertEqual(realtime["current"][0], 1000)
        self.assertAlmostEqual(session.energy_wh, 220, delta=0.05)

        stopped = self._down(device, CMD_STOP, 10, bytes((0,)))
        self.assertTrue(all(item.cmd != CMD_END for item in stopped))
        info = decode_stop_response(stopped[0].payload)
        self.assertEqual(info["result"], 1)
        self.assertEqual(info["order"], b"00000000000000000123")
        self.assertEqual(stopped[1].payload, bytes((0, 0)))
        self.assertNotIn(0, device.sessions)

    def test_natural_end_uses_0x0010(self) -> None:
        device = self._device()
        body = encode_start_request(1, 25, 800, 30, 30, 1, "9")
        self._down(device, CMD_START, 1, body)
        session = device.sessions[1]
        session.started = time.monotonic() - 120
        session.last_touch = session.started
        outs = device.poll(time.monotonic())
        end = next(item for item in outs if item.cmd == CMD_END)
        self.assertEqual(end.comm, COMM_DEVICE_REPORT)
        decoded = decode_end(end.payload)
        self.assertEqual(decoded["reason"], END_REASON_TIME)
        self.assertEqual(decoded["port"], 1)
        self.assertEqual(decoded["order"], b"00000000000000000009")
        self.assertGreater(decoded["elapsed"], 60)

    def test_query_info_args_volume_reboot_fault(self) -> None:
        device = self._device()
        info = self._down(device, CMD_QUERY_INFO, 4, b"")
        self.assertEqual(info[0].comm, COMM_DEVICE_RSP)
        self.assertEqual(info[0].msg_id, 4)
        self.assertEqual(decode_register(info[0].payload)["ports"], 2)

        args = self._down(device, CMD_QUERY_ARGS, 5, b"")
        self.assertEqual(len(args[0].payload), 15)

        updated = DeviceArgs(heart_time=12, charge_report=40, work_voltage=230)
        applied = self._down(device, CMD_SET_ARGS, 6, updated.to_bytes())
        self.assertEqual(applied[0].payload, bytes((1,)))
        self.assertEqual(device.heartbeat_interval, 12)
        self.assertEqual(device.report_interval, 40)
        self.assertEqual(device.voltage, 230)

        volume = self._down(device, CMD_SET_VOLUME, 0xF36A, bytes((1, 0, 3)))
        self.assertEqual(volume[0].msg_id, 0xF36A)
        self.assertEqual(volume[0].payload, encode_volume_response(1, 1, 0, 3))
        queried = self._down(device, CMD_QUERY_VOLUME, 2, b"")
        self.assertEqual(queried[0].payload[3], 3)

        ports = self._down(device, CMD_QUERY_PORTS, 3, b"")
        self.assertEqual(ports[0].payload, bytes((1,)))
        self.assertIn("空闲", ports[0].summary)

        fault = device.command_line("fault ff 5 1")
        self.assertEqual(fault[0].cmd, CMD_FAULT)
        decoded = decode_fault(fault[0].payload)
        self.assertTrue(decoded["whole"])
        self.assertEqual(decoded["code"], 5)
        reset = self._down(device, CMD_FAULT_RESET, 11, encode_fault_reset(1, 5))
        self.assertEqual(reset[0].payload, bytes((1,)))
        self.assertEqual(device.device_faults, set())

        self._down(device, CMD_START, 12, encode_start_request(0, 25, 800, 30, 30, 10, "1"))
        reboot = self._down(device, CMD_REBOOT, 13, b"")
        self.assertEqual(reboot[0].payload, bytes((1,)))
        self.assertTrue(device.close_requested)
        self.assertEqual(device.sessions, {})

    def test_card_query_needs_tail_byte(self) -> None:
        device = self._device()
        outbound = device.command_line("swipe 1 CC766062 B8")
        self.assertEqual(outbound[0].cmd, CMD_CARD_QUERY)
        self.assertEqual(outbound[0].comm, COMM_DEVICE_REQ)
        self.assertEqual(len(outbound[0].payload), 5)
        self.assertEqual(decode_card_query(outbound[0].payload)["card"].hex(), "cc766062")


class TcpSessionTest(unittest.TestCase):
    def test_register_start_and_stop_against_local_server(self) -> None:
        stop = threading.Event()
        ready = threading.Event()
        holder: dict = {}
        errors: list[str] = []

        def serve() -> None:
            conn = None
            srv = socket.socket()
            try:
                srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                srv.bind(("127.0.0.1", 0))
                srv.listen(1)
                srv.settimeout(5)
                holder["port"] = srv.getsockname()[1]
                ready.set()
                conn, _addr = srv.accept()
                conn.settimeout(5)
                decoder = FrameDecoder()
                stage = "register"
                device_id = "868089057672499"
                while not stop.is_set():
                    data = conn.recv(4096)
                    if not data:
                        break
                    for frame in decoder.feed(data):
                        if stage == "register" and frame.cmd == CMD_REGISTER and frame.comm == COMM_DEVICE_REQ:
                            holder["register"] = frame
                            conn.sendall(
                                pack_frame(
                                    device_id,
                                    CMD_REGISTER,
                                    frame.msg_id,
                                    COMM_GATEWAY_RSP,
                                    (1_700_000_000).to_bytes(4, "big"),
                                )
                            )
                            conn.sendall(
                                pack_frame(
                                    device_id,
                                    CMD_START,
                                    42,
                                    COMM_GATEWAY_CMD,
                                    encode_start_request(0, 25, 800, 30, 30, 60, "123"),
                                )
                            )
                            stage = "start"
                        elif frame.cmd == CMD_START and frame.comm == COMM_DEVICE_RSP:
                            holder["start"] = frame
                            conn.sendall(pack_frame(device_id, CMD_STOP, 43, COMM_GATEWAY_CMD, bytes((0,))))
                            stage = "stop"
                        elif frame.cmd == CMD_STOP and frame.comm == COMM_DEVICE_RSP:
                            holder["stop"] = frame
                            stop.set()
                            return
            except Exception as exc:  # noqa: BLE001 - 测试服务器把异常带回断言
                errors.append(repr(exc))
                stop.set()
            finally:
                if conn is not None:
                    conn.close()
                srv.close()

        thread = threading.Thread(target=serve, daemon=True)
        thread.start()
        self.assertTrue(ready.wait(2))
        cfg = SimConfig(
            host="127.0.0.1",
            port=holder["port"],
            device_id="868089057672499",
            port_count=2,
            heartbeat=30,
            reconnect=False,
            interactive=False,
        )
        code = Simulator(cfg).run(stop_event=stop)
        thread.join(3)
        self.assertEqual(errors, [])
        self.assertEqual(code, 0)
        self.assertEqual(holder["register"].device_id, "868089057672499")
        self.assertTrue(holder["register"].checksum_ok)
        self.assertEqual(holder["start"].msg_id, 42)
        self.assertEqual(holder["start"].payload[0], 1)
        self.assertEqual(holder["start"].payload[1], 0)
        stopped = decode_stop_response(holder["stop"].payload)
        self.assertEqual(stopped["result"], 1)
        self.assertEqual(stopped["order"], b"00000000000000000123")
        self.assertEqual(holder["stop"].msg_id, 43)


class CliTest(unittest.TestCase):
    def test_module_help(self) -> None:
        proc = subprocess.run(
            [sys.executable, "-m", "lvcc_sim", "--help"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(proc.returncode, 0, proc.stderr)
        self.assertIn("--device-id", proc.stdout)
        self.assertIn("868089057672499", proc.stdout)
        self.assertIn("29001", proc.stdout)


if __name__ == "__main__":
    unittest.main()
