# REA Inspection Notes — Mission: Wahtuh Pho

**Date of inspection:** 2026-10-19
**Source:** shallow clone (`git clone --depth 1`) of https://github.com/morluto/rea into `/tmp/rea` (reference material only — NOT part of the repo)

## What REA is

**REA = "Reverse Engineer Anything."** Version inspected: **6.7.0**. It is an MCP (Model Context Protocol) server plus CLI that lets an AI agent inspect compiled software *without its source code* and explain how features work, citing evidence.

- Language/stack: TypeScript on Node.js (requires Node.js 22.x ≥22.19, 24.x ≥24.11, or 26+; this VM has Node v24.20.0 / npm 10.9.4, so it meets the requirement). Published as the `rea-agents` npm package; setup via `npx rea-agents setup`.
- Analysis runs locally. Results carry evidence and documented limitations for each conclusion.
- Ships with a disclaimer for lawful use only (security research, malware analysis, CTFs, interoperability, auditing software you own/are authorized to examine) and explicitly refuses DRM/license-check circumvention, cheats, and copying proprietary software.

## Analysis capabilities (per README + docs/)

REA is a *wrapper/orchestrator*: it does not install the underlying engines — the caller supplies them.

| Target | Capability | Engine (caller-supplied) |
|---|---|---|
| Android APKs (static) | Manifest declarations, classes, decompiled methods, cross-references | Headless JADX (`inspect_android_package` MCP); full JDK 17+ required |
| Android resources | Decoded manifest, string tables, locales, version facts | Apktool 2.7.0+ (caller launcher; REA never installs Java) |
| Android deep decompile | Engine-backed pseudo-code, project units | JEB client serving MCP (optional, commercial) |
| Flutter apps | Dart snapshot hash, per-ABI digests, engine build provenance | Static APK parsing, no extra tools |
| Android devices (live) | Devices, packages, processes, logs, dumps, screen captures, APK/file transfer | Caller-supplied adb (emulator or hardware) |
| Native binaries | Pseudocode, assembly, strings, symbols, calls/references | Hopper, Ghidra, or IDA (existing install); also offline GNU objdump / Rizin |
| Go binaries | Embedded compiler version, modules, build settings | Built-in static ELF/PE/thin Mach-O inspection |
| Rust binaries | Embedded cargo-auditable crate names, versions, dependency edges | Built-in static inspection |
| WASM | Validation, sections, decoded WAT | WABT 1.0.42 |
| .NET assemblies | Metadata, CIL instructions, native deps, build comparisons | Static only, no extra tools |
| JavaScript/Electron | Modules, imports, source maps, routes, IPC, native add-ons | Node.js + npm |
| Firmware | Regions, extraction results | Binwalk / Unblob |
| Network/traffic | HAR/SAZ analysis, packet decodes, TLS endpoints, signal recordings | mitmdump, TShark, sigrok-cli, etc. |
| Apple bundles | File inventories, digests, plists, bundle anatomy | Built-in |

**Relevance to the mission:** the Android family (`docs/android-analysis.md`, `docs/apktool-resource-analysis.md`, `docs/jeb-analysis.md`, `docs/flutter-analysis.md`, `docs/adb-device-analysis.md`) is what would be used for APK inspection *if* a legitimately obtained APK existed. Notable prerequisites on Linux: full JDK 17+, a pinned `jadx-headless-mcp-0.7.1` JAR (SHA-256 verified in docs), and a procps-compatible `ps` on PATH. None of these are currently present on this VM (no Java at all — see ENVIRONMENT.md).

## License

**MIT License**, Copyright (c) 2026 morluto. Permissive: use, copy, modify, merge, publish, distribute, sublicense, sell allowed, with copyright notice included. Repo also contains THIRD_PARTY_NOTICES.md, CONTRIBUTING.md, SECURITY.md, and CHANGELOG.md.

## Tests

Tests exist and are extensive; **they were NOT run** (heavy suite; `node_modules` not installed in the shallow clone).

- Framework: Vitest (`vitest run` / turbo). Also Python unittest suites (`test:python`) for the lldb tracer and mitmproxy bridge.
- Layout under `tests/`: `acceptance` (129 files), `boundary` (839 files), `composition` (23), `conformance` (108), `evaluation` (15), `process-global` (46), plus `fixtures` (215 files).
- Additional configs: mutation testing via Stryker, coverage via v8, CI sharding scripts, a test-discovery verification script.
- What they cover (from structure/naming): domain/services/adapters unit tests, CLI boundary tests, MCP boundary tests, process-ownership tests, acceptance tests per capability (e.g. `sqliteDatabaseCli.test.ts`), and conformance/evaluation suites.

## ⚠️ IP constraint — APK inspection NOT performed

**No Wispr Flow APK was inspected.** There is no legitimately obtained Wispr Flow APK in this environment, and none was downloaded from any source. REA was inspected as a tool only, from its public source repo. Documenting this explicitly per mission rules: do not copy proprietary material, do not fetch APKs from unofficial sources.

If/when the project obtains a legitimately licensed APK (e.g. extracted from the user's own device), REA's Android family would be the candidate toolchain — pending JDK 17+ and the JADX headless engine being provisioned.
