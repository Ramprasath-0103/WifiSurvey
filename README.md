# Real-Time Network Quality Mapping and Connectivity Analysis

BCSE308P Computer Networks Lab — built to `CN_Project_Implementation_Plan_v2.md` (Revision 2,
phone-autonomous architecture).

## Layout

```
db/          PostgreSQL schema + the reference-point data
tools/       grid.html — place survey points on a grid, export JSON
scripts/     seed_points.py — validating loader for reference points
android/     the survey app (Kotlin)
tests/jvm/   standalone JVM tests for the pure parsing logic
dist/        built APKs
```

## Status

| Phase | Deliverable | State |
|---|---|---|
| 1 | Survey design: schema, grid editor, 27 reference points | done |
| 2 | Kotlin spike: BSS Load on screen | done |
| 3 | Ping + throughput primitives | next |
| 4 | App v1: full 8-stage measurement suite | |
| 5 | JSON export + Python importer | |
| 6–7 | Pilot survey, fixes | needs a phone and a building |
| 8 | Analysis pipeline | |
| 8b | ns-3 baseline `[TRACK S]` | |
| 9 | Full survey | needs a phone and a building |
| 10 | Dashboard | |
| 10b | ns-3 sweeps `[TRACK S]` | |

## Phase 1 — survey grid

Open `tools/grid.html` in a browser. Click cells to place reference points, name them, mark
exactly one as the **control point** (Plan §4.3), then **Export JSON**.

Load it into PostgreSQL:

```bash
createdb wifi_survey
psql -d wifi_survey -f db/schema.sql
python3 scripts/seed_points.py db/reference_points.json
```

The loader refuses input that would quietly ruin the survey later: duplicate labels, two points
in one grid cell, no control point (or more than one), invalid grid coordinates, unknown
location types, and point counts outside the 25–30 the plan asks for.

## Phase 2 — BSS Load spike

The smallest app that answers the one question Week 0 Check 2 asks: *does this campus's
hardware advertise BSS Load at all?* It scans, then prints every visible AP with its RSSI,
channel, band, and — where present — station count and channel utilisation.

**Install:**

```bash
adb install -r dist/wifi-survey-spike-v0.1.apk
```

Or copy the APK to the phone and open it (allow installing from unknown sources). Requires
**Android 11 (API 30) or newer** — `ScanResult.getInformationElements()` does not exist below
that, and without it BSS Load is unreachable.

On first run, grant location permission and make sure location services are on at system level,
or `getScanResults()` returns nothing.

**Reading the output.** The summary line reports how many of the visible APs advertise BSS
Load. If that number is 0 everywhere in the target building, Week 0 Check 2 has failed and you
fall back to the degraded indicator set in Plan §3.1 — raise it with Dr. Tamizharasi
immediately rather than in Week 9.

The app counts its own scans against Android's limit of four per two-minute window and says so,
because a throttled `startScan()` silently returns cached results.

**Build from source:**

```bash
cd android && ./gradlew :app:assembleRelease
```

Needs a `local.properties` with `sdk.dir=/path/to/android/sdk`, or `ANDROID_HOME` set.

**Run the unit tests** (JVM, no device needed):

```bash
cd android && ./gradlew :app:test
```

## Design note — why the parser is split three ways

`BssLoadParser.kt` and `RadioMath.kt` contain no `android.*` imports at all, so every piece of
arithmetic that matters — little-endian element decoding, unsigned byte handling, channel and
band derivation, the §7.1 classification thresholds — is unit-testable on a plain JVM.
`WifiScanner.kt` holds the thin Android glue that genuinely needs a device.

This matters because element-11 parsing is the single riskiest unknown in the project, and it is
much better to find a bug in it on a laptop than while standing in a stairwell.
# WifiSurvey
