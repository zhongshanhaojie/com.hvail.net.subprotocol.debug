# 小吖 LVCC 充电桩设备模拟器

这是驴充充（LVCC）充电桩协议的**设备侧** TCP 客户端。它连接平台的 Netty / choco_net 端口，上电先注册、再周期心跳，并应答开启充电、停止充电、查状态、改配置、重启、故障复位等下行指令，同时按协议主动上报充电结束、插座状态、功率和故障。

协议以 `vendor/lvcc-java/` 里的 `com.choco.net.xiaoya.lvcc` 为准（帧组包、`EnumCallCommand`、请求/应答模型和 `LvccProtoDecode.main` 中的完整抓包）。Java 源码只作对照，不参与运行。模拟器只用 Python 3 标准库。

默认设备号 `868089057672499` 来自协议包里的注册抓包，是假的 IMEI 样式字符串，不是生产凭据。接到真实平台时换成该环境登记的设备号。

## 运行

在仓库根目录：

```bash
python -m lvcc_sim --help

python -m lvcc_sim \
  --host 127.0.0.1 \
  --port 29001 \
  --device-id 868089057672499 \
  --ports 10 \
  --heartbeat 30 \
  --power 220
```

平台是 TCP **服务端**，模拟器主动连上去。`--host` / `--port` 填 Netty 监听地址。连上后日志里会有 TX/RX 的十六进制整帧，以及指令中文名。

环境变量可以代替对应参数：`LVCC_HOST`、`LVCC_PORT`、`LVCC_DEVICE_ID`、`LVCC_PORTS`、`LVCC_HEARTBEAT`、`LVCC_REPORT_INTERVAL`、`LVCC_POWER`、`LVCC_VOLTAGE`。

断线后默认 5 秒重连并重新注册。只试一次时加 `--no-reconnect`。`--smoke` 在注册和一次心跳之后约 1 秒退出，用来确认链路。

终端是交互式时可以直接输入命令（也可以显式加 `--interactive`）：

```text
ports
report
swipe 1 CC766062 B8
fault ff 5
fault 1 2 0
end 1
quit
```

`swipe` / `fault` / `end` 的插座号从 **1** 开始，和业务口一致。线上字节仍是 0 起始，和 `StartChargeVo` 里 `端口 - 1` 相同。

## 帧

与 `LvccProtocolFrame.pack` / `LvccByteFactory` 一致，多字节整数大端，设备号为 ASCII：

```text
7E 5D 7D 7F | 长度(2) | 版本 00 01 | 通讯标识 | 号长 | 设备号 | 消息号(2) | 指令(2) | 指令内容 | 异或
```

长度字段 = 整帧字节数 − 6。异或按 `LvccByteFactory.getXor(buf, 6, totalLen-7)`：下标 6 会先被放进累加器，循环又从下标 6 开始，等于异或两次后抵消。该字节固定是版本高字节 `0x00`。服务端 `BufferCheck` 里的异或判断是注释掉的，所以校验失败时模拟器只告警，仍然解析。

通讯标识不是网关 `pack()` 用的那两个值。设备抓包和 `LvccResponseDispatcher` 的注释是：

| 值 | 方向 |
| --- | --- |
| `0x00` | 网关下发 |
| `0x01` | 网关响应（注册/心跳回 4 字节 Unix 秒，主动上报多数回 `0x01` 表示成功） |
| `0x10` | 设备请求（注册、刷卡查询、刷卡订单） |
| `0x11` | 设备对下发指令的应答。网关不再回包 |
| `0x12` | 设备主动上报（心跳、充电结束、故障、插座状态、定时电量） |

设备应答会原样带回下行帧里的消息号。

## 设备行为

1. 连接后立刻发 **注册 `0x0001`**（`0x10`）。指令内容 10 字节：信号、主控版本、刷卡板版本、通讯模块版本、插座数、设备类型，末尾保留字节默认 `0x01`（与注册抓包一致，当前解析器不读）。服务端实际只从偏移 1、3 读两个版本号。
2. 约 0.3 秒后发第一帧 **心跳 `0x0002`**（`0x12`，指令内容为空），之后按 `--heartbeat` 周期发送。网关对这两条的响应都是 4 字节大端 Unix 时间，和 `DeviceRegisterResponse` / `DeviceHeatResponse` 相同。
3. 有口在充电时，按 `--report-interval`（默认 20 秒）上报 **`0x0030`** 和 **`0x0048`**。`0x0030` 按插座顺序带功率（瓦），空闲口为 0，避免通道号错位。`0x0048` 按源码注释排列：信号、端口数、耗电、电压、电流、温度、环境温度、在充口数量。电压原值 = 伏 × 100，电流原值 = 安 × 1000。耗电 short 按 Wh 累加。Java 在解析 `0x0048` 时把指令内容最后一个字节又丢掉一次，因此结尾的「整机数据条数」经常读不到，前面的端口数据不受影响。
4. 开启充电成功后上报 **`0x0021`** 插座状态。状态字节是模拟器约定、服务端原样保存：`0` 空闲，`1` 充电，`2` 故障。
5. **远程停止 `0x0007`** 的应答里已经带订单、分钟数和电瓶电量，平台会把它解析成结束订单，因此不再另发 `0x0010`。时长走完、空载、充满或本地 `end` 才发 **`0x0010`**（刷卡订单改发 **`0x0009`**）。
6. **重启 `0x0017`** 应答成功后断开，约 1 秒后重连并重新注册。进行中的订单直接丢掉，不再补结束帧。

`--power` 是充电功率（瓦），会被开始充电帧里的最大功率封顶。低于该帧的最小功率时，空载等待时间到了就结束。充满断电标志为 1 时，电瓶电量到 100% 后再等「充满等待」秒数才结束。电瓶电量是停止应答里的 1 字节，源码没有换算公式，模拟器从 50% 起每分钟加 1，上限 100。

## 指令

| 指令 | 方向 | 模拟器做什么 |
| --- | --- | --- |
| `0x0001` 注册 | 设备请求 | 10 字节版本/插座/类型 |
| `0x0002` 心跳 | 设备上报 | 指令内容为空 |
| `0x0005` 开启充电 | 网关下发 34 字节，设备应答 4 字节 | 结果、0 起始插座、剩余分钟 |
| `0x0006` 查询充电状态 | 网关下发插座号 | 服务端 `deviceQueryChargeStatus` 不读载荷。应答只有结果字节，功率、电量和订单写在日志里 |
| `0x0007` 停止充电 | 设备应答 27 字节 | 结果、插座、分钟、电瓶电量、20 字节订单，再加抓包里的 2 个 0 |
| `0x0008` 刷卡查询 | 设备请求 | 4 字节卡号 + 1 字节尾。解析器把除最后 1 字节外的内容当卡号，尾字节必须在 |
| `0x0009` 上报刷卡订单 | 设备请求 | 插座、分钟、20 字节订单、4 字节卡号、1 字节尾 |
| `0x0010` 充电结束 | 设备上报 | 原因、插座、订单、耗电、功率、充电秒数 |
| `0x0011` 查询设备信息 | 设备应答 | 与注册相同的 10 字节。服务端复用 `deviceRegister` |
| `0x0013` / `0x0014` 查询/设置配置 | 15 字节 | 布局等于 `SetDeviceArgsVo.outCommandBytes`。查询应答服务端只打日志 |
| `0x0016` 故障上报 | 设备上报 | 插座（`0xFF` 为整机）、故障码、产生/解除、4 字节详情 |
| `0x0017` 设备重启 | 设备应答 1 字节 | 成功后断开并重新注册 |
| `0x0019` 查询插座状态 | 设备应答 1 字节 | 服务端复用重启解析，只读结果字节。各口状态在日志里 |
| `0x0021` 插座状态更改 | 设备上报 | 插座、状态 |
| `0x0027` / `0x0028` 喇叭音量 | 设置 3 字节，应答 4 字节 | 应答与音量查询抓包一致：结果、播放开关、防打扰、音量 |
| `0x0030` 充电状态定时上报 | 设备上报 | 信号 + 每口功率 short |
| `0x0038` 复位故障 | 下发 2 字节，应答 1 字节 | 开关为 1 时按错误代码清除；代码 0 表示全部清除 |
| `0x0048` 实时充电状态 | 设备上报 | 见上文 |

`0x0010` 的结束原因字节服务端只放进 `setEvent`，没有码表。模拟器使用：`1` 达到充电时长，`2` 空载，`3` 充满，`4` 本地结束。

故障码文案来自 `EnumLvccFaultCode`。同一个数字在整机和单口上含义不同，例如整机 `5` 是烟感，单口 `1` 是继电器粘连。

## 和源码注释不一致、但按代码实现的地方

- `StartChargeVo` 的注释写充电时长单位是「10 分钟」。`inCommandString` 实际把「时长(秒)」整除 60 后写入。模拟器把这个 short 当作**分钟**。不足 60 秒会变成 0，0 表示一直充到停止指令。
- 停止应答里的电量是 **1 字节**「电瓶电量」；充电结束和 `0x0048` 里的耗电是 **2 字节**。
- 刷卡查询的网关组包函数会把结果改成 `4`（该卡未录入系统）。模拟器只有收到结果 `1` 且带订单号时才开电。

## 测试

```bash
python -m unittest discover -s tests -v
```

测试把 `LvccProtoDecode.main` 里长度完整的抓包（注册、心跳、开启/停止应答、音量、刷卡、`0x0030` 等）拆开再按同一规则组回去，字节必须一致。源码里两条以 `-` 分隔的 `0x0048` 十六进制被截断了，没有当作夹具。另外用本地 TCP 服务端走通注册、开启充电和停止充电。

## Docker 镜像（GHCR）

推送到 `main` / `master`、推送 `v*` 标签，或在 Actions 里手动运行 `.github/workflows/docker.yml` 时，会构建 `linux/amd64` 与 `linux/arm64` 并推到 GitHub Container Registry。工作流使用仓库的 `GITHUB_TOKEN`（`packages: write`），并带上 `org.opencontainers.image.source`，包会关联到本仓库。可见性跟随账号或组织的 Packages 默认设置；要给集群匿名拉取时，把该容器包改成 Public。私有包需要在命名空间里配置 `ghcr.io` 的 `imagePullSecret`。

镜像：

```text
ghcr.io/zhongshanhaojie/com.hvail.net.subprotocol.debug/lvcc-sim
```

| 标签 | 何时打上 |
| --- | --- |
| `latest` | 推送到默认分支 |
| 分支名，例如 `main` | 推送到该分支，或对手动运行时选中的分支 |
| `sha-<短提交>` | 每次构建 |
| `v1.2.3` | 推送同名 git 标签 |
| `1.2.3`、`1.2`、`1` | 该标签是 semver（`v1.2.3`）时 |

入口是 `python -m lvcc_sim`。镜像里的环境变量和命令行参数同名，未设置时用 CLI 默认值。容器内的 `127.0.0.1` 是容器自己，不是宿主机上的平台。

| 变量 | 含义 | 默认 |
| --- | --- | --- |
| `LVCC_HOST` | 平台 TCP 地址 | `127.0.0.1` |
| `LVCC_PORT` | 平台 TCP 端口 | `29001` |
| `LVCC_DEVICE_ID` | 设备号（ASCII） | `868089057672499` |
| `LVCC_PORTS` | 插座数量 | `10` |
| `LVCC_HEARTBEAT` | 心跳间隔（秒） | `30` |
| `LVCC_REPORT_INTERVAL` | 充电中 `0x0030` / `0x0048` 间隔（秒） | `20` |
| `LVCC_POWER` | 模拟充电功率（瓦） | `220` |
| `LVCC_VOLTAGE` | `0x0048` 电压（伏） | `220` |

拉取并看帮助：

```bash
docker pull ghcr.io/zhongshanhaojie/com.hvail.net.subprotocol.debug/lvcc-sim:latest
docker run --rm ghcr.io/zhongshanhaojie/com.hvail.net.subprotocol.debug/lvcc-sim:latest --help
```

平台监听在 Linux 宿主机上时，用 host 网络，让 `LVCC_HOST=127.0.0.1` 走到宿主机协议栈：

```bash
docker run --rm --network host \
  -e LVCC_HOST=127.0.0.1 \
  -e LVCC_PORT=29001 \
  -e LVCC_DEVICE_ID=868089057672499 \
  ghcr.io/zhongshanhaojie/com.hvail.net.subprotocol.debug/lvcc-sim:latest
```

不用 host 网络时，用 `--add-host` 把名字指到宿主机（Docker 20.10+ 的 `host-gateway`）：

```bash
docker run --rm \
  --add-host=lvcc-platform:host-gateway \
  -e LVCC_HOST=lvcc-platform \
  -e LVCC_PORT=29001 \
  -e LVCC_DEVICE_ID=868089057672499 \
  ghcr.io/zhongshanhaojie/com.hvail.net.subprotocol.debug/lvcc-sim:latest
```

镜像名后面可以再加 CLI 参数，例如 `--smoke`。

### Kubernetes

模拟器是 TCP 客户端，不需要 Service。`LVCC_HOST` 填平台在集群里能访问的地址（Service DNS 或集群外 IP），端口和设备号按该环境填写。站内命名空间用 `inner-web`。下面的主机名只是占位，换成真实的 Netty / choco_net 地址；设备号换成已登记的号码。

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: lvcc-sim
  namespace: inner-web
spec:
  replicas: 1
  selector:
    matchLabels:
      app: lvcc-sim
  template:
    metadata:
      labels:
        app: lvcc-sim
    spec:
      containers:
        - name: lvcc-sim
          image: ghcr.io/zhongshanhaojie/com.hvail.net.subprotocol.debug/lvcc-sim:latest
          imagePullPolicy: Always
          env:
            - name: LVCC_HOST
              value: "lvcc-platform.inner-web.svc.cluster.local"
            - name: LVCC_PORT
              value: "29001"
            - name: LVCC_DEVICE_ID
              value: "868089057672499"
            - name: LVCC_PORTS
              value: "10"
            - name: LVCC_HEARTBEAT
              value: "30"
```

