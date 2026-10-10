# Flag Hours — Plan

Android app for a flat-rate (flag-hour) technician to record what's flagged on each repair order (RO),
track time, and check paychecks.

## Setup
- Kotlin + Jetpack Compose (Material 3) + Room (KSP) + DataStore + Navigation Compose + Lifecycle ViewModel + kotlinx.serialization.
- Package `com.jeremy.flaghours`, app name "Flag Hours", minSdk 26, target/compile 36. Single module, manual DI in `FlagHoursApp.kt`.
- Gradle wrapper; JDK 17/21 toolchain (system default is JDK 25). `local.properties` sdk.dir=/home/dreamz/Android/Sdk (gitignored).
- Private GitHub repo `flag-hours`; GitHub Actions runs unit tests and builds a debug APK artifact.
- PROGRESS.md (done / in progress / next / known issues) updated and committed after every milestone.
- Each milestone gated on `./gradlew testDebugUnitTest assembleDebug` passing.

## Accuracy principle
The RO is the source of truth; the app records what's flagged on it. Hours stored as Int hundredths (1.6 = 160),
money as Long cents. No floats in money math. PayCalculator, PayPeriod, Reconciler, RoTextParser are pure Kotlin
(no Android imports) with thorough unit tests.

## Pay rules (all handled + tested)
1. Partial flags: `mySplitPct` per job.
2. Status OPEN → CLOSED → PAID.
3. Carryover: `workDate` vs `paidPeriodStart` are separate; dashboard shows worked vs paid vs still owed.
4. Warranty adjustment: `adjustedHundredths` overrides flagged; diff shown.
5. Rates per labor type CUSTOMER / WARRANTY / INTERNAL / DIAG.
6. Comebacks: `comebackOfJobId`; rule PAID / ZERO_PAY / CLAWBACK (negate original) / SPLIT.
7. Guarantee: None | Flat(hours) | IfClocked(hours, minClocked) — top-up at flag rate.
8. HourlyEntry (training / shop / diag-by-clock) at hourly rate.
9. Optional efficiency tiers (rate multiplier above X%).
10. Optional overtime rule; never hardcoded; show the math used.

## Calculation per period P
- flaggedWorked = Σ myShare for workDate in P
- flaggedPaid = Σ myShare for paidPeriod == P
- stillOwed = Σ myShare for workDate ≤ P.end && status != PAID
- clocked = Σ shift durations (LocalDateTime, crosses midnight, open shift counts to now)
- efficiency = flaggedWorked / clocked × 100 (null if clocked is 0)
- myShare = (adjusted ?: flagged) × split / 100, comeback rule applied
- grossPaid = Σ by type (flaggedPaid_type × rate_type) + hourly × hourlyRate + guarantee top-up, × tier multiplier
- checkGap = stubHours − (flaggedPaid + hourly + topUp); Reconciler lists the specific jobs explaining the gap
  (not marked paid, split mismatch, warranty cut, paid in other period).

## Pay plan (DataStore, kotlinx.serialization; `PayCalculator.summarize(..., plan)`)
flagRateCents; warrantyRateCents? / internalRateCents? / diagRateCents? (null = flag rate); hourlyRateCents;
period WEEKLY / BIWEEKLY / SEMI_MONTHLY + periodStartDay (+ anchor date for biweekly);
flagsPayWhen RO_CLOSED / MARKED_PAID / WORK_DATE (drives auto paidPeriodStart); guarantee; comebackRule;
overtime?; efficiencyTiers; decimals = 1.

First-launch wizard + Settings > Pay plan (same screens): 1 rates, 2 pay period, 3 when flags pay, 4 guarantee,
5 warranty/internal/diag rates, 6 comebacks, 7 extras (OT, tiers, decimals). Every step has a default and Skip.
Settings shows a "what changed" preview of last period's pay before saving. Totals computed live, never stored.

## Entities (Room)
- RepairOrder(id, roNumber, openedDate, vin?, year?, make?, model?, engine?, photoUri?, notes?)
- Job(id, roId, description, laborType, flaggedHundredths, adjustedHundredths?, mySplitPct = 100, workDate, status,
  paidPeriodStart?, comebackOfJobId?, refLowHundredths?, refHighHundredths?, refConfidence?)
- Shift(id, start, end?)
- HourlyEntry(id, date, minutes, reason)
- JobTemplate(id, description, make?, model?, flaggedHundredths, timesUsed) — autocomplete
- PayStub(periodStart PK, hoursHundredths, grossCents?)
- LaborRefCache(make, model, year, engine, jobSlug PK…, job, hours/low/high hundredths, confidence, fetchedAt)

## Screens (big touch targets, gloves)
Dashboard (worked/paid/owed, clocked, efficiency, est gross, big clock in/out); Scan RO; RO detail (VIN via vPIC,
lines, status chips, reference-range badge); Jobs list (filter Open/Unpaid/Paid + period, swipe mark paid);
Time (shifts, hourly entries, fix forgotten clock-out); Paycheck check (stub entry + gap explainer); Setup wizard;
Settings (pay plan, OLP API key stored encrypted, CSV export/import via share sheet / SAF).
Bottom nav: Dashboard / Jobs / Time / Paycheck; settings in top bar.

## Structure
data/{db,settings,remote}, domain/{PayCalculator,PayPeriod,Reconciler,RoTextParser,CsvExporter},
ui/{dashboard,scan,ro,jobs,time,paycheck,setup,settings,theme,navigation}; tests under app/src/test/.../domain.

## Milestones
1. Pay engine + tests.
2. Room DB / DAOs / repos (+ Room tests if feasible).
3. Wizard + settings pay plan + RO/job entry + jobs list + mark paid + JobTemplate autocomplete.
4. Shifts / hourly + dashboard.
5. Paycheck stub + reconciler UI.
6. CSV export/import.
7. (Jeremy daily-drives it.)
8. RO scan: CameraX + ML Kit text-recognition bundled (com.google.mlkit:text-recognition:16.0.1), RoTextParser
   regex heuristics (RO#, 17-char VIN, labor lines with hours) with tests, editable draft before save, manual entry
   always available.
9. NHTSA vPIC VIN decode (no key, https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/{vin}?format=json) +
   Open Labor Project lookup (GET https://openlaborproject.com/api/v1/labor-times?make=&model=&year=&engine=&job=,
   header x-api-key = user's own key from settings, never bundled; cache-first in LaborRefCache; free tier 10/day;
   attribution "Labor reference data: Open Labor Project" shown) and range badge (green in range, yellow below low).
   App must work fully offline without a key.

## Done when
All milestones in, tests green, debug APK builds, code pushed, PROGRESS.md and README (build + sideload) updated.
