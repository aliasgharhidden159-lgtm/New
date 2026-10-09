# PackWork — Native Android App (Kotlin + Jetpack Compose)

A fully native rewrite of the PackWork Inventory & Packing Tracker. No WebView, no
bundled website, no internet permission. Same features and business rules as the web version:

- **Home** overview: Home/Office godown totals, earned / paid / still owed, open work, recent tickets, low-stock alert
- **Stock**: items with SKU, unit, active/paused, piece rate; receive stock; dated stock-entry history
- **Jobs**: Office → Home transfer tickets (rate locked at issue), Home → Office reconciliation (packed / unused / damaged), payments
- **Pay**: piece-rate earnings per ticket, balance owed, add payment
- **Reports**: per-item, date-range ledger with 12 totals + CSV export
- **Settings**: currency, JSON backup / restore, tickets CSV export

Data is stored in a private JSON file (`filesDir/packwork-data.json`), written atomically on every change.

## Build
1. Open this folder in **Android Studio** (Koala or newer) and let Gradle sync.
2. Run ▶ on a device/emulator (minSdk 24), or Build → Build APK(s).
   Release: Build → Generate Signed Bundle / APK.

## Moving your existing data from the old WebView app
In the OLD app: Settings → Download JSON backup.
In this app: Settings → Restore → pick that file. The backup format is identical.
(The old app's browser storage cannot be read by the native app directly, so do this before uninstalling it.
Same package name `com.packwork.tracker`, so installing this over it keeps the same app slot.)

## Code map
- `data/Models.kt` – models + JSON (compatible with web backups)
- `data/Logic.kt` – ticket math, formatting, report builder, CSV
- `data/Repository.kt` – file persistence
- `ui/PackWorkViewModel.kt` – all rules: transfers, reconciliation, payments, guarded deletes
- `ui/Screens1-3.kt`, `ui/Dialogs.kt`, `ui/Components.kt`, `ui/App.kt` – Compose UI
