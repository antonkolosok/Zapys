# iPhone QA

Стан на 2026-11-03: **усі Swift/XCTest/Xcode/simulator перевірки UNRUN/BLOCKED**. Windows-хост не має Swift/Xcode; попередній `swift test --package-path ios` завершився до компіляції, оскільки swift не знайдено. Статичний Task 11 аудит 14 app Swift-файлів підтвердив membership у group/Sources, але не синтаксис, компіляцію чи виконання. Віддалений CI не запускався.

Команди з кореня на macOS з Xcode та iPhone simulator:

```bash
swift test --package-path ios
bash scripts/test-ios.sh
```

Пакетні тести не замінюють app build/UI тести. Скрипт тестує схему Zapys; логи й xcresult — `artifacts/ios-*/`. Усі наведені нижче файли authored, жоден результат XCTest не заявлено.

У таблиці `core/` означає `ios/Tests/ZapysCoreTests/`; UI — `ios/UITests/ZapysUITests.swift`.

| Вимога | Конкретні тести | Невиконана перевірка |
| --- | --- | --- |
| Валідація, ліміти, гроші null/free, duration, template literal/DST | core/DomainTests.swift, FormValuesTests.swift, IndependentQATests.swift | Swift compile/XCTest; native form errors/focus |
| SQLite reopen, UUID, snapshots, profile, foreign keys/rollback | core/RepositoryTests.swift, IndependentQATests.swift | Real SQLite execution, injected failure і atomic handoff |
| Editor, inline client/retry, override, manual end, repeat/status/payment | core/AppointmentDraftTests.swift, IndependentEditorTests.swift; UI | XCTest та simulator editor/navigation/dirty state |
| Конфлікти, cancel/confirm без мутації | core/IndependentQATests.swift, IndependentEditorTests.swift; UI | Транзакції та native dialogs |
| День/тиждень, 23:59, resize, lanes/gradient/compact time | core/CalendarGeometryTests.swift, IndependentCalendarTests.swift; UI | Gestures/render/scroll/agenda action isolation |
| Due/expiry/stale ID/timezone/clock, selective window, share outcome | core/DevicePolicyTests.swift, IndependentDeviceTests.swift, RepositoryTests.swift | XCTest плюс реальні notification/share APIs |
| Explicit terminal cancel проти automatic cancellation restoration | core/ReminderRestorationTests.swift; UI | П'ять SQLite regressions та незалежні List actions UNRUN |
| Відмова у видаленні пов'язаного клієнта, редагування після reopen; лінійне читання черги 100/10000, без зайвих UPDATE | core/FinalRepositoryRegressionTests.swift | Два SQLite cases UNRUN; кількість декодованих рядків і тригери, не виміряна швидкодія |
| Дата, час, статус, кількість послуг, selected/today | core/CalendarAccessibilityTests.swift; UI | Два core cases та actual-view assertions authored UNRUN; VoiceOver потребує пристрою |

## Нативне ручне приймання

- CRUD/search/history/profile: duplicate-phone клієнти з окремими UUID; restart; збереження optional fields/notes, null/free, ordered snapshots і локального непідтвердженого профілю. Видалення/зміни каталогу не псують історію.
- Новий editor вимагає end; tap одразу зберігає 30-хв чернетку; фінальний слот закінчується 23:59. Перевірити п'ять ordered services, overrides/clear, manual end, inline client retry та dirty input після storage/validation failure; repeat має новий UUID і скинуті payment/status/reminder.
- День/сім колонок/сьогодні/header drill-down; повернення з editor. Resize hold/drag/tap/scroll/cancel, snapping, overlap warning/confirm/cancel, паралельні lanes. Agenda editor/end та reminder prepare/cancel активуються незалежно.
- Світла/темна тема, Dynamic Type, VoiceOver, safe area/keyboard: selected day, client/date/time/status/service count, короткі події, compact week time, вертикальні послуги, unset/mixed colours і точні gradient anchors. Screenshot review ще не виконано.
- ContactsUI phone-property picker: один вибраний номер, multi-number contact, cancel, only-empty-field fill, dirty fields та ручний ввід без broad contacts access. Використати synthetic fixtures.
- Explicit notification permission і denial, profile flag, приватний текст, failure reporting, timezone/significant clock/foreground refresh, backward clock, expiry. Перевірити 60 earliest future requests із повною SQLite queue; overdue лишаються в manual queue, missed alerts не replay. ОС може затримати сповіщення; без виконання застосунку наступне вікно не гарантоване.
- Manual preview/open з повторною перевіркою актуального ID та expiry; inactive scene/dismissal скасовує callback. Cancellation, clipboard, missing/unknown extension → retry; completion error → error; тільки завершення allowlisted messenger extension → handoff. Installed extension IDs ще треба перевірити. Не надсилати зовнішніх повідомлень; handoff не означає verified recipient/sent/delivered.

## Історія та межі доказів

Початкові 19 independent QA methods специфікували overlap symmetry/containment/adjacency, exact time bounds, five-service limit, durable snapshots, rollback, literal Ukrainian tokens з обома апострофами, незалежні Kyiv DST UTC instants та stale outbox IDs. Вони не виконувалися. Пізніші RepositoryTests додали failure injection; це тепер authored coverage, а не старий висновок про відсутність fixture і не runtime pass. Enabled lead 1...525600, disabled may retain 0. Task 11 виправив source-level List button isolation та automatic-versus-explicit reminder restoration; це також не native proof. `git diff --check` на untracked файлах не підтверджує їхній вміст. Реальні контакти/зовнішні повідомлення не використовувалися.

