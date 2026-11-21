# Velnox Mobile — Agent Development Policy

## Authorized Local Build & Mandatory Git Push Policy

1. **Local Builds Only (No Remote Git Builds):**
   - Builds, compilation, and verification are performed **exclusively locally** inside the PocketDev environment (`/workspace/clever-bose`).
   - GitHub Actions remote builds are deactivated on push and will not be executed or waited on.
2. **Mandatory Git Commit & Push Per Development Round:**
   - Every time code modifications are completed and verified locally in PocketDev, the agent **MUST** commit all changes with a clear, descriptive message and immediately push to GitHub (`origin/main`).
   - This maintains an exact, granular commit history so any past version can be inspected or rolled back in case of unexpected errors or regressions.

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

## 2. AI Agent Responsibilities & Required Workflow

Every development task must follow this exact sequence:

**Step 1 — Inspect**
- Fetch or check the repository state and git branch (`origin/main`).
- Inspect working tree changes (`git status`, `git diff`).
- Understand the requested task before editing.
- Preserve unrelated user changes. Do not overwrite or discard uncommitted work.

**Step 2 — Modify Code**
- Implement requested changes cleanly and minimally.
- Follow existing architecture and patterns.
- Review diff before building (`git diff`).

**Step 3 — Build and Verify Locally**
- Execute the authorized PocketDev build command locally.
- Verify APK output at `app/build/outputs/apk/debug/app-debug.apk`.
- If build fails, inspect logs, identify root cause, and apply fixes.

**Step 4 — Mandatory Commit and Push to GitHub**
- After local verification passes, stage and commit the changes:
  ```bash
  git add <modified_files>
  git commit -m "<type>(<scope>): <clear description of changes>"
  git push origin main
  ```
- Always ensure changes are pushed to GitHub so commit history is preserved for future rollbacks.
- Do NOT trigger or wait for remote builds on GitHub Actions.

---

## 3. Final Report Format

After completing any development round, report:
- **Task Completed:** Summary of work done
- **Files Modified:** List of all files changed
- **Build Status:** PASS / FAIL (local PocketDev build)
- **APK Path & Size:** Location and file size of generated debug APK
- **Git Commit & Push:** Commit SHA, branch name, and push status

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

