# Flag Hours

An Android app for flat-rate technicians. You record what's flagged on each repair order (RO), and the app shows
flag hours worked, paid and still owed, how efficient you've been, and a pay estimate. It also checks each
paycheck against your records and points to the jobs that explain any difference.

The RO is the source of truth. The app works fully offline, and the optional network features (VIN decode,
labor-time reference) are only used when you tap them.

## Features
- **Setup wizard** with 7 steps for your pay plan (rates, pay period, when flags pay, guarantee, warranty/internal/diag rates,
  comebacks, overtime and efficiency tiers). Every step has a default and can be skipped. Settings > Pay plan uses the same
  screens and shows how last period's pay would change before you save.
- **Dashboard**: worked, paid and still-owed flag hours, clocked hours, efficiency and estimated gross, plus the exact math used.
  Has a big clock in/out button.
- **ROs and jobs**: one RO can have many lines, each with a labor type, flagged hours, an optional warranty adjustment, your split %,
  a work date and a status (Open, Closed, Paid). Lines can be linked as comebacks, and job descriptions autocomplete from past jobs.
- **Jobs list**: filter by Open, Unpaid or Paid, and by the period worked or paid. Swipe a job right to mark it paid (with Undo).
- **Time**: shifts (including ones past midnight), a fix for forgotten clock-outs, and hourly entries (training, shop time,
  diag paid by the clock).
- **Paycheck check**: enter the hours (and optional gross) from your stub. The app shows the gap and lists the jobs that could
  explain it, such as jobs not marked paid, a split mismatch, a warranty cut, or a job paid in another period. Each comes with a one-tap fix.
- **Scan RO**: camera or photo, read by on-device OCR (ML Kit, offline). The app finds the RO number, VIN, vehicle and labor lines,
  and opens them as an editable draft before saving. You can always enter the RO by hand instead.
- **VIN decode** via NHTSA vPIC (no key needed).
- **Labor-time reference** (optional) via Open Labor Project, using your own API key (stored encrypted). One lookup loads
  every job for a vehicle (a couple more the first time for a make), and job matching then happens on the phone:
  "R&R radiator" or "replace rad" both find "Radiator". Pick a match once and it's remembered. You can browse all jobs by
  category, and lines are matched automatically when the match is clear. Each line gets a badge: green means in range,
  yellow below, blue above. Errors say exactly what went wrong (no key, bad key, daily limit with reset time, vehicle
  not in the catalog with the names tried). See [docs/olp-api.md](docs/olp-api.md).
- **VIN barcode scan** on the RO screen (ML Kit, offline). A VIN fills year/make/model/engine from NHTSA when the RO is saved.
- **CSV backup**: share or save one CSV file with everything, and restore from it.

Hours are stored as integer hundredths (1.6 h = 160) and money as integer cents. Pay math never uses floating point.

## Build

Requirements: JDK 21 for Gradle (the build uses a JDK 21 toolchain, which Gradle downloads if it's missing), plus the
Android SDK with **platform 37.2** (current AndroidX libraries compile against it; the app still targets SDK 36 and runs on Android 8.0+).

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # once
./gradlew testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Tests cover the pay engine, pay periods, reconciler, RO text parser, CSV backup, API parsers (plain JVM),
the Room repository and lookup service (Robolectric), and Compose smoke tests that click through the real screens.

CI (GitHub Actions) runs the same command on every push and uploads the debug APK as the `flag-hours-debug-apk` artifact.

## Install on your phone

**From GitHub (no cable):**
1. Coming from 0.1.1 or later: just install over it, and your data stays. Coming from the 0.1.0 debug build or another test
   build: **uninstall it first**. Android refuses to update an app signed with a different key and only says "package appears to be invalid".
2. On the phone, open the latest release (signed in to GitHub) and download `flag-hours-<version>.apk`.
3. Open it, allow installs from your browser or Files app when asked, then tap *Install*.
4. Optional: check the file's SHA-256 against the one in the release notes.

**Over USB (adb):** turn on *USB debugging* (Settings > About phone > tap *Build number* 7 times, then
Developer options), plug in, and run `adb install -r flag-hours-<version>.apk`.

## Release builds

`./gradlew assembleRelease` produces a signed, R8-minified APK at `app/build/outputs/apk/release/app-release.apk`.
Signing reads `~/keystores/flag-hours.properties` (or the path in `FLAG_HOURS_SIGNING`) with `storeFile`, `storePassword`,
`keyAlias` and `keyPassword`. The keystore and properties are never committed. **Back up `~/keystores`**: without that key,
future versions can't be installed as updates and users would have to uninstall (losing data) to upgrade.
If the properties file is missing, the release build is produced unsigned. Release APKs include ARM native libraries only
(phones); debug builds keep x86 too for emulators.

## Project layout

```
app/src/main/java/com/jeremy/flaghours/
  domain/   PayCalculator, PayPeriod, PaidPeriodPolicy, Reconciler, RoTextParser, CsvExporter, Units (pure Kotlin)
  data/     db (Room), settings (DataStore pay plan, Keystore-encrypted API key), remote (vPIC, Open Labor Project)
  ui/       dashboard, jobs, ro, time, paycheck, scan, setup, settings, navigation, theme, common
```

See [PLAN.md](PLAN.md) for the full spec and [PROGRESS.md](PROGRESS.md) for status.

Vehicle decoding: NHTSA vPIC. Labor reference data: Open Labor Project.
