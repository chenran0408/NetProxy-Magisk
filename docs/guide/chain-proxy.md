# 链式代理

链式代理把流量按“手机 -> 前置节点 A -> 落地节点 B -> 目标网站”转发。对外出口是落地节点 B；原有直连规则、分应用策略和普通节点选择不变。

## 使用方法

1. 启动 NetProxy，打开 sing-box Dashboard 的代理页面。
2. 在 `链式前置` 选择前置节点 A。
3. 在 `链式代理` 选择落地节点 B。列表中的节点以 `链式落地/订阅名称/节点名称` 显示。
4. 在 `Proxy` 选择 `链式代理` 开启链式转发。选择 `Auto/...` 或 `Select/...` 可恢复普通代理。

前置和落地可以来自不同订阅。请选择两个不同节点，并确认前置节点能够连接落地协议；Hysteria、Hysteria2 和 TUIC 等 UDP 落地协议需要前置链路支持 UDP 转发。

## 更新与兼容

- 链式落地副本只写入运行时配置，不会修改或污染 Catalog 中的用户 Provider 文件。
- 订阅更新后，运行时会从最新 Provider 重新生成副本；原节点失效时 sing-box 会按当前可用选择恢复。
- 链式副本不参与普通分组的周期性健康检查，避免节点被重复测速；需要确认完整链路时可在 Dashboard 手动测速。
- HTTP、SOCKS、Shadowsocks、VMess、VLESS、Trojan、AnyTLS、Snell、ShadowTLS、Hysteria、Hysteria2 和 TUIC 可作为落地协议。
- 含 WireGuard/Tailscale Endpoint，或含 SSH、Naive、Tor 等不支持 dialer 覆盖的分组，不会生成落地副本。

没有符合条件的落地节点时，运行时不会添加链式选择器，普通代理仍可正常使用。
