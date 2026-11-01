# Environment notes

These are current execution constraints, not a backlog of repaired incidents.

- Check existing D-drive tools before declaring a compiler unavailable. Run Go from `src/native/netproxy`, Gradle from `src/android`, and repository scripts from the repository root.
- Invoke Git Bash with `--noprofile --norc`; the user's login startup file contains invalid UTF-16 shell bytes. Supply the D-drive Go toolchain on PATH. Do not edit shared startup files to run project tests.
- Run Gradle invocations serially when they share the same project build directory; concurrent jobs previously corrupted the Kotlin incremental cache.
- Configure the repository-compatible JDK, SDK and D-drive caches explicitly. Do not assume `apkanalyzer` is in Android build-tools; signing verification uses `apksigner`.
- GLM review requests have failed with provider quota and content-filter errors. Those failures are not completed reviews; do not claim independent approval or retry indefinitely.

Resolved compilation, quoting, packaging, chain-runtime and installer incidents were removed on 2026-11-02. Their fixes belong to source and regression tests. Duplicate incident IDs and the obsolete blanket prohibition on v8.6.3 were removed; the user later explicitly authorized that version.
