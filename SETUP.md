# Running this on your laptop

Everything below is already installed and working on this Mac. This file is for
rebuilding it elsewhere, and for the viva question "what does it need to run".

## What you need

| Software | Why | Install |
|---|---|---|
| PostgreSQL 14 | holds the measurements | `brew install postgresql@14` |
| Python 3 | analysis | ships with macOS, or `brew install python` |
| pandas, scipy, psycopg2 | the analysis itself | see below |
| A web browser | the dashboard | already have one |

Nothing else. No Node, no web server, no ns-3, no Wireshark. The dashboard is a
single HTML file with the data baked into it, so it opens by double-click.

```bash
python3 -m venv .venv
.venv/bin/pip install pandas scipy psycopg2-binary
```

## One thing to know about Postgres on this machine

Something else already occupies port **5432** here, and it wants a password we do
not have. So our database runs on **5433** instead, from the Homebrew data
directory. Nothing of yours was changed.

```bash
source .pgenv          # sets PGPORT=5433, PGHOST=/tmp, PGDATABASE=wifi_survey
```

Start it by hand if it is not running:

```bash
/opt/homebrew/opt/postgresql@14/bin/pg_ctl \
  -D /opt/homebrew/var/postgresql@14 -o "-p 5433 -k /tmp" -l .pg/server.log start
```

## First time setup

```bash
createdb wifi_survey
psql -d wifi_survey -f db/schema.sql
.venv/bin/python scripts/seed_points.py db/reference_points.json
```

## Every day after that

```bash
./run.sh                                   # re-analyse, reopen the dashboard
./run.sh ~/Downloads/wifi_survey_xxx.json  # import a new export, then analyse
```

Importing the same file twice is safe. Measurements are keyed on the UUID the
phone generated, so a repeat import updates rows rather than duplicating them.

## Replacing the sample data with yours

Right now the database holds generated sample data so the pipeline could be built
before the walk. To swap in the real thing:

```bash
source .pgenv
psql -d wifi_survey -c "TRUNCATE measurements, sessions, classifications CASCADE;"
./run.sh ~/Downloads/wifi_survey_20261019_0930.json
```

The yellow "Sample data" banner on the dashboard disappears on its own once the
data no longer comes from the generator.

### If your point names differ from the placeholders

Write a small JSON file mapping label to what the spot really is:

```json
{ "A1": "under the AP in my room", "A3": "far corner", "C5": "corridor outside" }
```

then

```bash
.venv/bin/python scripts/import_session.py export.json --places db/my_places.json
```

## What is in here

```
android/     the survey app (Kotlin)
db/          schema, reference points, the sample export
scripts/     seed_points.py, import_session.py, make_demo_data.py
analysis/    pipeline.py - correlations, scoring, classification
dashboard/   template.html (source) and index.html (generated, self-contained)
docs/        the Review 1 document and its figures
dist/        built APKs
```

## Where the big regenerable things live

The Desktop syncs to iCloud, so two heavy folders were deliberately kept out of
the project:

| What | Where | Size | Needed for |
|---|---|---|---|
| Gradle 8.11.1 | `~/cn-toolchain/gradle-8.11.1` | 276 MB | rebuilding the APK only |
| Build output | regenerated on build | 48 MB | nothing, it is output |

To rebuild the APK:

```bash
cd ~/Desktop/CN_Project/android
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
  ~/cn-toolchain/gradle-8.11.1/bin/gradle :app:assembleRelease
```

`.venv` does stay in the project because `run.sh` expects it there. If iCloud
ever mangles it, delete it and run:

```bash
python3 -m venv .venv && .venv/bin/pip install pandas scipy psycopg2-binary
```
