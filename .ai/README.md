# .ai — VelShop Mobile AI Ops Index

> **Purpose:** This folder is the canonical AI collaboration layer for the VelShop mobile repository.
> Every AI agent **must** start here, follow the startup protocol, and keep `.ai/reports/LATEST.md` current.

---

## Startup Protocol (run before any edit)

```bash
# 1. Remote is the source of truth
git remote -v
git fetch origin
git status
git branch --show-current
git rev-parse HEAD
git rev-parse origin/main   # authoritative remote SHA

# 2. If local != remote → `git pull --ff-only` before any edit
```

---

## File Index (read order)

| File | Purpose | When to read |
|------|---------|--------------|
| `.ai/SYSTEM_INTEGRATION_GUIDE.md` | **System Architecture & Subsystem Integration** (Auth, Messaging, Commerce, Geolocation) | **Every session / before cross-system changes** |
| `.ai/AI_RULES.md` | Core rules (startup sync, build, compile, reporting) | **Every session** |
| `.ai/AI_HANDOFF.md` | Current snapshot: what works, what's broken, next steps | **Every session** |
| `.ai/context/project-map.md` | Repo layout, module map, key paths | On first touch or area change |
| `.ai/build-apk/BUILD_APK_GUIDE.md` | **Reusable APK build method** (the reusable recipe) | Before any build or CI change |
| `.ai/reports/LATEST.md` | Latest change report (what changed, what broke, verification) | Every session / after any task |
| `.ai/history/` | Chronological task logs (append-only) | When debugging regressions |

---

## Authority Hierarchy

```
GitHub remote (main)  >  AI_RULES.md  >  AI_HANDOFF.md  >  context/*  >  source code
```

When docs conflict with source, **source wins** — then update docs.

---

## Quick Links

- **Current working workflow**: `.github/workflows/gradle-build.yml`
- **Working APK artifact**: `velshop-debug-apk` (GitHub Actions run #21, commit `e813c92`)
- **Project map**: `.ai/context/project-map.md`
- **Change report**: `.ai/reports/LATEST.md`

---

## Version Matrix (pinned, do not change without approval)

| Component | Version | Notes |
|-----------|---------|-------|
| JDK | 17 (Temurin) | GitHub Actions `actions/setup-java@v5` |
| Android SDK | API 35 | `platforms;android-35` + build-tools 35.0.0 + 34.0.0 |
| Gradle | 8.11.1 | Installed directly, not wrapper jar |
| AGP | 8.7.2 | `com.android.application` plugin |
| Kotlin | 2.0.21 | `org.jetbrains.kotlin.android` |
| Compose Compiler | 2.0.21 | Via `compose-bom:2024.10.01` |
| Compose BOM | 2024.10.01 | Single source of truth |

---

## Golden Rules (non-negotiable)

1. **No local APK builds** — GitHub Actions is the only build environment.
2. **Remote wins** — if local ≠ remote, `git pull --ff-only` before touching files.
3. **Verify, don't assume** — ran? show the log. passed? show the test count. built? show the file size.
3. **Commit with context** — every commit message explains *why*, not just *what*.
4. **Update LATEST.md** after every task; history/ is append-only.
5. **Never claim success without log proof** — "BUILD SUCCESSFUL" must be in the log.
