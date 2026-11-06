# Zapys

Нативний локальний календар для самозайнятого фахівця: Android (Kotlin) та iPhone (SwiftUI). Інтерфейс українською, дані зберігаються у SQLite на пристрої. Хмарна синхронізація та підтвердження облікового запису відкладені.

У вихідному коді реалізовано клієнтів і послуги, записи з окремими знімками послуг, денний і тижневий календар, чернетки, зміну тривалості з підтвердженням перетинів, історію, статуси й оплату, вибір контакту та локальну чергу нагадувань. Це стан реалізації коду, а не підтвердження працездатності застосунків: нативні збірки та запуск ще не виконані.

Нагадування готує текст для ручного відкриття месенджера. Відкриття або завершення роботи розширення не означає надсилання чи доставку. Локальний профіль не підтверджує номер телефону/email. Реальні контакти й зовнішні повідомлення під час перевірок не використовувалися.

## Структура

- `android/` — застосунок, доменна логіка, SQLite та Android-тести; контракт у `android/API.md`.
- `ios/` — пакет ZapysCore, SwiftUI-застосунок, Xcode-проєкт і тести; контракт у `ios/API.md`.
- `docs/REQUIREMENTS.md`, `docs/DESIGN.md` — вимоги й дизайн.
- `docs/STATUS.md`, `docs/qa/`, `docs/qa/verification.md` — стан, матриці перевірок та фактичні докази.
- Два HTML-файли в корені — збережені візуальні прототипи.

## Android

Потрібні JDK 17, Android SDK Platform 35, Build Tools 35.0.0 та доступ до залежностей. Мінімальна версія пристрою — Android 8.0/API 26. Використовуйте Gradle wrapper із репозиторію; Kotlin застосунку закріплено на 2.1.20.

Усі команди нижче запускаються з кореня репозиторію. На Windows підготовка та окрема офлайн-перевірка домену:

```powershell
./scripts/bootstrap.ps1
. ./scripts/env.ps1
./scripts/test-domain.ps1
```

Для встановлення SDK після відновлення доступу до серверів Android можна виконати `./scripts/bootstrap.ps1 -InstallSdk -AcceptAndroidLicenses` (останній параметр означає прийняття ліцензій Android).

```powershell
./android/gradlew.bat -p android --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./android/gradlew.bat -p android --no-daemon connectedDebugAndroidTest
```

Друга команда потребує підключеного пристрою або запущеного емулятора. На Linux/macOS замініть `./android/gradlew.bat` на `./android/gradlew`. APK очікується в `android/app/build/outputs/apk/debug/`, звіти — у `android/app/build/reports/`.

Для запуску всіх перевірок збірки у звичайному Windows PowerShell без ручного налаштування змінних:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\anton\Desktop\Zapys\scripts\build-android.ps1"
```

`Bypass` застосовується тільки до цього процесу. Скрипт перевіряє SDK, запускає unit/lint та складає обидва debug APK; пристрій для цього не потрібен. Instrumentation на пристрої виконується окремою командою вище.

Фінальний повний офлайн-прогін після виправлень: **84 тести PASS**, Kotlin 2.0.20/JUnit 4.13.2 із локального Gradle. Він не компілює Android API, SQLite, UI чи Kotlin 2.1.20. Точний результат наведено в `docs/qa/verification.md`.

## iPhone

Потрібні macOS, Xcode із підтримкою iOS 17+ та доступний iPhone simulator. З кореня репозиторію:

```bash
swift test --package-path ios
bash scripts/test-ios.sh
```

Скрипт вибирає доступний iPhone simulator, запускає схему `Zapys` із `ios/Zapys.xcodeproj`, складає застосунок і виконує UI-тести без підписування. Логи та `tests.xcresult` зберігаються в `artifacts/ios-*/`. Для ручного запуску відкрийте проєкт в Xcode та виберіть схему Zapys. Усі Swift/XCTest/Xcode/simulator-перевірки досі **UNRUN** на поточному Windows-хості.

## Обмеження та наступні перевірки

Користувач обрав Appetize для перегляду застосунку в браузері. CI підготовлено для артефакту `zapys-ios-appetize` з ARM64 simulator-збіркою `Zapys.app.zip`; Android використовує звичайний APK. Формат файлів і кроки завантаження описані в [APPETIZE.md](docs/APPETIZE.md). Збірку iOS та запуск в Appetize ще не виконано.

Активне нагадування має випередження 1–525600 хвилин. iOS планує до 60 найближчих майбутніх локальних сповіщень, зберігаючи повну чергу в SQLite; для наступного вікна потрібне узгодження при роботі застосунку. Пропущені сповіщення не відтворюються автоматично. Android використовує неточні сигнали; ОС може їх затримати. Відмова в дозволі сповіщень залишає ручну чергу доступною. Невідоме iOS-розширення месенджера залишає можливість повтору, без підтвердження доставки.

Нативні збірки, SQLite на пристроях, жести, доступність, світла/темна тема, системні дозволи й реальні адаптери месенджерів ще потребують виконання. Детальні сценарії: `docs/qa/android.md` та `docs/qa/ios.md`.

Клієнта з пов'язаними записами видалити не можна: це зберігає історію та можливість редагувати записи. Окремої функції архівування в цій версії немає.

CI описано в `.github/workflows/ci.yml`: Android unit/lint/APK та компіляція/запуск instrumentation, Swift package і iPhone simulator. Очікувані артефакти: `android-reports`, `zapys-debug-apk`, `swift-test-log`, `ios-simulator-results` та `android-device-results`. Наявність workflow не означає успішного запуску CI.

Стан на 6 листопада: після виправлення `Theme.column()` і підключення platform test libraries Android-код та instrumentation компілюються з Kotlin2.1.20/AGP8.9.2. Обидва debug APK зібрані, exit0; основний файл — `android/app/build/outputs/apk/debug/app-debug.apk`. Повні unit/lint ще заблоковані завантаженням JUnit4.13.2/lint-gradle31.9.2 у цьому середовищі. Для їх перевірки використайте `scripts/build-android.ps1` у звичайному PowerShell. Запуск на пристрої та Appetize ще не виконано. Xcode відсутній; Git/GitHub блокери публікації описані в `docs/STATUS.md`. Для iPhone потрібні macOS/Xcode (локально або в CI) та публікація feature-гілки. Коміт реалізації, push, PR, CI, merge та реліз не виконані; нативне приймання залишається невиконаним.


