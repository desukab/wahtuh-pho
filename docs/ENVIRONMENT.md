# Environment Recon — Mission: Wahtuh Pho

**Date of inspection:** 2026-10-19 (IST; outputs collected ~14:15 UTC)

All entries below are actual observed outputs, not assumptions.

## Rust toolchain

| Check | Result |
|---|---|
| `rustc --version` | **not found** — `rustc: command not found` |
| `cargo --version` | **not found** — `cargo: command not found` |
| `~/.cargo/bin` | **not found** — no such directory |
| `rustup` (in PATH) | **not found** — `rustup: command not found` |
| `/usr/local/bin` | no `rustc`/`cargo` entries |
| apt installed packages | no `rustc`/`cargo` packages installed |

**Conclusion: Rust is NOT installed anywhere on this VM.** Network is available (see below), so installation via `rustup` (https://rustup.rs) is possible if/when authorized. This is a blocker for compiling the Rust core.

## Git / VCS

| Check | Result |
|---|---|
| `git --version` | `git version 2.43.0` |
| `git status` in `~/workspace/wahtuh-pho` | **not a git repository** — `fatal: not a git repository`. The workspace is a plain directory containing only `docs/`. |

**Note:** per MEMORY, the target repo is `desukab/wahtuh-pho` on GitHub (GitHub OAuth connected for desukab; app installation verified for private repo reads), but no clone/initialization has been done locally in this environment.

## Java / JDK

| Check | Result |
|---|---|
| `java -version` | **not found** — `java: command not found` |

No JDK installed. (JDK is not strictly required for the Rust core, but would be needed for Android builds via Gradle.)

## Android SDK / NDK

| Check | Result |
|---|---|
| `ANDROID_HOME` | unset (empty) |
| `ANDROID_SDK_ROOT` | unset (empty) |
| `~/Android` | **not found** — no such directory |
| `/opt/android*` | **not found** — no such directory |
| `sdkmanager --version` | **not found** — `sdkmanager: command not found` |
| `ndk-build --version` | **not found** — `ndk-build: command not found` |

**Conclusion: No Android SDK or NDK is present.** Android target builds are not possible in this environment without downloading the command-line tools/NDK (~1–2 GB).

## Network

| Check | Result |
|---|---|
| `curl -sI --max-time 10 https://github.com` | **reachable** — `HTTP/1.1 200 OK`, `Content-Type: text/html`, `curl_exit=0`, response time ~2 s |

**Conclusion: Outbound internet access works** (at least to github.com; observed via a proxy that returned "200 Connection Established" before the origin's 200).

## OS / architecture

```
Linux htch-runtime 7.0.0-39-generic #1 SMP PREEMPT_DYNAMIC Thu Sep 24 08:42:15 UTC 2026 x86_64 x86_64 x86_64 GNU/Linux
```

- OS: Linux (Ubuntu-based kernel `7.0.0-39-generic`, build Sep 24 2026)
- Hostname: `htch-runtime`
- Arch: x86_64 (64-bit)

## Summary

- ✅ Network: available (github.com reachable)
- ✅ Git: 2.43.0
- ❌ Rust toolchain: absent
- ❌ Java/JDK: absent
- ❌ Android SDK/NDK: absent
- ⚠️ `~/workspace/wahtuh-pho` is not a git repo yet

**Critical path implications for Phase A1:** Rust installation is required before any `cargo build`/`cargo test` work. Android SDK/NDK is required before any mobile target work. Both are downloadable given working network.
