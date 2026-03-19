# v-link 架构

## 组件

* `coordinator`：负责注册、心跳跟踪、打洞通知、以及中继转发。
* `edge`：负责 TUN 设备收发、加密 UDP 数据面、直连/中继切换，以及停等协议（stop-and-wait）。
* `common`：负责协议编解码与加密能力。

## 数据路径

1. Edge 从 TUN 设备读取 IP 数据包。
2. 将目标虚拟 IP 解析为对应的 peer `nodeId`。
3. 通过 UDP 发送加密后的 `DATA_PACKET`：

   * 优先：发送到对端的直连地址。
   * 回退：通过 coordinator 中继转发。
4. 接收端解密后，将负载写回本地 TUN 设备。

## 直连/中继状态机

* `INIT`：尚未获得对端地址。
* `PROBING`：查询对端信息 + 通知 coordinator 发起打洞 + 发送直连探测包。
* `DIRECT`：直连 UDP 通路已建立。
* `RELAY`：直连超时或重试失败后，进入 coordinator 中继模式。

### 状态迁移

* `INIT -> PROBING`：收到首个出站数据包时触发。
* `PROBING -> DIRECT`：收到直连数据包 / ACK / 探测包时触发。
* `PROBING -> RELAY`：探测超时或重传次数耗尽时触发。
* `RELAY -> DIRECT`：后续探测成功时切回直连。

## 可靠性（简化版）

* 以对等节点为单位采用停等协议（stop-and-wait）。
* 相关字段：`seq` / `ack`。
* 在 RTO 超时后执行重传。
* 达到最大重试次数后，回退到中继模式。
* 接收端使用去重缓存，避免重复写入 TUN 设备。
