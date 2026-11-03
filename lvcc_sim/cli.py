"""命令行入口。"""

from __future__ import annotations

import argparse
import logging
import os
import sys

from lvcc_sim import __version__
from lvcc_sim.handlers import SimConfig
from lvcc_sim.simulator import Simulator


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="lvcc_sim",
        description="小吖 LVCC（驴充充）充电桩设备侧 TCP 模拟器。作为客户端连接平台 Netty 端口。",
        epilog=(
            "示例: python -m lvcc_sim --host 127.0.0.1 --port 29001 "
            "--device-id 868089057672499 --ports 10 --heartbeat 30 --power 220"
        ),
    )
    parser.add_argument("--host", default=os.environ.get("LVCC_HOST", "127.0.0.1"), help="平台 TCP 地址，环境变量 LVCC_HOST")
    parser.add_argument(
        "--port",
        type=int,
        default=_env_int("LVCC_PORT", 29001),
        help="平台 TCP 端口，环境变量 LVCC_PORT，默认 29001",
    )
    parser.add_argument(
        "--device-id",
        default=os.environ.get("LVCC_DEVICE_ID", "868089057672499"),
        help="设备号，ASCII，通常是 15 位 IMEI 样的字符串。环境变量 LVCC_DEVICE_ID",
    )
    parser.add_argument(
        "--ports",
        type=int,
        default=_env_int("LVCC_PORTS", 10),
        help="插座数量，写入注册帧。环境变量 LVCC_PORTS，默认 10",
    )
    parser.add_argument(
        "--heartbeat",
        type=float,
        default=_env_float("LVCC_HEARTBEAT", 30),
        help="心跳间隔（秒）。环境变量 LVCC_HEARTBEAT，默认 30",
    )
    parser.add_argument(
        "--report-interval",
        type=float,
        default=_env_float("LVCC_REPORT_INTERVAL", 20),
        help="充电中 0x0030/0x0048 上报间隔（秒）。环境变量 LVCC_REPORT_INTERVAL，默认 20",
    )
    parser.add_argument(
        "--power",
        type=float,
        default=_env_float("LVCC_POWER", 220),
        help="充电中的模拟功率（瓦）。会被开始充电帧里的最大功率封顶。环境变量 LVCC_POWER",
    )
    parser.add_argument(
        "--voltage",
        type=float,
        default=_env_float("LVCC_VOLTAGE", 220),
        help="0x0048 电压，单位伏，线上再乘 100。环境变量 LVCC_VOLTAGE",
    )
    parser.add_argument("--signal", type=_int_auto, default=0x1D, help="信号字节，默认 0x1D（与注册抓包一致）")
    parser.add_argument("--device-type", type=_int_auto, default=0x2F, help="设备类型，默认 47（10BL-1）")
    parser.add_argument("--soft-version", type=_int_auto, default=0x011E, help="主控版本，默认 0x011E")
    parser.add_argument("--hard-version", type=_int_auto, default=1, help="刷卡板版本，默认 1")
    parser.add_argument("--comm-version", type=_int_auto, default=1, help="通讯模块版本，默认 1")
    parser.add_argument("--temperature", type=int, default=35, help="端口温度原值，默认 35")
    parser.add_argument("--env-temperature", type=int, default=28, help="环境温度原值，默认 28")
    parser.add_argument("--retry", type=float, default=5, help="断线重连等待秒数，默认 5")
    parser.add_argument("--no-reconnect", action="store_true", help="失败或断开后直接退出")
    parser.add_argument("--smoke", action="store_true", help="连上后注册并发送一次心跳，约 1 秒后退出")
    parser.add_argument("--debug", action="store_true", help="打开调试日志")
    parser.add_argument("--interactive", dest="interactive", action="store_true", help="从标准输入读命令")
    parser.add_argument("--no-interactive", dest="interactive", action="store_false", help="不读标准输入")
    parser.set_defaults(interactive=None)
    parser.add_argument("--version", action="version", version=f"lvcc_sim {__version__}")
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    logging.basicConfig(
        level=logging.DEBUG if args.debug else logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        datefmt="%H:%M:%S",
    )
    if args.ports < 1 or args.ports > 255:
        parser.error("插座数量必须在 1..255")
    if args.heartbeat <= 0 or args.report_interval <= 0:
        parser.error("心跳和上报间隔必须大于 0")
    if args.port < 1 or args.port > 65535:
        parser.error("端口必须在 1..65535")
    try:
        args.device_id.encode("ascii")
    except UnicodeEncodeError:
        parser.error("设备号必须是 ASCII")
    if not args.device_id:
        parser.error("设备号不能为空")
    interactive = sys.stdin.isatty() if args.interactive is None else bool(args.interactive)
    if args.smoke:
        interactive = False
    cfg = SimConfig(
        device_id=args.device_id,
        host=args.host,
        port=args.port,
        port_count=args.ports,
        heartbeat=args.heartbeat,
        report_interval=args.report_interval,
        power_w=args.power,
        voltage=args.voltage,
        signal=args.signal,
        soft_version=args.soft_version,
        hard_version=args.hard_version,
        comm_version=args.comm_version,
        device_type=args.device_type,
        temperature=args.temperature,
        env_temperature=args.env_temperature,
        reconnect=not args.no_reconnect and not args.smoke,
        retry_seconds=args.retry,
        interactive=interactive,
        smoke=args.smoke,
    )
    return Simulator(cfg).run()


def _env_int(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if raw is None or raw == "":
        return default
    return int(raw, 0)


def _env_float(name: str, default: float) -> float:
    raw = os.environ.get(name)
    if raw is None or raw == "":
        return default
    return float(raw)


def _int_auto(text: str) -> int:
    return int(text, 0)


if __name__ == "__main__":
    raise SystemExit(main())
