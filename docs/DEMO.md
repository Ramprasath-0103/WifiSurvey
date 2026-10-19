# Demo script

Everything lives in `~/Desktop/CN_Project`. Keep this file open on a second
screen or printed.

---

## Before they walk in (5 minutes)

> **Note on the medium indicator.** The access points here do not advertise
> BSS Load, so channel utilisation is unavailable. The analysis automatically
> falls back to the loaded/idle latency ratio (plan sections 3.1 and 7.2) and
> the dashboard says so in a blue note at the top. Nothing else changes.


**1. Start it up.**

```bash
cd ~/Desktop/CN_Project
./run.sh
```

That starts the database, runs the analysis, and opens the dashboard in your
browser. If it prints the classification tables and the browser opens, you are
ready. Leave the browser tab open.

**2. Get the phone ready.**

- Install `dist/wifi-survey-v1.2.apk` if it is not already on.
- Open the app once. Grant location permission.
- Check the gateway box is filled in. If it is blank you are not on Wi-Fi.
- Run one measurement now as a rehearsal, so you know it works in that room.

**3. Have these three things open:**

- The dashboard in the browser
- A terminal sitting in `~/Desktop/CN_Project`
- The phone, unlocked, app open

---

## The demo, in order

### Step 1 — The problem, in one sentence (30 seconds)

> "Everyone knows which corridors have bad Wi-Fi. What nobody can tell you is
> *why*. It could be that you are too far from the access point, or it could be
> that forty people are sharing the same channel. Those look identical to a
> user, but they need opposite fixes. This project tells them apart."

Do not open anything yet. Just say it.

### Step 2 — Show the app running live (90 seconds)

Pick a point on the phone, press **RUN MEASUREMENT**, and talk while it runs:

> "It scans the beacons first, then pings the gateway, then runs a download and
> pings again *during* the download, then uploads, then scans again to see if
> the channel got busier."

When the card appears, point at three lines:

- **RSSI** — "that is the signal, what every Wi-Fi app shows you"
- **ratio** — "idle latency versus latency under load. Over 5x means queueing"
- **ratio** again — "this is how we detect congestion. Our access point does not
  advertise BSS Load, which is common on consumer hardware, so instead of asking
  the AP how busy it is, we measure how much delay piles up when we saturate the
  link. A contended channel queues; a quiet one does not."

### Step 3 — The two maps (the headline)

Switch to the dashboard, top section.

> "Same floor, coloured two different ways. Left is signal strength. Right is
> the speed we actually measured."

Then click through the time tabs: **night**, then **evening**.

> "Watch the left map. It barely changes, because walls do not move. Now watch
> the right map."

Let them see the speeds collapse. Then:

> "Signal identical, speed down seventy percent. That is not a coverage problem."

### Step 3b — If they ask about BSS Load

Be straight about it:

> "The plan was to read channel utilisation out of the beacon. We checked, and
> no access point in this building advertises it. It is an optional field. So we
> fell back to the indicator our own plan listed for exactly this case: latency
> under load divided by latency when idle. Under 3x is a quiet channel, over 5x
> is a contended one. It needs nothing from the access point at all."

This is a strength, not an excuse. You anticipated the failure, documented the
fallback before it happened, and the fallback works.

### Step 4 — The diagnosis grid

Scroll to **What is wrong at each point**. Click **night**, then **evening**.

> "Green at night, red in the evening, at the same points. Those are
> capacity-limited. The two brown ones at the far end stay brown all day,
> because those genuinely are too far from the access point."

### Step 5 — The table that proves it

Scroll to **Same spot, different time of day**.

Point at the C3 row:

> "174 megabits at night, 51 in the evening. A seventy-one percent drop. Signal
> moved one decibel. Channel occupancy went up sixty points. There is nothing
> else it could be."

### Step 6 — The recommendation

Scroll to **Worst points, with a diagnosis**.

> "Every point gets a verdict and an action. And here is the one people get
> wrong: for a capacity-limited point, adding another access point on the same
> channel makes it *worse*, because the new one joins the same contention
> domain. The instinct is to add hardware. The instinct is wrong."

That is the strongest thing the project says. End on it.

### Step 7 — If there is time

- **Correlations** section: Pearson next to Spearman with n stated.
  "With this few points the intervals are wide, so we report both and say so."
- **Control measurement** section: "the same strong-signal spot every session.
  If that moves, the campus uplink was slow that day, not the survey point."

---

## Questions they will ask

**"Why a phone and not a laptop?"**
macOS cannot read beacon information elements at all since Apple removed the
airport utility. Windows can through the Native Wifi API, Linux through `iw`.
Android exposes it through a documented public API, and a phone is what our
users actually hold.

**"Is this real data?"**
Say so plainly. If the yellow banner is showing: "this is modelled sample data
so we could build and test the pipeline before the survey; the real export
replaces it and every chart redraws." Never claim modelled numbers are measured.

**"Where does -67 dBm come from?"**
Vendor design guides from Cisco and Aruba. It is a conventional working
threshold, not a standard, and the report says that.

**"Your proposal said you would use BSS Load. What happened?"**
No access point in the building advertises it. It is optional in the standard
and plenty of hardware omits it. Section 3.1 of our plan listed the fallback
indicators in advance, in order of strength, and the loaded/idle latency ratio
is the second of them. The classification matrix works the same way, just with
queueing delay on the medium axis instead of channel occupancy.

**"What about the simulation you proposed?"**
Scoped out on schedule grounds. It was the top item on our own cut list
precisely because dropping it costs the frame-level indicators and nothing else.
The survey, the app, the analysis and the maps are untouched.

**"How do you know it is not the internet being slow?"**
The control point. Measured first every session at the same strong-signal spot.
Latency is measured against the gateway, one hop away, so it never leaves the
building.

---

## Swapping in your real data

After the walk, export from the phone, then:

```bash
cd ~/Desktop/CN_Project
source .pgenv
psql -d wifi_survey -c "TRUNCATE measurements, sessions, classifications CASCADE;"
./run.sh ~/Downloads/wifi_survey_20261019_0930.json
```

The yellow sample-data banner disappears by itself once the data is real.

If your point labels mean something different from the placeholders, write
`db/my_places.json`:

```json
{ "A1": "under the AP in my room", "C5": "corridor outside" }
```

and import with `--places db/my_places.json`.

---

## If something breaks mid-demo

| Problem | Fix |
|---|---|
| Dashboard is blank | `./run.sh` again, it rebuilds the page |
| "connection refused" from psql | `./run.sh` starts the database for you |
| App says gateway is blank | you are not on Wi-Fi, or type it from Settings > Wi-Fi > your network |
| App says CACHED results | Android scan limit, wait two minutes |
| App shows no Wi-Fi at all | press SELF CHECK, it names the blocker. Usually Location services off |
| Measurement fails on download | no internet; latency and beacon data still record fine |

Worst case, the dashboard is a single self-contained HTML file. It opens by
double-click with no database and no server running at all.
