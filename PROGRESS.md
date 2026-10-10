# Progress

Resume point for any session. Plan: [PLAN.md](PLAN.md). Gate: `./gradlew testDebugUnitTest assembleDebug`.

## Done
- Scaffold: AGP 9.4.1, Gradle 9.8.0 wrapper, Kotlin 2.4.20 (AGP built-in), KSP 2.3.12, Compose BOM 2026.09.00, JDK 21 toolchain.
  - compileSdk is **37.2** (current AndroidX/Compose/OkHttp require it); targetSdk stays 36, minSdk 26.
- M1 pay engine (pure Kotlin, `domain/`): Units, Models, PayPlan, PayPeriod, PaidPeriodPolicy, PayCalculator, Reconciler + tests.

- M2 data layer: Room entities/DAOs/converters (schema exported to app/schemas), PayPlanStore (DataStore + kotlinx.serialization),
  SecretStore (Android Keystore AES-GCM), FlagHoursRepository; Robolectric repository tests.

## In progress
- M3 UI: navigation, wizard, settings pay plan, RO/job entry, jobs list.

## Next
- M3 wizard + settings + RO/job entry + jobs list, M4 time + dashboard, M5 paycheck, M6 CSV, M8 scan, M9 VIN/labor lookup.

## Known issues / decisions
- Semi-monthly periods are fixed at 1st–15th and 16th–end of month (periodStartDay ignored).
- Guarantee top-up compares guarantee hours with *flag* hours paid (hourly entries are not counted toward it).
- Efficiency-tier multiplier applies to flag pay + guarantee top-up (flag-rate money), not hourly pay.
- CLAWBACK: the comeback line counts as −(original's share), charged at the original's labor-type rate,
  in the comeback line's own work/paid periods.
