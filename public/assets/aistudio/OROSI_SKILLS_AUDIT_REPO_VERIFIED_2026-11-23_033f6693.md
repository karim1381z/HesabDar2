# Orosi — Skills.sh Discovery & Repo-Fit Audit

**Prepared:** 2026-11-23  
**Repository:** https://github.com/karim1381z/HesabDar2  
**Purpose:** Complete the work requested by the uploaded `find-skills` skill, with candidates checked against the actual Orosi repository.  
**Scope:** Discovery, verification, compatibility and installation plan only. This document does not install anything or change project code.

---

## TL;DR

The earlier skills catalog was a useful first draft, but it was **not yet a reliable final answer** to Gemini's request. It promoted too many low-install/low-star community skills as top choices and confused some skill collections with individual installable skills.

For Orosi, start with these six:

| Priority | Skill | Why it belongs | Install route |
|---|---|---|---|
| P0 | `ui-ux-pro-max` | Strong UI/UX system, accessibility, touch, color, typography, dark-mode guidance and an actual Jetpack Compose stack dataset | `npx skills add https://github.com/nextlevelbuilder/ui-ux-pro-max-skill --skill ui-ux-pro-max` |
| P0 | `jetpack-compose-performance` | First-party Google instructions for profiling and fixing Compose jank, recomposition, state, lazy layouts and rendering | `android skills add jetpack-compose-performance --project=.` |
| P1 | `mobile-android-design` | Native Android / Material 3 / Compose patterns; a useful implementation-specific companion to Pro Max | `npx skills add https://github.com/wshobson/agents --skill mobile-android-design` |
| P1 | `testing-setup` | First-party Google test-strategy audit covering current test dependencies, unit/UI/screenshot/E2E testing | `android skills add testing-setup --project=.` |
| P1 | `android-clean-architecture` | Repositories, domain/data/presentation boundaries and dependency rules; use as an audit lens, not a mandate to modularize | `npx skills add https://github.com/affaan-m/ecc --skill android-clean-architecture` |
| P1 | `kotlin-coroutines-flows` | Structured concurrency, Flow/StateFlow, cancellation and coroutine testing | `npx skills add https://github.com/affaan-m/ecc --skill kotlin-coroutines-flows` |

For Compose performance, there is also a useful **Skydoves collection of individual skills**. It is a collection, not one single skill called `compose-performance-skills`. Select only the relevant child skills after the main Google audit; details below.

**Important:** install project-locally, verify the actual `SKILL.md` files and references, and do not assume Google AI Studio automatically discovers every agent directory. If automatic discovery is unavailable, have Gemini read the installed files explicitly. Do not globally install into an ephemeral cloud environment.

---

## 1. What the uploaded `find-skills` file actually requires

The project contains the uploaded skill at:

`public/assets/aistudio/SKILL_c00eeea0.md`

It is the `find-skills` skill. Its workflow requires:

1. Understand the task and relevant domain.
2. Check the skills.sh leaderboard first.
3. Search with specific queries when the leaderboard does not answer the need (`npx skills find ...`).
4. Verify each candidate rather than trusting search ranking alone:
   - Prefer 1K+ installs; treat skills below 100 installs cautiously.
   - Prefer reputable or official sources.
   - Treat repositories with fewer than 100 GitHub stars skeptically.
5. Present skill name, purpose, installs, source, exact install command and skills.sh page.
6. Offer installation only after explaining how it will be installed.

`npx skills find` is a **discovery/search command**, not an install command. To install, use `npx skills add ...` or the relevant official Android CLI command.

---

## 2. What is actually in the Orosi repository

Checked against the repository's `main` branch and the current source files. This is a source review, not a successful build or runtime benchmark.

### Confirmed stack and architecture

- Native Android app using Kotlin, Jetpack Compose and Material 3.
- Single active Android application module (`:app`); no active multi-module structure was found.
- Version catalog currently lists AGP `9.1.1`, Kotlin `2.2.10`, Compose BOM `2024.09.00`, Room `2.7.0`, Coroutines `1.10.2` and Robolectric `4.16.1`.
- Room database has four entities: transactions, bank accounts, commitments and app settings. Database version is `7`; `exportSchema = false` in the inspected source.
- `OrosiFinanceRepository` is a large repository implementation; `OrosiViewModel` constructs the database/repository directly. No active Hilt/Koin dependency-injection setup was found in the inspected Gradle configuration.
- Compose UI state is exposed through `StateFlow`; the activity uses `collectAsStateWithLifecycle()`.
- The app currently uses a dark Material 3 color scheme and explicitly supplies RTL layout direction.
- `MainActivity` requests the highest supported display refresh mode; this is a request to the display system, not proof that every frame is rendered at 120 Hz.
- Release builds have R8 minification and resource shrinking enabled.
- Gradle includes unit, coroutine, Compose UI, AndroidX and Robolectric test dependencies. Instrumented testing dependencies are also present.
- `OrosiRegressionSuite` declares `EXPECTED_TOTAL_CASES = 84`; the source describes it as running from JUnit and the in-app testing lab. The repository also contains substantive JVM unit tests. The inspected Android instrumented test file is only a placeholder/example, so do not claim that 84 full device UI tests already exist.
- `OROSI_CUSTOM_INSTRUCTIONS.md` identifies itself as v2.8 and contains critical financial, ledger, SMS, UX and performance invariants. It is the source of truth and must be read before any proposed code change.

### Consequences for skill selection

- UI/UX and Compose performance are directly relevant.
- Testing strategy is relevant because unit tests and Robolectric/Compose test dependencies exist, but real device/UI coverage needs a separate audit.
- Clean Architecture is relevant as a diagnostic lens because the repository and ViewModel are large, but it must **not** automatically trigger a multi-module rewrite or DI migration.
- Room guidance is relevant, especially schema evolution and migration testing. However, the best high-confidence candidates discovered were broader Google testing/architecture skills rather than a highly trusted dedicated Room skill.
- No need to add Retrofit, OkHttp, WorkManager, Navigation Compose, DataStore, Hilt or Koin merely because a skill mentions them. Their corresponding dependency declarations are absent or commented out in the current app build configuration.
- SMS and audio permissions plus the exported SMS receiver make security/privacy review relevant. Any changes here need to preserve Android platform requirements and Orosi's SMS ingestion behavior.

### Files inspected (representative, not exhaustive)

- `OROSI_CUSTOM_INSTRUCTIONS.md`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/ui/OrosiViewModel.kt`
- `app/src/main/java/com/example/data/local/OrosiDatabase.kt`
- `app/src/main/java/com/example/data/repository/OrosiFinanceRepository.kt`
- `app/src/main/java/com/example/domain/OrosiRegressionSuite.kt`
- `app/src/test/java/com/example/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt`
- `app/src/main/java/com/example/ui/theme/Theme.kt`
- `app/src/main/java/com/example/ui/theme/Color.kt`
- `app/src/main/java/com/example/receiver/BankSmsBroadcastReceiver.kt`

---

## 3. Recommended skills — verified shortlist

Install counts and GitHub stars below are a snapshot of the skills.sh/repository pages checked on 2026-11-23. They change over time. Official Google Android skills are identified separately; do not invent a skills.sh install count for them if no individual skills.sh listing is available.

### A. `ui-ux-pro-max` — primary UI/UX skill

- **Source:** [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)
- **skills.sh:** [ui-ux-pro-max](https://www.skills.sh/nextlevelbuilder/ui-ux-pro-max-skill/ui-ux-pro-max)
- **Snapshot:** about **466.5K installs**; repository page showed about **152.8K stars**; security badges on the skills.sh listing were Pass at the time checked.
- **What it provides:** searchable styles, product palettes, typography pairings, UX rules, icon guidance, chart patterns and technology-specific implementation guidance.
- **Why it fits Orosi:** its source actually contains a `jetpack-compose.csv` stack dataset. The skill can inform native Compose UI, dashboard hierarchy, financial data readability, touch interaction, accessibility, design consistency and dark-mode decisions. It can also run RTL-specific searches.
- **Exact install:**

```bash
npx skills add https://github.com/nextlevelbuilder/ui-ux-pro-max-skill --skill ui-ux-pro-max
```

- **Important source paths:**
  - `.claude/skills/ui-ux-pro-max/SKILL.md`
  - `.claude/skills/ui-ux-pro-max/data/stacks/jetpack-compose.csv`
  - `.claude/skills/ui-ux-pro-max/scripts/search.py`
  - `.claude/skills/ui-ux-pro-max/references/quick-reference.md`
- **Use it properly:** run the script from the path actually created by the installer. Do not assume that `.claude` is the installation destination in AI Studio; discover the actual location first.

Example queries (run separately):

```bash
python <ACTUAL_SKILL_DIR>/scripts/search.py "Orosi personal finance dashboard dark theme" --design-system --project-name "Orosi" --format markdown
python <ACTUAL_SKILL_DIR>/scripts/search.py "Compose state hoisting Material 3 list performance" --stack jetpack-compose
python <ACTUAL_SKILL_DIR>/scripts/search.py "Persian RTL financial dashboard" --domain product --rtl=full
python <ACTUAL_SKILL_DIR>/scripts/search.py "dark layered surfaces financial app" --domain style --rtl=full
```

The script has mode constraints: `--design-system` is its own mode; do not combine it with `--stack` or `--rtl`. Run the stack-specific and RTL searches separately. Do not use `--persist` during this audit.

### B. `jetpack-compose-performance` — official Google performance skill

- **Source:** [android/skills](https://github.com/android/skills/tree/main/jetpack-compose/jetpack-compose-performance)
- **Author:** Google LLC; skill metadata was updated 2026-11-10.
- **skills.sh count:** no individual skills.sh listing was established in this audit; the source is first-party Google Android skills, so do not fabricate a count.
- **Official Android CLI install:**

```bash
android skills add jetpack-compose-performance --project=.
```

- **What to audit:** state/snapshot reads, backward writes, stability, recomposition, lazy lists, modifiers, graphics, allocations, animation and rendering. It includes reference files and requires reading the relevant reference before raising or fixing a finding.
- **Orosi-specific caution:** the v2.8 custom instructions recommend stable keys/content types and immutable row models. Verify each lazy item in context. Do not blindly add/remove `@Immutable` or `@Stable`; check actual model shape, Compose compiler reports and measured impact. In particular, do not annotate entities or collections mechanically.
- **Profile only release/non-debuggable builds** and use real measurements. R8 being enabled does not prove that Baseline Profiles exist or that 120 Hz has been achieved.

#### Direct mapping to Gemini's original Compose-performance request

This is the explicit two-source answer to the original request; keep both sources in the shortlist because they serve different purposes.

**1) Discover the Skydoves collection on skills.sh (search only):**

```bash
npx skills find compose performance
```

This command searches for candidates; it does **not** install anything. The matching Skydoves project is a collection of individual skills, not a single installable skill named `compose-performance-skills`.

**2) Inspect the collection and install only relevant child skills:**

- Collection/source: [skydoves/compose-performance-skills](https://github.com/skydoves/compose-performance-skills)
- skills.sh: [compose-performance-skills](https://skills.sh/skydoves/compose-performance-skills)
- Useful child skills for this app: `optimizing-lazy-layouts`, `diagnosing-compose-stability`, `stabilizing-compose-types`, `deferring-state-reads`, `choosing-derivedstateof`, `configuring-lazy-prefetch`, `generating-baseline-profiles`, `testing-compose-in-release-mode`, and `configuring-r8-for-compose`.

Example installs (run only after checking the actual skill listing and need):

```bash
npx skills add https://github.com/skydoves/compose-performance-skills --skill optimizing-lazy-layouts
npx skills add https://github.com/skydoves/compose-performance-skills --skill diagnosing-compose-stability
npx skills add https://github.com/skydoves/compose-performance-skills --skill deferring-state-reads
npx skills add https://github.com/skydoves/compose-performance-skills --skill generating-baseline-profiles
```

**3) Install the official Google Android skill (actual install):**

```bash
android skills add jetpack-compose-performance --project=.
```

- Official source: [android/skills — jetpack-compose-performance](https://github.com/android/skills/tree/main/jetpack-compose/jetpack-compose-performance)
- Official Android CLI documentation: [Android skills](https://developer.android.com/tools/agents/android-skills)
- This is the primary audit skill; use the Skydoves child skills as targeted supplements, not as a replacement.

**4) Apply the requested checks specifically to Orosi:**

| Gemini's requested topic | What the audit must inspect | Orosi-specific note |
|---|---|---|
| Recomposition and state reads | Snapshot reads, backwards writes, derived state, Composition vs Layout/Draw phase | Inspect actual hot composables; distinguish static suspicion from measured cost |
| `@Immutable` / `@Stable` | Real immutability, stable parameters, compiler stability reports | Do not add annotations mechanically; Room entities already have annotations in some places, which must be evaluated rather than copied blindly |
| `LazyColumn` / `LazyRow` | Stable keys, `contentType`, item composable skippability, allocations, prefetch | Cross-check the v2.8 row-model and list invariants |
| `collectAsStateWithLifecycle` | Lifecycle-aware collection, lifecycle owner, start/stop behavior | Already imported and used in `MainActivity.kt`; verify current use and any other collection sites rather than adding it indiscriminately |
| Baseline Profiles | Whether a profile generator/module, rules and release integration actually exist | Do not claim profiles exist just because the skill recommends them |
| R8 | Release minification, shrink resources and keep rules | `app/build.gradle.kts` already enables minification and resource shrinking; inspect `proguard-rules.pro` for correctness |
| 120 Hz / jank | Release-mode Macrobenchmark or equivalent device trace, frame timing and dropped frames | `MainActivity` requests the highest supported display mode; this is not proof of 120 fps rendering |

**Required output:** for every finding, provide file path and line, evidence, impact, confidence (`verified`, `probable`, or `needs runtime measurement`), recommended correction and regression/performance test. Do not change code during this audit.

### C. `mobile-android-design` — native Android design companion

- **Source:** [wshobson/agents](https://github.com/wshobson/agents/tree/main/plugins/ui-design/skills/mobile-android-design)
- **skills.sh:** [mobile-android-design](https://skills.sh/wshobson/agents/mobile-android-design)
- **Snapshot:** about **26.9K installs** and **42.8K repository stars**; security checks displayed Pass on the page checked.
- **What it covers:** Material Design 3, Compose patterns, native Android interaction, adaptive layouts, themes, accessibility and state hoisting.
- **Why it fits:** a practical Android-specific companion to UI UX Pro Max. Use both as complementary references, but resolve differences in favor of Orosi's approved design decisions and actual implementation constraints.
- **Exact install:**

```bash
npx skills add https://github.com/wshobson/agents --skill mobile-android-design
```

### D. `testing-setup` — official Google Android testing strategy

- **Source:** [android/skills](https://github.com/android/skills/tree/main/testing/testing-setup)
- **Author:** Google LLC; skill metadata was updated 2026-11-14.
- **skills.sh:** [testing-setup](https://skills.sh/android/skills/testing-setup)
- **Snapshot:** about **3.4K installs**; Android skills repository showed about **10.7K stars**. One security service displayed a warning on the listing; this is not by itself proof of malicious code, but inspect before installation.
- **Official Android CLI install:**

```bash
android skills add testing-setup --project=.
```

- **What it covers:** inspect current JUnit/Robolectric/Compose/Espresso setup, test source sets, UI tests, screenshot tests and end-to-end strategy; produce an analysis report.
- **Orosi-specific caution:** the skill may recommend installing Hilt if no DI exists. Do not follow that recommendation automatically. For this task, audit the existing setup only. Do not add Hilt, MockK, screenshot tooling or new dependencies without a separate approval.

### E. `android-clean-architecture` — architecture review lens

- **Source:** [affaan-m/ecc](https://github.com/affaan-m/ecc/tree/main/skills/android-clean-architecture)
- **skills.sh:** [android-clean-architecture](https://skills.sh/affaan-m/ecc/android-clean-architecture)
- **Snapshot:** about **7.3K installs** and **308.4K repository stars**; security badges displayed Pass on the page checked.
- **What it covers:** dependency inversion, domain/data/presentation boundaries, repository patterns, use cases and module boundaries.
- **Why it fits:** Orosi currently has a very large repository implementation and ViewModel with direct construction of dependencies. This skill can help identify real coupling and testability risks.
- **Exact install:**

```bash
npx skills add https://github.com/affaan-m/ecc --skill android-clean-architecture
```

- **Do not misapply:** its recommended architecture often uses multiple modules and DI. Orosi is currently a single app module with manual construction. Do not make multi-module, Hilt or Koin migration a required finding without demonstrating a concrete benefit and receiving approval.

### F. `kotlin-coroutines-flows` — Kotlin concurrency and streams

- **Source:** [affaan-m/ecc](https://github.com/affaan-m/ecc/tree/main/skills/kotlin-coroutines-flows)
- **skills.sh:** [kotlin-coroutines-flows](https://skills.sh/affaan-m/ecc/kotlin-coroutines-flows)
- **Snapshot:** about **6.4K installs** and **303.9K repository stars**; security badges displayed Pass on the page checked.
- **What it covers:** structured concurrency, Flow/StateFlow/SharedFlow, cancellation, error handling, concurrency and coroutine testing.
- **Why it fits:** Orosi uses `StateFlow`, `combine`, `stateIn`, `viewModelScope`, Flow-backed Room access and background SMS processing.
- **Exact install:**

```bash
npx skills add https://github.com/affaan-m/ecc --skill kotlin-coroutines-flows
```

---

## 4. Useful supplementary skill collections (not one skill each)

These collections can be useful after the core audit, but they should not be represented as a single installable skill or installed wholesale.

### Skydoves `compose-performance-skills`

- **Repository:** [skydoves/compose-performance-skills](https://github.com/skydoves/compose-performance-skills)
- **skills.sh collection page:** [compose-performance-skills](https://skills.sh/skydoves/compose-performance-skills)
- **Collection:** 26 individual skill folders. The repository has roughly 495 GitHub stars in the snapshot checked; most individual skills have low install counts (many below 100), so under the uploaded `find-skills` criteria they are supplementary, not top-priority picks.
- **Useful child skills for Orosi:**
  - `optimizing-lazy-layouts`
  - `diagnosing-compose-stability`
  - `stabilizing-compose-types`
  - `deferring-state-reads`
  - `choosing-derivedstateof`
  - `configuring-lazy-prefetch`
  - `generating-baseline-profiles`
  - `testing-compose-in-release-mode`
  - `configuring-r8-for-compose`
- **Example exact child install:**

```bash
npx skills add https://github.com/skydoves/compose-performance-skills --skill optimizing-lazy-layouts
npx skills add https://github.com/skydoves/compose-performance-skills --skill diagnosing-compose-stability
```

Select only a few children after consulting the Google skill; avoid installing the entire collection without a reason.

### Skydoves `android-testing-skills`

- **Repository:** [skydoves/android-testing-skills](https://github.com/skydoves/android-testing-skills)
- **Collection:** 54 individual skills across Compose, fundamentals, Kotlin and other Android testing topics. The repository itself has over 300 stars, but individual child skill install counts are generally low. Its own README explicitly says users do not need all 54.
- Use only a child skill that addresses a demonstrated gap in Orosi's current test setup. Do not install all 54.

### `claude-android-ninja` — optional second opinion, not core

- **Source:** [Drjacky/claude-android-ninja](https://github.com/Drjacky/claude-android-ninja)
- **skills.sh:** [claude-android-ninja](https://skills.sh/drjacky/claude-android-ninja/claude-android-ninja)
- **Snapshot:** about **589 installs**, **127 repository stars**, security checks Pass on the page checked.
- **Install:**

```bash
npx skills add https://github.com/drjacky/claude-android-ninja --skill claude-android-ninja
```

- It is a valid Android/Kotlin/Compose skill, but parts of its templates and guidance assume newer Room 3, Navigation 3 and other dependencies. Use it only as an optional review reference; align everything with Orosi's actual Room 2.7.0 and active Gradle dependencies. Do not copy templates or upgrade dependencies automatically.

---

## 5. Names from Gemini's draft — what is and is not verified

| Name Gemini used | Audit result | Correct treatment |
|---|---|---|
| `compose-performance-skills` | A real Skydoves **collection/repository**, not one individual skill | Choose individual child skills from section 4; use Google's official `jetpack-compose-performance` as the main skill |
| `jetpack-compose-performance` | Real official Google Android skill | Keep; install with Android CLI |
| `ui-ux-pro-max` | Real, popular, high-trust skill; has an actual Jetpack Compose data stack | Keep as P0; do not omit |
| `dark-mode-design` | No sufficiently verified standalone Android/Compose skill with this exact identity was established in this audit | Do not invent a package name; cover dark-mode through UI UX Pro Max + native Material 3 review + Orosi's existing theme |
| `android-clean-architecture` | Real skill from affaan-m/ecc | Use this exact package name and source |
| `kotlin-android-architecture` | Exact reliable skill identity was not established | Do not install by guessed name; use `android-clean-architecture` |
| `android-testing-skills` | A Skydoves repository/collection of 54 skills, not one skill | Select individual child skills only if needed |
| `claude-android-ninja` | Real valid skill | Optional second opinion only; watch its Room 3 / Navigation 3 assumptions |
| `android-room-database` (krutikjain) | File exists, but snapshot was only about 128 installs and source repository has about 13 stars | Not a primary recommendation under the uploaded `find-skills` quality thresholds |
| `android-compose-foundations`, `android-material3-design-system`, `android-compose-accessibility` (krutikjain) | Files exist, but individual install counts are generally under 1K and source repo has about 13 stars | Treat as experimental/low-confidence references, not top recommendations |
| `design-mobile-apps` | Despite a very high leaderboard position, its skill is a REST API integration for Sleek Design, requires `SLEEK_API_KEY`, has very few GitHub stars and a failed Snyk indicator on the page checked | Exclude: not an appropriate local Android UI/Compose skill |

No standalone `dark-mode-design` or exact `kotlin-android-architecture` name should be fabricated just to complete the table.

---

## 6. Installation in Google AI Studio — safe procedure

Do not assume that an ephemeral cloud workspace supports persistent global installation. Do not use `-g` for this project workflow. First check the actual environment and install locally.

### Step 1 — inspect available CLIs and repository root

```bash
pwd
command -v npx || true
command -v android || true
find . -maxdepth 3 -type d \( -name .agents -o -name .gemini -o -name .claude \) -print
```

### Step 2 — install the core skills

For skills.sh packages:

```bash
npx skills add https://github.com/nextlevelbuilder/ui-ux-pro-max-skill --skill ui-ux-pro-max
npx skills add https://github.com/wshobson/agents --skill mobile-android-design
npx skills add https://github.com/affaan-m/ecc --skill android-clean-architecture
npx skills add https://github.com/affaan-m/ecc --skill kotlin-coroutines-flows
```

For Google's Android CLI skills:

```bash
android skills add jetpack-compose-performance --project=.
android skills add testing-setup --project=.
```

If `android` is unavailable, do not claim installation succeeded. Check whether the same skills can be acquired through the official `android/skills` repository and manually place the complete skill folders (including `references/`) in a workspace skill directory that the current agent actually reads. Do not copy only the SKILL.md if it links to local reference files.

### Step 3 — verify installation (mandatory)

```bash
find . -type f -name SKILL.md -print
```

For each selected skill:
1. Open its `SKILL.md` completely.
2. Resolve and read every local reference that is mandatory for the requested audit category.
3. Verify the source directory, actual skill name and package/commit version.
4. Report the concrete file path. A successful CLI exit alone is not proof that Gemini has loaded or read the skill.

Gemini CLI documentation describes workspace skills under `.gemini/skills/` and `.agents/skills/`. However, for Google AI Studio Build mode, automatic discovery must be verified inside that exact environment. If it is not automatic, tell Gemini to explicitly read the installed SKILL.md and references before auditing. Do not state that the skill is active unless this has been verified.

### Step 4 — fallback if persistent installation is unavailable

If the cloud environment resets or does not support the required CLI:
- Keep the upstream source links and version/commit in the report.
- Download/copy the entire required skill folder, including `references`, into the repository workspace only if the user approves a persistent project change.
- Otherwise, read the files during the current session and produce a concise `OROSI_SKILL_RULES.md` or append approved rules to `OROSI_CUSTOM_INSTRUCTIONS.md` only after explicit approval.
- Never say a skill is “installed permanently” if only its instructions were read in the current session.

---

## 7. Exact audit plan for Orosi — no code changes yet

1. **Read the constitution:** read `OROSI_CUSTOM_INSTRUCTIONS.md` end-to-end first. Its financial invariants override generic skill suggestions.
2. **Confirm the baseline:** inspect Gradle files, Room database/version, repository, ViewModel, SMS receiver/manifest, themes, screen composables and existing test suites.
3. **UI/UX audit:** use `ui-ux-pro-max` with the `jetpack-compose` stack plus separate RTL searches; use `mobile-android-design` as a native Material 3 cross-check. Audit readability of balances, transaction amounts, charts, labels, spacing, touch targets, color contrast, typography, dark surfaces and navigation. Do not invent a redesign contrary to approved decisions.
4. **Compose performance audit:** use Google `jetpack-compose-performance`; read each relevant mandatory reference before reporting a finding. Check the actual lazy lists, stable keys/content types, parameter stability, recomposition, expensive formatting, image/logo decoding, modifiers, animation, cold start and frame performance. Separate static code concerns from issues proven by profiling.
5. **Architecture audit:** use `android-clean-architecture` to identify specific coupling, oversized classes, responsibilities and testability issues. Do not require multiple modules, DI, use-case wrappers or a rewrite without concrete evidence and a separate approved plan.
6. **Room audit:** inspect entities, DAO query shapes, write transactions, indexes, database version, migration strategy and testability. `exportSchema=false` is a finding to assess, not permission to change schema or migration behavior automatically. Preserve financial ledger semantics.
7. **Coroutines audit:** inspect scopes, cancellation, error handling, receiver lifecycle, synchronization/mutex behavior, Room flows and main-thread work. Preserve SMS processing and timestamp/order guarantees.
8. **Testing audit:** run/report the actual 84-case regression suite and existing unit tests if the environment permits. Inspect the difference between JVM tests, Robolectric, instrumented and in-app tests. Do not label a JVM test as a real-device UI test. Identify gaps and propose a staged test plan.
9. **Security/privacy audit:** review `READ_SMS`, `RECEIVE_SMS`, `RECORD_AUDIO`, exported receiver exposure, backup rules, log/backup contents and data minimization. Do not change manifest or SMS behavior without a separate proposal.
10. **Report only:** classify each finding as `verified`, `probable`, or `needs runtime measurement`; give file paths/line numbers, impact, evidence, recommended fix and regression tests. Do not change source, Gradle dependencies, manifests, Room schema, design system, or any existing test cases during this first pass.

### Mandatory Orosi constraints

- Financial correctness, ledger integrity and no phantom records are P0. Never alter a bank SMS's exact amount, direction or bank identity when tagging its purpose.
- Preserve payday-cycle calculations, cashflow truth, self-transfer exclusions and commitment rules.
- Preserve the existing 84 regression cases; do not delete, weaken or silently change expected results.
- No blanket `@Immutable` / `@Stable` annotations or removals. Follow Google's current Compose guidance and prove the impact.
- Do not claim 120 Hz performance from `preferredDisplayModeId` alone; measure on a device and report dropped frames/jank.
- Do not add dependencies or introduce Hilt/Koin, Navigation Compose, DataStore, WorkManager, Retrofit/OkHttp or a multi-module conversion as part of the audit.
- Do not use `public/assets/aistudio` wholesale as APK assets; it contains large review media. Preserve the current selective asset-copy strategy.

---

## 8. Ready-to-paste Gemini prompt

```text
The attached file is the completed skills.sh discovery and Orosi repository-fit audit. Follow it as the execution plan.

First, read OROSI_CUSTOM_INSTRUCTIONS.md completely. It is the product's source of truth, especially its P0 financial invariants and P1 performance constraints.

Then verify the current repository state yourself. Do not assume the report is still current if the branch has moved. Confirm Gradle versions, active dependencies, Room version/schema, architecture, UI theme, RTL handling, the 84-case regression suite and current test source sets.

Use the six core skills in the report. Install project-locally only when the actual AI Studio workspace supports it. Verify every SKILL.md and its referenced files by opening them. If the skill loader does not automatically discover them in this Build workspace, read the files explicitly. Do not claim installation or activation without evidence.

For UI UX Pro Max, use its actual Jetpack Compose dataset and run the documented search.py queries. Run design-system generation separately from stack/RTL searches; do not combine incompatible flags. Do not persist generated design files during this audit.

For Compose performance, read each applicable reference before flagging an issue. Do not blanket-add or remove @Immutable/@Stable. Distinguish static code analysis from runtime-measured jank. Check the actual release build and do not equate a refresh-rate request with measured 120 Hz rendering.

For testing-setup, audit the dependencies and current test pyramid. The project already has JVM tests, Robolectric and Compose/Espresso test dependencies; do not install Hilt, MockK or screenshot tooling merely because a generic instruction suggests it.

For architecture and Coroutines, identify concrete coupling/cancellation/lifecycle issues. Do not force multi-module architecture, Hilt/Koin or use-case wrappers without evidence.

For Room, report the implications of Room 2.7.0, database version 7 and exportSchema=false. Do not change the database schema or migrations.

Do not modify source files, Gradle files, dependencies, AndroidManifest, database schema, UI theme, OROSI_CUSTOM_INSTRUCTIONS.md or existing test cases. Do not create commits or pull requests. This stage is an audit only.

Return a concise TL;DR first, then:
1. Skills actually installed/read, their exact paths and verification evidence.
2. Current project baseline.
3. Findings grouped by P0/P1/P2 with evidence and exact file paths/line numbers.
4. Performance findings that still need device profiling.
5. Testing coverage gaps (clearly distinguishing JVM, Robolectric, instrumented and in-app tests).
6. Recommended fixes ordered by risk/benefit, with regression tests for each.
7. Any skill that could not be installed or loaded, and the exact reason.
```

---

## 9. Sources

- Uploaded skill in Orosi repository: [find-skills SKILL.md](https://github.com/karim1381z/HesabDar2/blob/main/public/assets/aistudio/SKILL_c00eeea0.md)
- Orosi repository: [karim1381z/HesabDar2](https://github.com/karim1381z/HesabDar2)
- UI UX Pro Max: [skills.sh listing](https://www.skills.sh/nextlevelbuilder/ui-ux-pro-max-skill/ui-ux-pro-max) · [source repository](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)
- Android Mobile Design: [skills.sh listing](https://skills.sh/wshobson/agents/mobile-android-design) · [source file](https://github.com/wshobson/agents/blob/main/plugins/ui-design/skills/mobile-android-design/SKILL.md)
- Google Android skills: [source repository](https://github.com/android/skills) · [Jetpack Compose Performance](https://github.com/android/skills/tree/main/jetpack-compose/jetpack-compose-performance) · [Testing Setup](https://github.com/android/skills/tree/main/testing/testing-setup)
- Android CLI / Android skills docs: [Android skills](https://developer.android.com/tools/agents/android-skills) · [Android CLI](https://developer.android.com/tools/agents/android-cli)
- Android Clean Architecture: [skills.sh listing](https://skills.sh/affaan-m/ecc/android-clean-architecture) · [source](https://github.com/affaan-m/ecc/tree/main/skills/android-clean-architecture)
- Kotlin Coroutines & Flows: [skills.sh listing](https://skills.sh/affaan-m/ecc/kotlin-coroutines-flows) · [source](https://github.com/affaan-m/ecc/tree/main/skills/kotlin-coroutines-flows)
- Skydoves Compose Performance collection: [skills.sh](https://skills.sh/skydoves/compose-performance-skills) · [source / index](https://github.com/skydoves/compose-performance-skills)
- Skydoves Android Testing collection: [source / README](https://github.com/skydoves/android-testing-skills)
- Claude Android Ninja: [skills.sh listing](https://skills.sh/drjacky/claude-android-ninja/claude-android-ninja) · [source](https://github.com/Drjacky/claude-android-ninja)

**Audit limitations:** This document records a GitHub source review and public catalogue checks on 2026-11-23. It does not prove that skills were installed in the user's Google AI Studio session, that all tests pass, or that the app meets 120 Hz performance targets. Those require execution in the user's actual workspace/device.
