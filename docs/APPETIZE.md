# Запуск Zapys в Appetize

Обраний користувачем варіант: нативний застосунок у браузері через Appetize. Підготовка архіву додана до CI; успішної iOS-збірки, завантаження в Appetize чи посилання на запущений застосунок поки немає.

## iPhone

Appetize приймає архів `.zip`/`.tar.gz` із `.app` для iOS Simulator; `.ipa` для фізичного iPhone не підходить. Сервіс рекомендує ARM-збірку. Джерело: [вимоги Appetize для iOS](https://docs.appetize.io/platform/app-management/uploading-apps/ios).

1. Завантажити код і workflow в GitHub та виконати `Native checks`. У job `ios-app` спочатку проходять simulator/UI тести, потім окремо складається unsigned ARM64 Release для iOS Simulator.
2. Завантажити артефакт **zapys-ios-appetize** та розпакувати зовнішній архів GitHub. Усередині знайти **Zapys.app.zip**.
3. У своєму обліковому записі відкрити [Appetize Upload](https://appetize.io/upload) й завантажити саме **Zapys.app.zip**. Обрати доступний iPhone з iOS 17 або новішою.
4. Відкрити створений застосунок у браузері й перевірити календар, клієнтів та редактор. Реальна підтримка системних інтеграцій потребує окремої перевірки.

Команда для Mac з Xcode, з кореня проєкту:

```bash
bash scripts/build-appetize-ios.sh
```

Результат після успішного виконання: `artifacts/appetize-ios-*/Zapys.app.zip`. Скрипт перевіряє simulator-платформу та ARM64 executable до пакування. Сам скрипт збірки не запускає тести; CI виконує їх попереднім кроком. Збірка й пакування не завантажують файл у сторонній сервіс, токени Appetize в репозиторії не потрібні.

## Android

Appetize також приймає звичайний APK: [вимоги для Android](https://docs.appetize.io/platform/app-management/uploading-apps/android). 6 листопада локально успішно зібрано `android/app/build/outputs/apk/debug/app-debug.apk`; це файл застосунку для завантаження. Повні unit/lint та запуск на пристрої ще не пройдені; завантаження в Appetize не виконувалося. У майбутньому CI він очікується в артефакті **zapys-debug-apk**: перед завантаженням в Appetize розпакувати зовнішній архів GitHub.

На цьому Windows-комп'ютері SDK35/Build Tools уже встановлені. Для повної збірки поза мережевими обмеженнями ChatGPT:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\anton\Desktop\Zapys\scripts\build-android.ps1"
```

Поточні результати збірок і блокери — у [STATUS.md](STATUS.md). Успішне пакування не підтверджує запуск у Appetize; після завантаження потрібна фактична перевірка.
