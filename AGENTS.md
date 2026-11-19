# Velnox Mobile — Agent Development Policy

## Mandatory Remote Build Policy

GitHub Actions is the **only** authorized build, test, and verification environment for Velnox Mobile.

AI agents are responsible ONLY for:
- modifying code,
- pushing changes to GitHub,
- monitoring GitHub Actions,
- analyzing remote build logs, and
- fixing problems based on actual CI results.

**AI agents MUST NEVER build, test, or compile the Android project locally.**

---

## 1. Mandatory restrictions — no local building

AI agents are strictly prohibited from executing any local Android build, compilation, or test command.

Forbidden commands include, but are not limited to:

- `./gradlew build`
- `./gradlew assembleDebug`
- `./gradlew assembleRelease`
- `./gradlew test`
- `./gradlew testDebugUnitTest`
- `./gradlew lint`
- `gradle build`
- any Gradle task that compiles, builds, tests, packages, or validates the Android project by execution
- any alternative local Android build mechanism
- Docker-based Android builds
- any workaround to bypass this policy

Do not build APKs on the local machine, the Android Harness terminal, Termux, or any other local environment.
Do not install Android SDKs or download build dependencies solely to perform local builds.

Reading build configuration files is allowed. Executing build tasks is not.

---

## 2. AI agent responsibilities

Follow this exact workflow for every task:

**Step 1 — Inspect**
- Fetch the latest repository state.
- Check the current branch and latest commit SHA.
- Inspect working tree changes.
- Read relevant project documentation and `AGENTS.md`.
- Understand the requested task before editing.
- Preserve unrelated user changes. Do not overwrite or discard uncommitted work.

**Step 2 — Modify code**
- Implement only the requested changes.
- Keep modifications focused and minimal.
- Avoid unrelated refactoring.
- Do not introduce mock data, fake implementations, unnecessary files, or redundant documentation.
- Preserve existing application behavior unless explicitly requested to change it.
- Review the modified files and diff before committing.

**Step 3 — Commit and push to GitHub**
- Review the diff.
- Ensure only intended changes are included.
- Commit the changes with a clear message.
- Push the working branch to GitHub.
- Allow the configured GitHub Actions workflow to start.

GitHub is responsible for executing all builds and tests. Do not attempt to build before pushing or after pushing.

**Step 4 — Monitor GitHub Actions**
- Identify the exact workflow run associated with the pushed commit.
- Wait for the workflow to finish.
- Do not assume a build passed because the push succeeded.
- Retrieve and inspect the actual workflow result.
- If the workflow fails, retrieve the failed job logs and identify the root cause.

**Step 5 — Analyze build logs**
When a GitHub Actions build fails:
1. Read the complete relevant error logs.
2. Identify the actual root cause.
3. Determine which files or changes caused the failure.
4. Avoid speculative fixes.
5. Do not blindly repeat previous failed fixes.
6. Make the smallest appropriate correction.
7. Commit and push the fix.
8. Wait for a new GitHub Actions run.
9. Inspect the new logs and verify the outcome.

Repeat only when justified by actual CI errors. Do not enter an endless retry loop. If repeated attempts fail, or the failure is outside the agent's control, stop and report the remaining issue with evidence.

**Step 6 — Successful build verification**
A task is considered build-verified ONLY when the relevant GitHub Actions workflow confirms success.

Check the available workflow steps, including when applicable:
- Unit tests
- Kotlin compilation
- Debug APK assembly
- APK integrity verification
- Package ID verification
- Signing verification
- Artifact upload

Do not claim the project builds successfully without a confirmed GitHub Actions result.
If an APK artifact is required, retrieve it from the GitHub Actions artifact output. Never generate it locally.

---

## 3. Error handling

If GitHub Actions fails:

> Read Logs → Identify Root Cause → Fix Code → Commit → Push → Wait for GitHub Build → Read Logs Again

If GitHub Actions succeeds:
- Confirm the workflow run succeeded.
- Confirm the relevant build and verification steps passed.
- Report the commit SHA.
- Provide the workflow run link.
- Provide the APK artifact link if available and required.

If GitHub Actions cannot run (unavailable, queued indefinitely, cancelled, blocked, or inaccessible):
- Do not perform a local build as a fallback.
- Do not bypass the CI requirement.
- Report the problem and wait for the appropriate action.

---

## 4. Protect the CI environment

AI agents must not:
- Disable failing workflow steps just to obtain a green build.
- Remove tests to hide failures.
- Weaken verification.
- Modify build configuration to bypass errors without understanding the cause.
- Falsify build status.
- Claim a build is successful when it has not been verified.
- Push unverified fixes repeatedly without reviewing the logs.

Workflow or CI configuration modifications are allowed only when explicitly needed for the task or when a confirmed CI configuration problem requires a legitimate correction.

---

## 5. Permanent documentation

This policy lives in `AGENTS.md`.
- Locate and inspect the existing `AGENTS.md` first.
- Append or integrate this rule into the existing documentation.
- Do not replace or delete unrelated rules.
- Avoid duplicate sections if a similar policy already exists.
- Ensure all future AI agents can clearly discover this mandatory rule.

If the project has additional agent instructions, update them only when necessary to ensure they reference this policy.

---

## 6. Required AI workflow

Every Android development task must follow:

Inspect → Plan → Edit → Review Diff → Commit → Push → GitHub Actions → Read Logs → Fix if Necessary → Verify

No local builds. No local tests. GitHub Actions only.

---

## 7. Final report format

After completing any development task, report:

- Task completed
- Files modified
- Commit SHA
- Branch name
- GitHub Actions run link
- Build status: PASS / FAIL / PENDING
- Test status
- APK artifact link, if available
- Remaining issues

Do not report PASS without evidence from GitHub Actions.

This policy is mandatory for all AI agents and all future Android development tasks in Velnox Mobile. The AI agent's role is to develop and maintain code. GitHub Actions is the sole authority for building, testing, and verifying the Android application.
