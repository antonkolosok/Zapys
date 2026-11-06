# Task 1 independent review

Review date: 2026-11-02. Scope: task-1 brief, implementation report, task-1-review-current.diff, current setup scripts, requirements and design. Static source review only; no duplicate installation, build, network or Git checks were run.

## Verdicts

- Spec compliance: **CONCERNS / incomplete verification gates**. Documentation covers the local SQLite architecture, Ukrainian UI, truthful unverified profile and manual reminder handoff, approved limits and sequential native implementation. Design explicitly reconciles prototype limitations with seven-column week and ordered labels. No application implementation was required in this task. SDK installation, repository writes, setup commit and macOS execution remain BLOCKED as reported; this review does not convert them to passing checks.
- Source quality: **CHANGES REQUESTED** for reproducible JDK setup and truthful tool verification below. These defects are fixable in source and are separate from DNS, permission and integration restrictions.

## Findings

### P2 — JDK source is not reproducibly pinned

`scripts/bootstrap.ps1:20-23` resolves the latest JDK17 release on each fresh setup but reuses the fixed cached filename `jdk.zip`. A later upstream release changes both the downloaded version and expected checksum. A cache containing the previous verified release then fails verification when re-extraction is needed; clean machines also receive different versions from the same source revision. Pin an explicit JDK release and upstream checksum (with a version-specific archive filename), as already done for Gradle and GitHub CLI. Preserve checksum verification.

### P2 — Existing JDK directory can bypass project-local installation

`scripts/bootstrap.ps1:19` treats the existence of `.tools/java` as installation success. Interrupted extraction or an empty directory suppresses repair. `scripts/env.ps1:4-5` then selects the first directory without checking `bin/java.exe`; when none exists it retains an inherited JAVA_HOME, and `java -version` can succeed using another Java on PATH. Consequently bootstrap can report success without its required local JDK. Detect the expected versioned executable, repair or explicitly fail an incomplete installation, and verify the chosen project-local Java executable rather than an unqualified PATH command.

### P2 — Bootstrap success message exceeds performed verification

`scripts/bootstrap.ps1:43-44,60` checks only Java before saying that tool launchers are available. Existing Gradle/GitHub CLI paths skip extraction without a launch/version check, and the command-line-tools sentinel checks `sdkmanager.bat` although SDK installation actually invokes `android`. This can report setup success with a broken Gradle/gh installation or incomplete Android CLI directory. Verify the required launchers and exit codes before reporting them available; use the actual Android CLI launcher as a required installation file. Keep installed-SDK verification conditional on `-InstallSdk` and retain the current component file checks.

## Evidence and remaining gates

The report and STATUS document Java/Gradle/gh launch evidence, SDK DNS failure, denied Git index writes, GitHub HTTP 403/invalid CLI authentication and unavailable macOS/Xcode. These are accepted as reported evidence rather than independently rerun here. There is no Android APK/lint/instrumentation or iOS simulator pass. The reviewed diff contains documentation/configuration/scripts, no credentials or generated mobile build output, and no prototype changes. No merge, release or commit was performed during review.

After the source fixes, perform a focused setup review and record the actual verification outcome. Task 1 cannot be described as fully complete until its blocked SDK/repository/commit gates are satisfied or explicitly carried forward as blocked.

## Supplemental setup tool review

Also inspected `scripts/test-domain.ps1` at the lead's request. It compiles pure domain source and selected JUnit test files using Gradle's bundled Kotlin 2.0.20 compiler, isolates output under ignored `artifacts/`, rejects an empty test selection, and checks compiler/JUnit exit codes. Its comments and success message explicitly distinguish this fallback from Kotlin 2.1.20, Android Gradle, SDK, lint and instrumentation verification. No additional blocking source finding from this static inspection. Execution evidence belongs in the lead's report; this review did not rerun the command.

## Fix round 1 scoped re-review — 2026-11-02

Reviewed `task-1-fix1.diff`, updated task-1 report and current bootstrap/env scripts. No commands were rerun and no production/setup source was changed by this reviewer.

| Prior finding | Verdict | Source evidence |
| --- | --- | --- |
| JDK not reproducibly pinned | **ADDRESSED** | Explicit Temurin 17.0.20.1+1 release URL, fixed SHA256 and version-specific archive name replace latest-release discovery. |
| JDK directory sentinel can accept incomplete/external Java | **ADDRESSED** | Bootstrap requires the exact local `bin/java.exe`; env fails when it is missing and sets JAVA_HOME to the pinned directory; verification invokes that executable directly. |
| Success message exceeds launcher verification | **ADDRESSED** | Java, Gradle and gh are invoked directly with exit checks. Android extraction uses the actual `android.exe` sentinel. Final output limits its Android claim to launcher presence and explicitly leaves runtime/SDK verification separate. |

**Updated source-quality verdict: APPROVED for this scope.** No new blocking breakage identified in these fixes. The lead reports default bootstrap exit 0 with Java 17.0.20.1, Gradle 8.11.1 and gh 2.103.3; accepted as reported execution evidence, not independently reproduced here. Offline JUnit evidence remains limited to the pure domain fallback.

**Spec/gate verdict remains CONCERNS / incomplete verification gates.** Android CLI runtime/SDK installation, Git writes/commit, remote push/PR and macOS execution remain explicitly BLOCKED. Source approval does not establish those gates as passed or make Task 1 fully complete.
