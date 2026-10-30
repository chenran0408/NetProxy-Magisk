# 策略管理

NetProxy Android App 的“策略”入口提供常用域名策略的选择界面。首版内置 AI、YouTube、TikTok、Netflix、GitHub 和 Google 六个策略。

## API

- `GET /api/v1/policies` 返回 `schema=1` 策略列表及可用 Catalog 分组和节点。
- `PUT /api/v1/policies/{id}` 保存 `group_id`、`node_tag` 和 `mode`。`node_tag` 为空表示组内 Auto，非空表示手动节点。
- `POST /api/v1/policies/{id}/url-test` 使用现有节点测速服务测试当前策略选择。

策略选择保存在模块状态目录的 `policies.json`，使用独立的 schema=1 文件，不会改写 Provider 或 runtime 文件。服务运行时保存策略会请求 Native 重载；若重载失败，接口明确返回“已持久化、未同步”，Android 页面显示错误，重新进入页面可读取保存后的选择。服务停止时只保存选择，启动服务后由 Native 生成并应用运行时策略。订阅更新删除了手动选中的节点时，Native 会将选择回退为该策略组内的 Auto。

策略页面会展示服务运行状态，以及最近一次测速返回的节点状态：有延迟结果为可用、完成测速但没有延迟为失败、没有测速时间为未测速。自动测速结果由 sing-box 缓存，因此测速后显示的是核心返回的最新状态，不保证每个节点都由本次请求更新。

策略会生成独立的 runtime route overlay，并随服务启动、检查和重载加载。
