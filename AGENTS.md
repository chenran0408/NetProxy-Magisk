# Repository Guidelines

## Project Structure & Module Organization

NetProxy-Magisk is an Android transparent-proxy project with several coordinated components:

- `src/module/`: Magisk/KernelSU/APatch module files, runtime configuration, lifecycle scripts, and packaged web assets.
- `src/native/netproxy/`: Go CLI and service implementation for catalog, subscriptions, configuration, eBPF/TUN operation, and the module API.
- `src/webui/`: TypeScript/Vite terminal-style management UI; build output is consumed by the module.
- `src/android/`: Android manager and supporting platform modules.
- `docs/`: VitePress documentation; `tests/`: shell, Go, WebUI, packaging, and contract checks.

Keep changes within the owning component. Changes to shared JSON/API contracts must be checked across native, Android, WebUI, module scripts, and tests.

## Build, Test, and Development Commands

Run commands from the repository root:

```sh
sh tests/verify.sh quick      # Go, shell contracts, and workflow checks
sh tests/verify.sh webui      # WebUI type checks, tests, and production build
sh tests/verify.sh android    # Android tests, lint, and debug build
sh tests/verify.sh docs       # Documentation build
sh tests/verify.sh full       # All verification suites
```

For focused WebUI work, use `cd src/webui && npm run check` or `npm run dev`. For native work, use the Go toolchain from `src/native/netproxy`; run package tests with `go test ./...`.

## Engineering Style: Less Defense, Fast Feedback, Maintainability

- Treat local diagnostic/runtime logs and `netproxyctl` output as user-authorized data flows. Redact only credentials, tokens, and subscription credential fields, at the writer's single entry point. Preserve full exceptions and stack traces, package/class/method names, private control codes, URI paths (excluding query parameters), timestamps, and process names verbatim. Free text is not a reason to filter. Any new redaction rule must first update this policy with its rationale; later sessions must not silently broaden redaction.
- Do not add defensive try/catch, retries, timeouts, null fallbacks, or parameter checks without an observed failure mode or a contract-test requirement. Empty catches are defects. Fail fast and identify the component and stage.
- Deletion is a valid fix: remove dead code directly and delete replaced paths in the same change.
- Make the smallest reliable change. Keep one concern per commit, reproduce a bug with a failing test before fixing it, reuse existing controlled formats, and avoid introducing abstraction layers for small requirements.
- For shared contract changes, check native, Android, WebUI, module scripts, and tests together, as required by "Project Structure & Module Organization".
- Start verification with the affected Go package or shell test, then `sh tests/verify.sh quick`, and use `full` only when the scope requires it. Do not add defensive tests for unchanged paths.

## Coding Style & Naming Conventions

Use UTF-8 and preserve existing line endings. Go code must be `gofmt`-formatted, with idiomatic mixed-case identifiers. TypeScript follows the existing strict compiler configuration and uses `camelCase` for values and `PascalCase` for components/types. Android code follows Kotlin conventions. Shell scripts must remain POSIX-compatible for Android `/system/bin/sh`; quote paths and arguments. Use descriptive lowercase filenames and keep JSON schema/API field names stable.

## Testing Guidelines

Add regression coverage for bug fixes and place tests beside the relevant component. Shell tests use the `tests/*_test.sh` pattern; WebUI tests use `tests/*.test.mjs`; Go tests use standard `*_test.go`. Run the narrowest relevant suite first, then `sh tests/verify.sh full` before release-oriented changes.

## Commit & Pull Request Guidelines

Use the repository's Conventional Commit style, such as `fix(module): ...`, `feat(ebpf): ...`, or `chore(module): ...`. Keep commits focused. Pull requests should explain the user-visible change, affected components, and commands run; include screenshots or recordings for UI changes. Do not commit node credentials, subscription URLs, tokens, signing material, or raw device logs.

## Security & Configuration Tips

Never disable TLS verification to solve connectivity problems. Treat module API credentials, certificates, and node data as sensitive. Preserve atomic configuration updates and validate schema/API changes with the existing contract tests.

The core dependency `github.com/reF1nd/sing-box` is a third-party fork selected by a `go.mod` replace directive; upgrades require manual review of the fork's commit diff and a record of the review conclusion in the commit message.
