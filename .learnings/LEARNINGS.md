# Maintained project lessons

- User terminology: 策略组 means a routing policy definition, not a subscription or catalog node group. Keep policy CRUD in the policy page; node/subscription management already has its own page.
- Region selection means a fixed region with automatic node selection inside that region, not a disguised manual node choice. Resolve persisted group IDs to runtime tags for routing and delay requests.
- Screenshot acceptance must show the actual changed screen and interaction. A dashboard screenshot is not policy-screen evidence. Connect devices only when the current task authorizes it.
- A config parse/check alone does not prove a core can start. Preserve protocol fields when generating chain copies; provider-backed selector defaults have runtime constraints. Keep these guarantees in focused tests, not repeated ad hoc workarounds.
- Preserve `NETPROXY_MODULE_DIR` on live-wrapper calls during installation. Keep ZIPs Android-unzip compatible and module metadata LF-only; existing packaging/installer tests own these repaired regressions.
- Update English, Chinese and Russian resource keys together, including format arguments.
- Do not infer a new release version or treat a local build as a published release. v8.6.3 was explicitly authorized later; each new task's push/tag/release restrictions still apply.
- A confirmed policy write and runtime synchronization are different results. Retain save errors in the editor, show confirmed cards, and do not invite duplicate creation after a persisted-but-unsynchronized response.

The obsolete feature-request entries were removed: region UI and policy CRUD already exist, while introducing node-group management as policy management was explicitly corrected by the user.
