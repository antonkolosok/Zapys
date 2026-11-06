# Android QA

Оновлення 6 листопада: після виправлення `Theme.column()` та підключення platform test libraries код застосунку й instrumentation скомпілювався з Kotlin2.1.20/AGP8.9.2. `--offline :app:assembleDebug :app:assembleDebugAndroidTest` завершилася exit0; обидва APK зібрані. Повні Gradle unit/lint заблоковані некешованими JUnit4.13.2/lint-gradle31.9.2 та обмеженням мережі; повторний запуск — через `scripts/build-android.ps1` у звичайному PowerShell. Логи: `artifacts/android-theme-fix-2026-11-06/`. Запуск на пристрої ще UNRUN. Нижче збережено історичні офлайн-результати від 3 листопада.

Стан на 2026-11-03. Фінальний повний офлайн-прогін після виправлень: **84 JUnit PASS**, exit 0 (Kotlin 2.0.20/JUnit 4.13.2). Це лише domain-код; закріплений Kotlin 2.1.20, Android API, SQLite й UI не компілювалися. Точний результат наведено у `docs/qa/verification.md`.

Команди з кореня репозиторію:

```powershell
./scripts/test-domain.ps1
./android/gradlew.bat -p android --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./android/gradlew.bat -p android --no-daemon connectedDebugAndroidTest
```

На 6 листопада SDK Platform35/Build Tools35.0.0 доступні й APK зібрані. Повна друга команда потребує завантаження залежностей unit/lint; остання — пристрою/емулятора. Компіляція тестового файла не означає, що тест уже виконаний.

У таблиці `unit/` означає `android/app/src/test/java/ua/zapys/domain/`, `device/` — `android/app/src/androidTest/java/ua/zapys/`.

| Вимога | Конкретні тести | Межа доказу / залишок |
| --- | --- | --- |
| Валідація клієнтів/послуг, ліміти, null/free, гроші | unit/DomainRulesTest.kt, IndependentRulesTest.kt, FormRulesTest.kt | Offline PASS; поля/фокус/клавіатура UI UNRUN |
| UUID, спільний телефон, каталог, профіль, restart | device/ClientCrudTest.kt, PersistenceTest.kt | Реальний SQLite/reopen/foreign keys/rollback UNRUN |
| Чернетки, 5 впорядкованих snapshots, override, inline retry, статус/оплата/повтор | unit/AppointmentEditorTest.kt, AppointmentSaveSessionTest.kt, IndependentEditorTest.kt; device/AppointmentEditorPersistenceTest.kt | Domain PASS; SQLite й native editor/rotation UNRUN |
| Перетини, сусідні інтервали, відмова без запису | unit/DomainRulesTest.kt, IndependentRulesTest.kt, IndependentEditorTest.kt; device/PersistenceTest.kt, AppointmentEditorPersistenceTest.kt | Domain PASS; реальна транзакція/діалоги UNRUN |
| День/7 колонок, 23:59, 15-хв resize, lanes, градієнти | unit/CalendarGeometryTest.kt, IndependentCalendarTest.kt, CalendarPresentationTest.kt; device/CalendarViewInteractionTest.kt | Геометрія PASS; жести/малювання/доступність UNRUN |
| Шаблони, DST, due/expiry, stale ID, 1 reminder | unit/ReminderRegressionTest.kt, IndependentRulesTest.kt, DeviceRulesTest.kt, IndependentDeviceTest.kt, DeviceClockTest.kt | Domain PASS; час ОС/SQLite UNRUN |
| Clock rollback, timezone terminal state, boot, persisted queue | device/DeviceClockPersistenceTest.kt, DevicePersistenceTest.kt, PersistenceTest.kt | Авторські й незалежні SQLite fixtures UNRUN |
| Selective picker, targeted share, локальний profile flag, foreground callback | device/DeviceIntentTest.kt, ManualOpenLifecycleTest.kt | Джерела authored; compilation/lifecycle/installed app UNRUN |

## Нативне приймання, яке ще треба виконати

- CRUD/search/history: створити два різні UUID з одним телефоном; змінити один, перезапустити; перевірити незалежність і збереження опційних полів. Видалення використаного клієнта не пошкоджує історію; редагування каталогу не змінює snapshots.
- Обидва маршрути створення: New з порожнім обов'язковим end і tap із негайно збереженою 30-хв чернеткою; кінець дня 23:59. Перевірити до 5 вертикальних послуг, очищення duration/price, unknown проти free, manual end та збереження dirty input після помилки/rotation.
- День/тиждень/сьогодні/повернення з editor; перетини у паралельних lanes; resize tap/drag/cancel, 15-хв snapping, підтвердження й відмова без зміни оригіналу. Короткі події, compact week time/status та всі послуги в agenda.
- Статус/оплата/повтор: новий UUID, нові дата/час, скинуті payment/status/reminder; минула й майбутня історія з точною межею end == now.
- Світла/темна тема, великий шрифт, TalkBack: дата/час/клієнт/статус/кількість послуг, selected day, незалежні дії agenda, контраст і градієнт при unset/bright/dark кольорах. Візуальних screenshot approvals немає.
- Picker: тільки вибраний контакт, cancel/unavailable provider, кілька номерів і відновлення після recreation; ручний ввід залишається доступним. Не використовувати реальні контакти.
- Сповіщення: explicit permission/denial/channel/profile off, приватний текст, inexact alarm/Doze, expiry, boot, timezone і clock rollback, перезапуск та foreground без втрати dirty form. Ручна черга працює без дозволу.
- Preview/open revalidation: stale/expired/cancelled rows не відкривають composer; pause скасовує відкладений callback. Відсутній messenger → error/retry/copy. Перевіряти тільки synthetic текст і відкриття composer, без зовнішнього надсилання; handoff ніколи не є delivered.

## Історичні докази

Task 2 QA зафіксував 31 offline PASS (21 independent + 10 developer), згодом task-wide baseline став 35. Це історія, а не поточний повний набір. Task 4 додав два ClientCrud SQLite cases, task 6/8/10 — editor/calendar/device fixtures. Усі нативні fixtures залишаються UNRUN. Стара згадка нульового enabled lead замінена узгодженою вимогою 1..525600 хвилин; disabled preferences можуть зберігати 0. SQLite atomicity не доведена офлайн-тестами. Жодних автоматичних повідомлень не надсилали.

