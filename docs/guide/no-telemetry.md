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

## 发布变体与开关

- `netproxy_no_telemetry` 从编译阶段排除上报实现；使用 `NETPROXY_NO_TELEMETRY=1 sh tests/ci_verify.sh` 可在本机验证并构建该变体，共享构建工作流对应 `no-telemetry: true`。
- 普通构建通过 `POSTHOG_PROJECT_TOKEN` 和 `POSTHOG_HOST` 注入上报参数。任一为空时，Native 不会创建上报器；这表示上报未启用，不等同于编译掉上报代码。
- Fork 不会自动继承原仓库的上报凭据。未自行配置上述变量的本机构建默认不含这些凭据。现有发布构建在普通变体中设置了 `NETPROXY_TELEMETRY_REQUIRED=1`，缺少参数会直接失败；需要无遥测发行包时应明确选择无遥测变体，不能把失败构建描述成已成功发布。
