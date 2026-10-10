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
- **Labor-time reference** (optional) via Open Labor Project, using your own API key (stored encrypted). Results are cached
  and the free tier's 10 lookups a day is respected. Each line gets a badge: green means in range, yellow below, blue above.
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

## Sideload onto your phone

**Over USB (adb):**
1. On the phone, open Settings > About phone and tap *Build number* 7 times. Then go to Settings > System > Developer options and turn on *USB debugging*.
2. Plug in the phone and accept the "Allow USB debugging" prompt.
3. Run `adb install -r app/build/outputs/apk/debug/app-debug.apk`

**Without a cable:**
1. Download `app-debug.apk` on the phone, either from the latest GitHub release or from the `flag-hours-debug-apk` artifact of a CI run.
2. Open it. Android asks you to allow installs from that app (your browser or Files). Allow it, then tap *Install*.

Debug builds from different machines are signed with different debug keys. If an install fails with a signature
error, uninstall the old copy first (this deletes its data, so share a CSV backup first).

## Project layout

```
app/src/main/java/com/jeremy/flaghours/
  domain/   PayCalculator, PayPeriod, PaidPeriodPolicy, Reconciler, RoTextParser, CsvExporter, Units (pure Kotlin)
  data/     db (Room), settings (DataStore pay plan, Keystore-encrypted API key), remote (vPIC, Open Labor Project)
  ui/       dashboard, jobs, ro, time, paycheck, scan, setup, settings, navigation, theme, common
```

See [PLAN.md](PLAN.md) for the full spec and [PROGRESS.md](PROGRESS.md) for status.

Vehicle decoding: NHTSA vPIC. Labor reference data: Open Labor Project.
