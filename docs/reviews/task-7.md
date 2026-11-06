# Task 7 — незалежний огляд iPhone editor

Дата: 2026-11-03. Перевірено task-7-brief/report/qa, task-7-ios.diff та відповідні розділи REQUIREMENTS/DESIGN. Огляд обмежений Task 7; production-файли не змінювалися.

## Висновок щодо специфікації

Потрібні виправлення перед прийняттям. Основні сценарії реалізовані, але конфігурація target виключає новий екран історії, а вимога фокусування першого некоректного поля не виконана для дати/часу.

## Сильні сторони

- AppointmentDraft зберігає сирі перевизначення, nullable duration/price, порядок та ліміт п’ять; підготовка повертає копію і не змінює видимий статус.
- Усі три шляхи збереження використовують спільний pipeline; inline client має стабільний UUID до успіху, після успіху стає вибраним клієнтом і отримує новий наступний UUID.
- Typed overlap доходить до UI; allowOverlap використовується тільки після явного підтвердження. Скасування залишає незбережені дані та попередній запис.
- History фільтрується за UUID, використовує один captured now та точну межу end <= now. Repeat скидає UUID/status/payment/manual-end та вимагає підтвердження нових date/start.
- Decimal підсумовує п’ять Int64 цін без Int64 overflow. AppModel залишає доступ до SQLite на послідовній черзі та повертає результати на main.

## Знахідки за пріоритетом

1. **P1 — HistoryView.swift не включено до compilation sources app target.** `ios/Zapys.xcodeproj/project.pbxproj:23-29`: оголошено file reference 073 та build file D6, але 073 відсутній у App group, а D6 — у PBXSourcesBuildPhase 014. Саме відсутність D6 у sources блокує компіляцію: `ios/App/ZapysApp.swift:23` використовує AppointmentRow, а ClientsView — HistoryView, обидва оголошені тільки у виключеному файлі. Додати файл до групи та D6 до sources; після цього перевірити app build на macOS. Звіт Task 7 помилково стверджує, що обидва файли включені в PBX membership.

2. **P2 — Помилки date/start/end не фокусують відповідне поле.** `ios/App/AppointmentEditor.swift:35-39,97-104`: DatePicker та кнопки required date/start/end не мають focus binding. Перед `prepared` останній focus встановлено на note. Наприклад, вибрати клієнта, залишити новому запису відсутній end, натиснути save: prepared відхиляє запис, а focus залишається на note. Аналогічно end <= start та відсутні repeat date/start не спрямовують до першої помилки. Потрібен явний validation-field результат/відповідність контролам з фокусом або доступним перенесенням уваги й прокруткою до першого некоректного date/time контролу; пріоритет перевірки має відповідати порядку форми.

## Якість та межі перевірки

Архітектурний поділ draft/repository/UI доречний; незалежні тести охоплюють реальні SQLite reopen/conflict сценарії, а не тільки допоміжні обчислення. Висновок щодо якості: **потрібні виправлення**, насамперед target membership; наведені тести не доводять працездатність SwiftUI навігації чи фокусу.

Swift та xcodebuild відсутні на Windows. XCTest (включно з новими AppointmentDraftTests та IndependentEditorTests) повністю **UNRUN**; package/app compilation, simulator/UI tests, реальні native picker interactions, VoiceOver, клавіатурний focus, light/dark та Dynamic Type **не перевірені**. Статичний огляд і whitespace checks не є compile pass. Перед прийняттям потрібні macOS package tests, simulator build та перевірка зазначених UI сценаріїв. Calendar geometry та device/reminder adapters не розглядалися.
## Повторний огляд — fix round 1

Перевірено тільки task-7-fix1.diff, виправлений звіт і task-7-membership-check.txt, без розширення попереднього scope.

- **P1 addressed на рівні source:** project.pbxproj:25,29 тепер містить HistoryView reference 073 в App group та D6 у Sources phase 014 app target. Таблиця membership містить усі вісім App Swift-файлів рівно по одному разу; попереднє неточне твердження у звіті виправлено. Знайдена причина виключення HistoryView/AppointmentRow усунена.
- **P2 addressed на рівні source:** AppointmentDraft.swift:54-68 явно визначає перший timeValidationIssue у порядку date/start/end. AppointmentEditor.swift:108-109 перевіряє його до client/text validation; контролам призначено відповідні id/focus/accessibilityFocus, а onChange validationAttempt прокручує й повторно спрямовує увагу навіть при повторній тій самій помилці. Новий regression перевіряє field identity та порядок; він UNRUN. Реальну поведінку native control focus/VoiceOver слід підтвердити на simulator/device.

Нових конкретних дефектів у зміненому фрагменті не знайдено. Поточний spec verdict: **попередні findings виправлені за статичним оглядом; native acceptance залишається BLOCKED**. Quality verdict: **немає відкритих source findings у цьому огляді; готово до macOS verification**. Це не compile/test pass: усі XCTest, app/package compilation, simulator, VoiceOver та фактичний focus залишаються UNRUN/BLOCKED на Windows. Початковий висновок вище збережено як історію першого раунду; цей додаток є актуальним висновком.