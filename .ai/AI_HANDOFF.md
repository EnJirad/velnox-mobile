# AI_HANDOFF.md — Velnox Mobile Current Snapshot

> **Current snapshot (not a log).** What works, what's broken, decisions, next steps.
> Statuses: **VERIFIED / UNVERIFIED / FAILED / BLOCKED / SKIPPED / NOT TESTED**.
> Supersedes previous handoffs.
> Repository: `EnJirad/velnox-mobile`

---

## Current State (2026-11-17, run 44098361945)

| Area | Status | Notes |
|------|--------|-------|
| Android project | **VERIFIED** | Kotlin 2.0.21 + Compose, AGP 8.7.2, Gradle 8.11.1 |
| Login screen | **VERIFIED** | Compiles and ships; email/password validation, show/hide, remember-me, loading, notices |
| Design tokens | **VERIFIED** | `ui/theme/` implements §2 colours, §7.1 squircle radii, §8 type scale |
| Strings | **VERIFIED** | Thai default + English; no hardcoded UI copy |
| GitHub Actions build | **VERIFIED** | Run 44098361945, `BUILD SUCCESSFUL in 2m 30s` |
| APK artifact | **VERIFIED** | `velnox-debug-apk`, 9,097,188 bytes, 9,527,422 bytes on disk |
| APK identity | **VERIFIED** | `aapt2 dump badging`: `com.velnox.app`, `Velnox`, launcher `MainActivity`, minSdk 26 / target 35 |
| Local Gradle build | **BLOCKED** | Packaged Gradle is 9.9.1 and its launcher jar is missing; AGP 8.7.2 needs 8.9+ only. CI is the build route |
| On-device run | **NOT TESTED** | No install route in this session (no Shizuku, no root) |
| Backend auth | **SKIPPED** | Submit simulates a 900 ms request, then shows a local success notice |

---

## Decisions

1. **No Gradle wrapper jar.** `gradlew` delegates to `GRADLE_HOME`/`gradle`. Keeps a binary out of the repo and avoids the aarch64 native-library trap recorded in the old handoff.
2. **No icon-extended dependency.** Password visibility uses a text control; the check mark and brand mark are drawn with layout primitives, so no icon font is added.
3. **Tokens as top-level vals.** `VelnoxBrand`, `VelnoxBorder` etc. live in `Color.kt` and are read directly, not through a `MaterialTheme` extension.
4. **Single screen, no navigation.** This slice is the login screen only; a nav host arrives with the signed-in area.
5. **Thai is the default locale**, English in `values-en`.

---

## Known issues

- None blocking. The first workflow run failed on YAML parsing; fixed by using a block scalar for that `run:` step.

---

## Next Agent Startup Order

1. `.ai/AI_HANDOFF.md` ← **YOU ARE HERE**
2. `.ai/AI_RULES.md`
3. `.ai/VELNOX_DESIGN_THEME.md` (source of truth for UI)
4. `.ai/reports/LATEST.md`
5. `.ai/context/project-map.md` — note: still describes `velshop-mobile`; update on first touch
6. `git fetch origin && git rev-parse HEAD && git rev-parse origin/main`

---

## Secrets / Credentials

No secrets in `.ai/`. No tokens, keystores or passwords in this repo.

---

## Signature

> This handoff supersedes all previous.
> **Last updated:** 2026-11-17 by the session that created the app and shipped the login APK.
