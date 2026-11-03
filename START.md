# 本地启动

仓库根目录就是模拟器。需要 Python 3.10 及以上，只用标准库，不必安装第三方包，也没有 `requirements.txt`。

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

`--host` 和 `--port` 是平台 Netty / choco_net 的 TCP 监听地址。模拟器作为客户端连上去。设备号 `868089057672499` 是协议抓包里的假号码，接到真实平台时换成已登记的设备号。

只连一次、断开即退出：

```bash
python -m lvcc_sim --host 127.0.0.1 --port 29001 --device-id 868089057672499 --no-reconnect
```

注册并发送一次心跳后退出：

```bash
python -m lvcc_sim --host 127.0.0.1 --port 29001 --device-id 868089057672499 --smoke
```

自检：

```bash
python -m unittest discover -s tests -v
```
