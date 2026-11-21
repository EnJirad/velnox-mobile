# Velnox Mobile — Agent Development Policy

## Authorized Local Build Policy (PocketDev Environment)

Local builds are authorized and performed directly in the PocketDev environment (`/workspace/clever-bose`).

AI agents are responsible for:
- modifying code,
- building the debug APK locally using PocketDev's optimized build commands,
- verifying build output and test results,
- pushing changes to GitHub when requested.

---

## 1. Local Android Build Instructions

For Android projects, never use plain `gradle build` or start a Gradle daemon. Build only the debug APK.

First check whether `app/build/outputs/apk/debug/` already contains an APK:
- **Cached fast path (with existing debug APK):**
  ```bash
  timeout 5m gradle -Dorg.gradle.jvmargs= --no-daemon --max-workers=2 --offline --init-script /root/.gradle/init.d/pocketdev-android.gradle -Pandroid.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2 assembleDebug --console=plain --stacktrace
  ```
- **First build / dependency resolution path (no existing debug APK or when cached build finishes with clear missing-cached-dependency error):**
  ```bash
  timeout 15m gradle -Dorg.gradle.jvmargs= --no-daemon --max-workers=2 --init-script /root/.gradle/init.d/pocketdev-android.gradle -Pandroid.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2 assembleDebug --console=plain --stacktrace
  ```

### Build Monitoring Rules:
- Antigravity does not stream output from a foreground shell command until that command exits.
- Start the optimized Android build with `run_command` normally; do not use `nohup`, shell backgrounding, or a detached process (they do not survive PocketDev's proot boundary).
- If Antigravity moves the command into a managed task, immediately use `manage_task` to check its status and obtain its log path.
- Poll that managed task and tail its log with short commands about every 10 seconds so progress is visible, then read the final output and exit status. Never wait silently on a long foreground command.

---

## 2. AI Agent Responsibilities

Follow this workflow for tasks:

**Step 1 — Inspect**
- Fetch or check the repository state.
- Inspect working tree changes.
- Understand the requested task before editing.
- Preserve unrelated user changes. Do not overwrite or discard uncommitted work.

**Step 2 — Modify Code**
- Implement requested changes cleanly and minimally.
- Follow existing architecture and patterns.
- Review diff before building.

**Step 3 — Build and Verify Locally**
- Execute the authorized PocketDev build command.
- Verify APK output at `app/build/outputs/apk/debug/app-debug.apk`.
- If build fails, inspect logs, identify root cause, and apply fixes.

**Step 4 — Git Commit and Push**
- When requested or after successful local verification, commit clean changes and push to GitHub.

---

## 3. Final Report Format

After completing any development task, report:
- Task completed
- Files modified
- Build status: PASS / FAIL
- APK path & size
- Verification details

---

## 8. AI Model Orchestration & Quota Delegation Policy

### 8.1 Default Role & Lightweight Tasks (Gemini 3.8 Flash Medium)
- **Coordinator & Everyday Model:** `Gemini 3.8 Flash (Medium)` acts as the primary coordinator, chat conversationalist, and handler for lightweight/routine tasks.
- **Scope for Lightweight Tasks:**
  - Minor UI adjustments, font tweaks, alignment/spacing shifts, simple asset changes.
  - Small, isolated bug fixes or configuration adjustments.
  - Daily chat, status questions, and general inquiries.

### 8.2 Major Tasks & Architecture (Specialized Models)
Whenever a task involves significant feature additions, deep architectural changes, multi-file rewrites, or critical system logic:
1. **System Analysis & Architecture Planning:**
   - Delegate to top reasoning models: **Claude Opus 5.5 (High)** or **Gemini 4 Argon (High)** to analyze requirements and construct a precise, robust implementation plan.
2. **Implementation & High-Performance Coding:**
   - Delegate code generation, core logic, and complex debugging to the top coding model: **Claude Sonnet 5.5 (High/Medium)** to ensure maximum code quality, stability, and adherence to project patterns.

### 8.3 Mandatory Quota Check & Fallback Cascade
**Agents must always verify quota availability before delegating tasks to specialized models:**
1. **Pre-flight Quota Check:** Verify the target model's remaining quota prior to launching workers or subagents.
2. **Quota Fallback Hierarchy:**
   - **For Analysis & Planning:**
     - 1st Priority: `Claude Opus 5.5 (High)`
     - 2nd Priority: `Gemini 4 Argon (High)`
     - 3rd Priority: Next available high-reasoning model with active quota (e.g., `Gemini 4 Neon High`).
   - **For Coding & Implementation:**
     - 1st Priority: `Claude Sonnet 5.5 (High/Medium)`
     - 2nd Priority: `Gemini 4 Neon (High)` or `Gemini 4 Hydrogen (High)`
     - 3rd Priority: Next available coding-capable model with active quota.
3. **Emergency Fallback:**
   - If quotas across all specialized models are exhausted, immediately fall back to **`Gemini 3.8 Flash (Medium)`** to continue and complete the work without stalling or blocking the user.

