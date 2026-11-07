# Zapys native interface

Implementation guidance, 2026-11-02. The approved HTML prototypes and `REQUIREMENTS.md` define scope. This document supplies practical native defaults; it does not add features. Keep the quiet warm-green calendar identity and Ukrainian copy. Native system controls and typography are appropriate; do not embed the HTML or reproduce its outer phone frame. The user already approved the mockup and authorized implementation without further design questions.

Reference scope: both HTML files illustrate the day timeline, date strip and editor styling. Their date strip is not the required seven-column week timeline, and their comma-joined event service text is superseded by the ordered vertical labels below. Week geometry and label-aligned gradients follow the approved requirements; do not copy these prototype limitations into the native apps. The dark palette below is a native implementation default, not a second approved HTML theme.

## Tokens

Light-theme reference values come from `zapys-calendar-preview.html`; dark-theme values are native defaults. Units below mean dp on Android and points on iOS.

| Role | Light | Dark |
| --- | --- | --- |
| Background / fields | `#F5F6F2` | `#141B17` |
| Surface | `#FFFEFA` | `#1C2520` |
| Primary text | `#233A2C` | `#E8F1E9` |
| Secondary text | `#6A776D` | `#ACB9AE` |
| Divider / field border | `#E4E9E0` | `#344039` |
| Primary action / selection | `#2F6047` | `#B3DBC0` |
| Text on primary | `#FFFEFA` | `#172D20` |
| Standard appointment fill | `#E8F1E5` | `#2A3D30` |
| Draft fill | `#FBEFCF` | `#443A22` |
| Draft text | `#785A1C` | `#F0D998` |
| Error | `#AC3B36` | `#FFAAA2` |

Use 20-unit page margins, 12-unit row gaps, 16–20-unit section gaps; compact calendar margins may use 12. Field and small button radius 10–11, event radius 10, service cards 12. Avoid decorative shadows inside screens. Use platform body styles near 16 for form text, 14–16 for content, 12 for secondary metadata and 22–23 for screen titles. Calendar time labels may use 11–12 with tabular numerals. Respect Dynamic Type / font scale rather than hard-coding text height.

## Navigation and screens

Four persistent destinations: **Календар**, **Клієнти**, **Послуги**, **Налаштування**. Use platform bottom navigation / tab bar. Appointment, client and service editing opens a pushed full-screen form with back navigation. The calendar remains the initial screen, requiring no account. Preserve selected date and day/week mode when returning from details.

Android bottom navigation uses four equal-width targets with baseline alignment disabled. Full destination labels wrap at the user's font scale; the row takes the tallest label's measured height and all four buttons fill that common height, with centered text and a minimum 48dp touch height. Selected destinations use primary fill and text-on-primary as well as selected accessibility semantics. Preserve the root's system-bar padding so every target stays above bottom insets and inside landscape side insets.

- **Calendar:** small zapys wordmark; month/year and previous-week, **Сьогодні**, next-week controls; **День / Тиждень** segmented switch; Monday-first seven-day strip. Selected date uses solid primary fill and text-on-primary. Differentiate today using a small outline/dot when it is not selected. Day heading includes weekday and date. A prominent **+ Новий запис** action stays reachable above the safe area without covering events.
- **Appointment detail/editor:** title **Новий запис**, **Чернетка**, or **Запис** as appropriate. Date/start/end first; client selection and inline new-client entry next; ordered services next; appointment note, status and payment next; reminder summary and manual action last. Primary **Зберегти запис**; secondary **Зберегти чернетку** where relevant. Existing records expose **Повторити запис** and explicit delete/cancel actions. Repeat opens a new form and asks for the new date/time, without retaining payment, status or reminder outcome.
- **Clients:** name/phone search, list rows with name and phone, add action and optional selective **Імпортувати з контактів**. Client card has contact fields, note, color, reminder preferences, then **Майбутні записи** and **Минулі записи**, including services and notes. Empty sections say **Записів ще немає**. Keep editing separate from tapping a history appointment.
- **Services:** searchable or plain scrollable catalog, add/edit form. Show name; known duration and price underneath. Blank optional values read **Не вказано**; zero price reads **Безкоштовно**. Catalog remains unrestricted by the five-per-appointment limit.
- **Settings:** **Локальний профіль**, optional phone/email, notification permission information, and reminder queue/state list. Explain storage once: **Дані зберігаються на цьому пристрої. Хмарну синхронізацію ще не підключено.** Local profile caption: **Не підтверджено. Це локальний профіль, а не вхід в обліковий запис.** No password, OTP, verified badge or cloud-backup claim.

## Calendar geometry and interaction

Default scale is 72 units/hour (1.2 units/minute), shared by day and week. Use a 42-unit time-label gutter, full-hour solid rules and half-hour faint/dotted rules. Timeline covers 00:00–23:59 and initially scrolls near 09:00; do not hide early/late appointments. Week uses seven equal-width day columns following the gutter, with subtle vertical dividers and a sticky day header. Header tap selects that date and opens day mode. Week navigation always moves seven days; **Сьогодні** selects the actual current date.

Android date selection uses a separate Monday-first row spanning the full calendar content width inside its normal 12dp outer padding. All seven equal cells fit, with Monday at the content edge and no time-label spacer. Day mode uses this selector alone. Week mode also shows compact date headings immediately above the scrolling timeline; only these headings retain the shared 42dp gutter and align with the seven actual grid columns. Selected/today styling and tap-to-day behavior belong to the full-width selector.

Event top is `(startMinute - timelineStartMinute) * scale`; height is duration times scale with a small visual inset of at most 3 units. Never inflate visible event height to fit text and thereby misrepresent end time. A 30-minute draft is about 36 units tall. Day events show client at top, then services individually below in selection order, then time/status when space permits. Week events show name/time and as many vertical service labels as fit. Ellipsis or **+N послуги** reveals hidden services; the full detail always lists all five. Long labels wrap in detail, but may truncate in the grid. Do not place service names in a comma-separated horizontal line.

Short events retain true duration and use a compact title; selection opens details with the complete service list. Supply an accessible list/agenda representation of visible appointments for screen-reader navigation and large text, with the same edit actions. Touch regions can extend invisibly when they do not steal another event's tap; dense events remain individually reachable via that list.

Overlapping events occupy parallel lanes inside their day. Allocate earliest available lane by start/end; use the group's maximum simultaneous lane count for equal widths. Adjacent end/start times share a lane. Never draw one active appointment on top of another. Cancelled items are excluded from conflicts; if shown, use a muted cancelled label and do not obstruct active blocks.

Tapping an empty date/time immediately persists a blank **Чернетка** with a 30-minute duration and announces the saved time. Start snaps to 15 minutes. Latest start for this route is 23:29, ending 23:59; clearly show that actual time rather than 24:00. Do not silently wrap into tomorrow. **Новий запис** instead opens an unsaved form with empty required end time.

Selected events show a lower-edge resize grip and current time range. Dragging previews end time with 15-minute snapping; clamp to after start and at most 23:59. Also expose an accessible **Змінити час закінчення** action and ordinary end-time field. Cancelled gesture restores the original. Before committing an overlap, show **Цей час перетинається з іншим записом** with conflicting client/time and **Зберегти попри перетин / Скасувати**. Cancel preserves the previous persisted interval. Apply the same confirmation to form saves and resize; do not persist the conflicting preview before confirmation.

## Color and vertical gradients

Review example: a colored client, an unset first service and a colored second service produce exactly two stops, anchored to the client and second-service labels. Do not move the second stop onto the first-service row or add a stop for the unset service. Test this in both day and week layouts.

Client and service color controls show a named swatch, beginning at **Без кольору**, and support clearing. Opening the control offers the four existing presets (**Зелений**, **Блакитний**, **Золотистий**, **Бузковий**) and **Власний колір** with a hue slider, saturation/value touch plane and named saturation/value sliders for accessible input. A live swatch previews the pending choice; **Підтвердити** commits once and **Скасувати** leaves the form unchanged. New custom choices are opaque RGB. Existing stored ARGB values remain unchanged until a new choice is confirmed. Both the committed unsaved form color and any open dialog's pending color, null choice and HSV values survive activity recreation without marking a clean form dirty. Selected presets have checked and selected semantics; the visible value uses a color name rather than a HEX code. Any selected color must remain readable in calendar gradients. Color does not encode status alone.

Construct the ordered label rows as client, service 1…5. For every colored label, calculate its vertical center in the event's own content coordinate space, then normalize by event height to a gradient stop. Ignore unset colors entirely; do not synthesize a default stop for them. The gradient runs top-to-bottom, holds the first color above its stop and the last below its stop. One contributing color produces solid fill; none uses standard event fill (draft uses draft fill). Thus the gradient follows the corresponding vertical labels rather than using arbitrary evenly spaced stops. Recalculate after font/layout changes. In the compact grid, use the same logical row order; clipped labels remain clipped and never move their stops into a misleading visible order. Full detail offers enough room for all rows and exact anchors.

Readable labels take priority over visible color intensity. Give each text row a nearly opaque theme-surface backing (for example 94% surface) and theme ink; keep gradient exposed in surrounding padding and a narrow side rail. This avoids choosing a single foreground against a multicolor background. Validate contrast against the composited row background; use an opaque surface if needed. Selected outline is primary, at least 2 units. Keep a textual **Чернетка**, **Скасовано**, or other status label even when a color exists.

## Forms and data clarity

Use visible labels, not placeholder-only fields. Date and time use native pickers with 24-hour display; phone uses telephone keyboard, price uses decimal keyboard, durations positive whole minutes. Required client name/phone and required appointment date/start/end show `*`. Optional surname, patronymic, note and color can sit in a **Додатково** disclosure. Notes are text-only with a 2000-character counter near the limit.

In the Android appointment editor, place all inline new-client labels, guidance and fields in one container. Show it only for **Без клієнта**; derive visibility from the selected client ID initially and after recreation. Keep inactive raw text so toggling back restores it. Save actions use the selected existing client even when hidden inline text is nonblank or invalid. Only the active inline path validates and creates a client; a client already saved before an appointment failure becomes the selected identity for retry.

Services are vertical cards numbered by selection order, each with name, optional duration/price/note and remove button. Show **0 / 5** through **5 / 5**. At five, disable adding and explain **У записі вже 5 послуг**. Keep removal available. Empty service selection is valid. Duration suggestion is available only when every selected duration is known. Changing end manually preserves it; use an explicit **За тривалістю послуг** action to reapply the suggestion. Unknown prices remain distinct from free: a partial total reads **600 ₴ + невказана ціна**, rather than treating null as zero. Mark payment using **Не оплачено / Оплачено**, with optional **Фактично отримано, ₴**.

Status labels: **Заплановано**, **Завершено**, **Скасовано**, **Не з'явився / не з'явилася**; draft stays separate. Destructive actions name the affected record and ask confirmation. Back from a changed form offers save draft where valid, discard, or continue editing; never discard silently. Preserve input after validation and database failures.

## Reminders and graceful errors

Client preferences contain enabled switch, messenger, lead time and editable template. Android forms omit the recipient field and explain **Одержувача ви обираєте в месенджері під час ручного надсилання.** Preserve stored legacy recipient values without displaying them in reminder rows or preview titles. Blank recipient values do not block enabled reminders or manual sharing; retain time, state and lifecycle checks. Channel names remain Viber, WhatsApp, Telegram, Instagram. Explain **До 1 нагадування на запис** and **Автоматичне надсилання не підключено. Ви можете підготувати повідомлення та відкрити месенджер вручну.** Template variables insert appointment text literally. Show a message preview before opening the system composer.

| Stored state | Visible wording |
| --- | --- |
| Scheduled | **Заплановано — нагадаємо вам надіслати** |
| Needs manual action | **Потрібно надіслати вручну** |
| Handed to messenger | **Передано до месенджера. Надсилання не підтверджено.** |
| Error | **Не вдалося відкрити повідомлення** with retry/copy action |
| Cancelled | **Нагадування скасовано** |

Primary action is **Підготувати повідомлення**, then **Відкрити месенджер**. Never say **Надіслано** or **Доставлено** after opening a share sheet. Past/cancelled records cannot prepare a new reminder. Reschedule invalidates the previous reminder text/time. Default local notification is privacy-conscious: **Час підготувати нагадування в Zapys**, without client/phone/note. Denied notifications show **Сповіщення вимкнено. Нагадування доступні в застосунку** and a settings link; manual queue remains usable. Denied/unavailable contacts show **Не вдалося відкрити контакти. Введіть ім'я та телефон вручну** without blocking entry.

Field errors appear inline and focus the first failing field: **Вкажіть ім'я**, **Перевірте номер телефону**, **Завершення має бути пізніше за початок**, **Ціна не може бути від'ємною**. Storage failure stays on the form: **Не вдалося зберегти запис. Спробуйте ще раз**. Do not show a success toast until the transaction succeeds. Technical ceilings use **Досягнуто технічного ліміту локальної версії**, never a fabricated paid upgrade.

## Mobile accessibility and review

Use minimum 48-unit Android / 44-unit iOS interactive targets, safe areas, accessible names for previous/next, add/remove, color and resize controls, and visible keyboard focus. Screen readers announce appointment client, date, start/end, draft/status and service count; color is supplemental. Provide text errors and selected state in semantics. Large text may stack start/end fields and move metadata below titles. Allow scroll to focused fields above the keyboard. Haptics are optional and never the only acknowledgement.

Review light/dark and large text on both platforms: empty calendar; short/long five-service events; mixed unset and very bright/dark colors; overlapping lanes; 23:59 boundary; keyboard form validation; denied permissions; unverified profile and manual reminder labels. Screenshot review is visual evidence only; runtime persistence, conflicts and reminder validity need functional tests.
