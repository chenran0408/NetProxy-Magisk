# 无 Telemetry 构建

本项目提供 `netproxy_no_telemetry` 构建标签。该变体从 Native 程序中编译掉设备标识读取、兼容性摘要、事件队列、PostHog 上传和 Telemetry 锁，不创建 `config/telemetry` 或 `/dev/netproxy/telemetry.lock.flock`。

## 本地构建

在 `src/native/netproxy` 目录执行：

```sh
go test -tags netproxy_no_telemetry ./...
CGO_ENABLED=0 GOOS=android GOARCH=arm64 go build \
  -tags netproxy_no_telemetry -trimpath -buildvcs=false \
  -o netproxyctl-android-no-telemetry ./cmd/netproxyctl
```

该标签只影响 Native 程序。Android 管理器、节点数据、订阅、规则和普通运行时功能保持不变；历史上已经上传的第三方统计数据不会被此构建删除。
