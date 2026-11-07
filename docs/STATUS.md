# Zapys — поточний стан

## 2026-11-07 — Task 8 Planned default verified and reviewed

- Fresh appointment forms now start as Planned. Normal picker uses four explicit statuses; loaded drafts display Planned for primary save without mutating their in-memory/persisted draft identity during rendering.
- Explicit/back draft save, calendar30-minute drafts, repeat resets and existing normal status round-trips remain covered. Failed validation plus real recreation retains raw fields and draft identity.
- Common helper passed:107unit tests,lint0errors/16originalwarnings,bothAPKs. Device33PASS covers status choices/mapping/persistence, client selection, draft/repeat behavior, validation and existing calendar/header interactions.
- /root/task8_review approved specification and quality, no blockers. Task8 separate commit pending,Tasks9–10pending. No PR,merge or release.

## 2026-11-07 — Task 7 recipient and client-selection verified and reviewed

- Client/reminder UI now explains manual recipient selection in the messenger. Legacy recipient properties and JSON remain exact; enabled reminders with blank recipients use the existing schedule/state/lifecycle gates.
- An existing selected client takes precedence over inactive inline text. The whole inline form hides initially and after recreation; toggling back preserves raw input. Client persistence followed by an appointment conflict/retry reuses the same identity.
- Common helper passed:106unit tests,lint0errors/16originalwarnings,bothAPKs. Device35PASS covers selection/recreation, real SQLite retry, legacy recipient preservation, manual intent payload and phone/name/contact/reminder regressions. No actual messenger launch or message send.
- /root/task7_review approved specification for both7a/7b and code quality, no blockers. Task7 complete in commit31fd586;Task8 in progress,Tasks9–10pending. No PR,merge or release.

## 2026-11-07 — Task 6 contact action verified and reviewed

- Contact action now follows the heading/back area and precedes name/phone on both client create and edit forms. Existing binding, provider flow and field restoration IDs remain unchanged.
- Common helper passed:101unit tests,lint0errors/16originalwarnings,bothAPKs. Device28PASS covers ordering, selective synthetic result/cancel/read failure, manual entry, actual recreation/ownership, phone/name validation and color-state regression. No real contacts accessed.
- Twenty-four native renders cover light/dark,320/360/411dp,font1/2,create/edit. Four narrow light/dark normal/large images inspected; /root/task6_review approved specification and quality, no blockers. Task6 complete in commit94bd905;Task7 in progress,Tasks8–10pending. No PR,merge or release.

## 2026-11-07 — Task 5 name limits verified and reviewed

- Each personal-name field permits 50 Unicode code points after outer trim. Full and inline forms share domain validation and visible guidance, preserve overlong raw input, and focus the first invalid field. Valid combined display names may exceed 50.
- Imported, pasted and legacy values are never truncated. Actual recreation preserves failed form input; corrected saves preserve raw phone formatting and existing appointment snapshots.
- Common helper exit0: 101 unit tests PASS, lint0errors/16originalwarnings, both APKs. Affected device suite40PASS, including typing/paste, synthetic import, full/inline recreation, legacy persistence and color regression.
- /root/task5_review: specification PASS, quality APPROVED, no blockers. Task5 complete in commit aee0b0c; Task6 in progress, Tasks7–10 pending. No PR, merge or release.

## 2026-11-07 — Task 4 Ukrainian phone validation verified

- Shared `PhoneRules.normalize` validates full Ukrainian mobile/fixed-line formats and provides canonical duplicate comparison. Full client, inline client and imported-contact saves reject invalid numbers with focused phone errors while preserving input. Accepted raw formatting, including edge spaces/NBSP, is stored unchanged.
- Legacy invalid clients remain readable; appointments referencing them still save. Duplicate cancellation writes nothing; confirmation creates a distinct client UUID. No read-time normalization or data migration.
- Common gate: 92 unit tests PASS, lint 0 errors/16 original warnings, both APKs. Affected device suite: 35 tests PASS, covering full/inline/import validation, duplicate interaction, client/appointment/reminder persistence and color regression. Intentional invalid legacy fixtures are retained.
- `/root/task4_review`: specification APPROVED, quality APPROVED, no blockers. Task 4 complete in commit `d4ac5c5`; Task 5 in progress, Tasks 6–10 pending. No PR, merge or release.

## 2026-11-07 — Task 3 visual color picker verified

- Client/service colors now use a named swatch, four preset choices and a custom hue/saturation/value picker with live preview, clear, confirm and cancel. Existing ARGB is preserved until a deliberate new choice; new custom colors are opaque. The original `Theme.colors` caller interface is retained.
- Committed value and pending dialog HSV/null/open state restore across real activity recreation. Initial rendering/restoration/cancel do not mark forms dirty; confirmation notifies once. Client/service save-reopen paths were exercised with synthetic data.
- Common gate PASS: 84 unit tests, lint 0 errors/16 original warnings, both APKs. Device PASS: 10 picker/activity tests and 11 calendar regressions. All 13 final native/live images inspected, covering light/dark, enlarged text and arbitrary/black/white calendar colors.
- Visual findings were reproduced and fixed before review: unchecked dark radio contrast was 1.13:1, and expanded large-text Cancel showed only 67/142px. Explicit themed indicators and a scrollable body with reserved action space now pass the unchanged contrast/visibility assertions.
- `/root/task3_review`: specification APPROVED, quality APPROVED, no blocking findings. Task 3 complete in commit `14c2bdd`; Task 4 in progress, Tasks 5–10 pending. No PR, merge or mobile release.

## 2026-11-07 — Task 2 bottom navigation verified

- Navigation now uses one baseline-disabled adaptive row with common button bounds, full Ukrainian labels, selected styling and at least 48dp touch height. Changes are local to navigation; root system-inset handling is preserved.
- Common gate: 84 unit tests PASS, lint 0 errors/16 original warnings, both APKs pass. Device checks: four navigation tests plus three additional real orientation/system-navigation inset checks PASS; Task 1 calendar regression 11 PASS. Measured widths 320/360/411dp and landscape widths, font scales 1/1.3/2; light/dark renders and all four attached configurations inspected.
- `/root/task2_review` approved specification and code quality after matched baseline comparison. Exact pre-task `283782e` and current app have identical clipped Calendar New Appointment bounds in landscape; this is an existing calendar viewport defect, recorded as required Task 9/final-regression follow-up. It is not claimed fixed here.
- Task 2 complete in commit `17ba516`. Tasks 3–10 pending; no aggregate PR or merge.

## 2026-11-06 — execution preflight in local Codex

- Read AGENTS.md and the approved ten-task handoff/spec/plan. Actual Superpowers skills are available and loaded; independent preflight reviewer `/root/preflight_review` checked all ten tasks and task pairs. Accepted rulings are recorded in the active progress ledger.
- Created `feat/android-ux-improvements` from `384f290` using the supported Git approval flow. GitHub CLI `api user --jq .login` returned `antonkolosok`, exit 0. Preserve unrelated `android/.idea/`.
- Common Android baseline PASS: approved `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1` exited 0; 84 unit tests across 13 suites, 0 failures/errors; lint, app APK and instrumentation APK assembly passed. Sandbox attempt failed dependency access; approved retry succeeded. Device/UI checks remain UNRUN while the dedicated emulator image installs; no attached devices were found. WHPX acceleration check returned usable (exit 0).
- Preparation/rules commit: `e10f748`. Task 1 complete in commit `283782e`: the full-width date selector starts Monday at the content edge; separate week headings retain the 42dp grid gutter. Common checks: 84 unit tests PASS, lint 0 errors/16 existing warnings, both APKs built. Device checks: 6 header tests plus 5 calendar interaction tests PASS; 16 light/dark/large-text native renders inspected, maximum measured heading drift 0.232px. Reviewer `/root/task1_review` approved specification and code quality with no blocking findings.
- Task 9 must preserve historical client snapshots through persistence; the existing editor save refreshes them. The accepted ruling adds a narrow move persistence path and a reopen regression test without changing editor save semantics.
- Tasks 2–10 remain pending their individual gates. No PR, merge, auto-merge or release performed.

## 2026-11-06 — новий пакет із 10 Android-поліпшень

- Користувач затвердив нове правило для майбутньої роботи: кожен створений агент застосовує відповідні Superpowers skills; окрема задача → тести → незалежне рев'ю → окремий коміт. Один спільний PR лише після всіх задач; merge/auto-merge не виконувати. Правило записане в кореневому `AGENTS.md`.
- Підготовлені специфікація `superpowers/specs/2026-11-06-android-ux-improvements.md`, покроковий план `superpowers/plans/2026-11-06-android-ux-improvements.md`, ledger `superpowers/progress/2026-11-06-android-ux-improvements.md` і стартове завдання `CODEX_HANDOFF.md` для нового локального Codex.
- Два агенти з Superpowers виконали read-only аналіз календаря/навігації та форм/валідації. Виявлені важливі зв'язки: 42dp spacer відповідає gutter сітки; приховані inline-поля зараз можуть замінити вибраного клієнта; recipient перевіряється у domain/manual-reminder логіці; DRAFT не можна прибирати зі збереження разом із пунктом picker. План враховує ці зв'язки.
- Свіжий локальний Git уже містить MVP-коміт `384f290` (`feat: add native local calendar MVP`), гілка `feat/local-mvp`; це новіше за попередні записи про untracked source. На момент початку підготовки сторонньою незбереженою зміною була `android/.idea/`; її не змінювали.
- Усі десять продуктових задач поки **PENDING**: ця сесія готує передачу, оскільки не може виконати обов'язкові коміти через read-only `.git`. Код застосунку цим пакетом не змінений; нових тестових PASS, комітів, push або PR для пакета не заявлено. Виконання починається з preflight і task 1 у локальному Codex.

## 2026-11-06 — авторизація GitHub перевірена у двох середовищах

- У звичайному PowerShell користувача `gh api user --jq .login` двічі повернула `antonkolosok`: авторизація CLI на комп'ютері працює. Повторний вхід не потрібний.
- Та сама команда з цієї ізольованої сесії після підтвердження користувача повернула HTTP401. Помилка стосується доступу з цієї сесії; попереднє твердження про недійсну авторизацію користувача було надто широким. Точний механізм розбіжності не встановлено.
- Читання через Git і конектор GitHub працює; остання перевірка remote не знайшла гілок. Правила поточної сесії залишають `.git` лише для читання. Нових commit/push/PR під час перевірки не виконували.

## 2026-11-06 — виправлена компіляція Android, APK зібрані

- Відтворено помилку `Theme.kt:22`: усередині `LinearLayout.apply` неуточнений `background` означав `Drawable` компонента замість кольору теми. Виправлено на `this@Theme.background`; код застосунку скомпілювався з Kotlin 2.1.20.
- Наступна перевірка виявила відсутні platform test libraries для наявних `AndroidTestCase`/`InstrumentationTestCase`. Додано `useLibrary` для `android.test.runner`, `android.test.base`, `android.test.mock`; тестові джерела тепер компілюються. Згенерований test manifest уже містить runner.
- `--offline :app:assembleDebug :app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**, exit 0. Створені `android/app/build/outputs/apk/debug/app-debug.apk` та `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. Лог: `artifacts/android-theme-fix-2026-11-06/apk-build-after.log`.
- Повна команда з `scripts/build-android.ps1` ще не пройшла: завантаження `junit:junit:4.13.2` заблоковане мережевими обмеженнями; для lint також немає кешованого `com.android.tools.lint:lint-gradle:31.9.2`. Логи `build-after.log` та `cached-build.log` у тій самій папці. Потрібен повторний запуск helper у звичайному PowerShell. 84 офлайн-тести нижче — попередній результат, не новий Gradle PASS.
- Запуск застосунку, instrumentation на пристрої, iOS та Appetize залишаються UNRUN. Попередження SDK XML/застарілих API не зупинили APK-збірку; Kotlin daemon у фінальному прогоні використав успішний fallback після помилки тимчасового файла.

## 2026-11-06 — попереднє оновлення SDK та Appetize

- Після запуску встановлення користувачем потрібні файли Android SDK35, Build Tools35.0.0 та platform-tools з'явилися у `.tools/android-sdk`. `aapt2 version` успішно повернув `2.19-11948202`.
- Canonical Gradle wrapper тепер запускає Gradle8.11.1: кеш заповнено вже наявним локальним ZIP, SHA256 якого збігається із закріпленим у wrapper. Мережевого обходу чи нового завантаження для цього не було.
- Реальна команда `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` завершилася exit1 до компіляції: не вдалося розв'язати залежність `com.android.application:8.9.2` з Google/Maven/Plugin Portal. APK немає; нативні тести та lint не виконалися. Лог: `artifacts/android-build-2026-11-06/gradle-checks-after-local-cache.log`. `adb version` у цьому середовищі також упирається в заборону запису до профілю користувача; файли профілю не змінювалися.
- Для наступної спроби у звичайному PowerShell підготовлено `scripts/build-android.ps1`: сам налаштовує проєктні змінні, запускає всі чотири задачі та перевіряє наявність APK. Відсутність SDK більше не є поточним блокером.
- Користувач обрав Appetize. Додано `scripts/build-appetize-ios.sh` та CI-артефакт `zapys-ios-appetize` з `Zapys.app.zip`; пакування заплановане після успішних simulator/UI тестів. iOS-збірку, пакування та завантаження в Appetize ще не виконано. Інструкція: `APPETIZE.md`; наявний Android APK також можна буде завантажити в Appetize.
- Перевірено структуру CI YAML, UTF-8/LF нового shell-скрипта та синтаксис PowerShell helper. Це не виконання нативної збірки. GitHub-авторизація, публікація коду й macOS CI залишаються потрібними для iPhone.

## 2026-11-06 — попередня спроба встановлення

- За запитом користувача повторно виконано `./scripts/bootstrap.ps1 -InstallSdk -AcceptAndroidLicenses`. Java 17.0.20.1, Gradle 8.11.1 та gh 2.103.3 запускаються. Встановлення SDK завершилося з exit 1: завантажувач Android CLI отримав `Connection refused` для `https://dl.google.com/android/cli/latest/windows_x86_64/android-cli.exe`.
- Android SDK35/platform-tools/build-tools35.0.0 не встановлено. Перевірені як проєктна папка, так і стандартна `C:\Users\anton\AppData\Local\Android\Sdk`; готового SDK немає. Лог спроби: `artifacts/setup-2026-11-06/android-setup.log`.
- Цей комп'ютер працює на Windows 11; Xcode потребує macOS. Уже підготовлений workflow може виконати Swift та iPhone simulator перевірки на `macos-15`, без власного Mac.
- Свіжа read-only перевірка GitHub: репозиторій `antonkolosok/Zapys` порожній, гілок і запусків Actions немає. Конектор повертає 403 при перевірці дозволу; локальний `gh auth status --hostname github.com` повідомляє недійсний токен. CI не запускали, доступи не змінювали, нічого не публікували.
- Наступна доступна дія користувача: виконати команду встановлення SDK у звичайному PowerShell поза обмеженим середовищем ChatGPT; для iOS CI відновити робочий доступ до GitHub і завантажити код/workflow. Нові збірки або тести застосунків цього дня не виконувалися; нижче збережено результат перевірки коду від 3 листопада.

Стан на 2026-11-03. Вихідний код Tasks 1–11 та фінальні виправлення пройшли незалежний огляд. У Task 12 завершено доступну перевірку коду й документацію: 84 офлайн-тести пройшли, усі шість фінальних зауважень закриті повторним рев'ю (`reviews/final.md`). Нативні збірки, запуск і публікація PR залишаються заблокованими. Огляд джерел не доводить, що застосунки складаються або працюють.

| Перевірка | Фактичний стан |
| --- | --- |
| Повний офлайн Android domain | Фінальний прогін після виправлень: 84 PASS, Kotlin 2.0.20/JUnit 4.13.2, exit 0; точний результат у `qa/verification.md` |
| Android Kotlin 2.1.20, compile/lint/APK/androidTest compilation | UNRUN/BLOCKED: немає SDK Platform/Build Tools; завантаження з dl.google.com не вдалося через DNS |
| Android SQLite, instrumentation, UI, емулятор/пристрій | UNRUN/BLOCKED |
| Swift package/XCTest, Xcode build, simulator/UI | UNRUN/BLOCKED: Windows без Swift/Xcode |
| iOS project structure | Task 11: 14 app Swift-файлів рівно по одному в App group і реальній Sources phase; XML/static PASS, не компіляція |
| Remote CI | Workflow підготовлено, запусків і артефактів немає |
| Git / публікація | `feat/local-mvp`, порожній baseline `4716bb6`, джерела untracked; `.git/index.lock` permission denied, реалізацію не закомічено |
| GitHub | Feature write 403; CLI auth invalid; login antonkolosok, installations [], permission read 403. Push/PR BLOCKED |

Код охоплює локальні клієнти/послуги/профіль, редактор і SQLite, день/тиждень, перетини/resize, оплату/повтор/історію, вибір контакту та локальні нагадування на обох платформах. Детальні матриці: `qa/android.md`, `qa/ios.md`; вимоги: `REQUIREMENTS.md`; фактичні фінальні команди: `qa/verification.md`. Історія окремих задач збережена в `.superpowers/sdd/2026-11-02-local-mvp/progress.md` та `docs/reviews/`.

Профіль локальний і непідтверджений. Автоматичної зовнішньої доставки немає; handoff не означає sent/delivered. iOS має вікно 60 майбутніх сповіщень і потребує наступного foreground reconciliation; повна черга залишається у SQLite. Системні дозволи, picker, lifecycle, clock/boot, жести, доступність і адаптери месенджерів потребують нативного виконання. Реальні контакти та повідомлення не використовувалися.

Windows sandbox URI-помилка більше не відтворюється; джерела редагуються. Це не усуває SDK/Xcode/Git/GitHub блокери. Для розблокування: отримати SDK 35/35.0.0, виконати Android команди з README на пристрої/емуляторі, виконати Swift package та simulator-команди на macOS, відновити дозволений запис feature-гілки й запустити CI. Після успішних перевірок можна підготувати незлитий PR. Merge, auto-merge та реліз не дозволені й не виконані.

