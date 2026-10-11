# Progress

Resume point for any session. Plan: [PLAN.md](PLAN.md). Gate: `./gradlew testDebugUnitTest assembleDebug`.

## Done
- Scaffold: AGP 9.4.1, Gradle 9.8.0 wrapper, Kotlin 2.4.20 (AGP built-in), KSP 2.3.12, Compose BOM 2026.09.00, JDK 21 toolchain.
  - compileSdk is **37.2** (current AndroidX/Compose/OkHttp require it); targetSdk stays 36, minSdk 26.
- M1 pay engine (pure Kotlin, `domain/`): Units, Models, PayPlan, PayPeriod, PaidPeriodPolicy, PayCalculator, Reconciler + tests.

- M2 data layer: Room entities/DAOs/converters (schema exported to app/schemas), PayPlanStore (DataStore + kotlinx.serialization),
  SecretStore (Android Keystore AES-GCM), FlagHoursRepository; Robolectric repository tests.

- M3 UI: theme, type-safe navigation with bottom nav, 7-step setup wizard (Skip resets a step to defaults),
  Settings > Pay plan with "what changed" preview of last period, new RO with lines, RO detail with status chips,
  full-screen job editor (adjusted hours, split, comeback picker, paid-period override), jobs list with
  status/period filters and swipe-to-mark-paid with Undo, JobTemplate autocomplete chips.

- M4 Time screen (big clock in/out, shifts per period with date+time editing, forgotten clock-out fix after 14 h,
  hourly entries) and Dashboard (worked/paid/owed, clocked, efficiency, est. gross, expandable math, clock card).
- Robolectric Compose smoke tests (`ui/SmokeTest`) walk setup → dashboard → clock → tabs → settings, and create an RO.
  They caught a real bug (navigation after a suspend save ran off the main thread) — fixed with `onMain`.

- M5 Paycheck screen: defaults to the period that just ended; stub hours + optional gross saved per period;
  recorded breakdown; gap card; reconciler explanations with one-tap fixes (mark paid this period / move to next
  period / open RO).

- M6 CSV backup: one-file typed-row CSV (`domain/Csv`, `domain/CsvExporter`) with ROs, jobs, shifts, hourly,
  stubs and pay plan JSON; Settings > Share backup / Save to file (SAF) / Restore (replace all, with counts
  confirmation). Round-trip tested in domain and repository. Photos aren't included.

- M8 RO scan: CameraX preview + capture (torch toggle), or pick a photo; bundled ML Kit text recognition (offline);
  OCR lines regrouped into visual rows; `RoTextParser` (RO#, VIN with OCR fixes + check digit, year/make/model,
  labor lines with hours + labor type) with tests; result opens the editable New RO draft; manual entry always
  available; photo kept with the RO (thumbnail + full view, deleted with the RO).

- M9 VIN decode via NHTSA vPIC (no key) fills year/make/model/engine on the RO; Open Labor Project lookup per
  line with the user's own key (stored encrypted), cache-first in LaborRefCache (misses cached too), 10/day limit
  enforced locally, 401/429/offline handled; range badge (green in range, yellow below, blue above) with
  attribution. Parsers tolerate small response-shape changes. Tested with a fake HTTP interceptor.

- README (build + sideload), CI green (runner's preinstalled SDK + platform 37.2), debug APK attached to the
  v0.1.0 GitHub pre-release.

- v0.1.1: signed release build (key in ~/keystores, outside the repo), R8 minify + resource shrinking with keep
  rules (Room, kotlinx.serialization, ML Kit, OkHttp), v2+v3 signatures, versionCode 2. Published as
  flag-hours-0.1.1.apk, sha256 b1e96b5d634e3bd31bfc022ef04b8082b56ecbfdaadd83a669146f598fdfed05.
  - v0.1.0's debug APK was valid (sha matched the local build; v2 signature OK; not testOnly; minSdk 26; zip OK; 4 ABIs).
    The phone's "package appears to be invalid" is most likely a signature clash with the earlier test app that has the
    same package name. Fix on the phone: uninstall the old app, then install.

- v0.2.0: labor lookup rework (see docs/olp-api.md). Root cause of "no information found": OLP `job` is an exact slug
  named after the part ("Radiator"), and make/model/engine must be OLP slugs.
  - Vehicle resolution: VIN (typed, scanned from the RO, or **VIN barcode** via bundled ML Kit barcode scanning) → vPIC
    (now also displacement + cylinders) → make via cached /vehicles makes list (180 d) → model fuzzy-matched against
    cached models per make (30 d) → **one /labor-times call per vehicle without job/engine**; engine picked by
    displacement+cylinders, picker if ambiguous; make/model pickers if ambiguous; manual year/make/model picker.
  - Cache: new tables olp_catalog, olp_vehicles, olp_vehicle_aliases, labor_rows (DB v2, Room auto-migration that
    drops only the old labor_ref_cache; user data kept; migration test). jobs gained refJob/refHoursHundredths,
    job_templates gained refJobSlug.
  - `domain/JobMatcher`: filler removal, synonyms/abbreviations, stemming, token overlap + edit distance, top 5;
    tested phrasings incl. "radiator replacement"/"R&R radiator"/"replace rad" → Radiator, "front brakes"/"frt pads"
    → Brake Pads - Front, "water pump replace" → Water Pump.
  - UI: vehicle panel on the RO screen, top 3 picks per line, "More…"/"Browse all jobs" sheet (search + categories),
    picks remembered per description+vehicle, auto-apply for remembered or clear-cut matches (so scanned lines are
    prefilled once the vehicle is loaded).
  - Real error messages (no key, bad key, daily limit with local reset time, burst, tier locked, payment, Cloudflare,
    not found with the slugs tried, offline); quota from X-RateLimit headers; local stop at 0 remaining.
  - Settings: share/clear the raw response log (last 40, never the key).
  - New RO with a VIN and no vehicle → free NHTSA decode on save.
  - Release APK now ARM-only (32 MB). Published flag-hours-0.2.0.apk, versionCode 3, sha256 1d2260c64055502ee7ff4d03a81ee565a20fb74237121f44ed1e1d4b15a43887.

## In progress
- Nothing. Waiting on milestone 7 feedback.

## Next
- Milestone 7: Jeremy daily-drives the app and reports issues.

## Known issues / decisions
- Release APK is ~32 MB (ARM only; bundled ML Kit OCR + barcode models).
- R8 output is not exercised by the Robolectric tests (they run the debug build); first launch on a phone is the real check.
- No Room migrations yet (schema v1); any entity change needs a migration before users have data.
- No OLP key on the build machine, so fixtures (app/src/test/resources/olp) follow the documented shape, not captured responses.
  The /vehicles shape is undocumented; `OlpParsers.vehicleOptions` accepts strings or objects with common key names.
  Whether labor-times rows carry an `engine` field is unknown; if they don't, all rows are used (no engine filter).
  If a real response doesn't parse, the user can share the lookup log from Settings.
- No KVM on the build machine, so no emulator; UI is smoke-tested with Robolectric Compose tests instead.
- Semi-monthly periods are fixed at 1st–15th and 16th–end of month (periodStartDay ignored).
- Guarantee top-up compares guarantee hours with *flag* hours paid (hourly entries are not counted toward it).
- Efficiency-tier multiplier applies to flag pay + guarantee top-up (flag-rate money), not hourly pay.
- CLAWBACK: the comeback line counts as −(original's share), charged at the original's labor-type rate,
  in the comeback line's own work/paid periods.
