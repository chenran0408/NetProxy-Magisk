# 策略管理

NetProxy Android App 的“策略”入口提供常用域名策略的选择界面。首版内置 AI、YouTube、TikTok、Netflix、GitHub 和 Google 六个策略。

## API

- `GET /api/v1/policies` 返回 `schema=1` 策略列表及可用 Catalog 分组和节点。
- `PUT /api/v1/policies/{id}` 保存 `group_id`、`node_tag` 和 `mode`。`node_tag` 为空表示组内 Auto，非空表示手动节点。
- `POST /api/v1/policies/{id}/url-test` 使用现有节点测速服务测试当前策略选择。

策略选择保存在模块状态目录的 `policies.json`，使用独立的 schema=1 文件，不会改写 Provider 或 runtime 文件。订阅更新后若节点标签失效，策略仍保留原选择，页面可重新选择有效节点。

策略会生成独立的 runtime route overlay，并随服务启动、检查和重载加载。服务运行中保存策略会触发重载；重载失败时保留已保存策略并返回 `policy.runtime_sync_failed`。如果订阅更新后手动节点不存在，下一次运行时准备会自动回退到该组 Auto。
