# Demo Day — Complete Guide

**Project:** Real-Time Network Quality Mapping and Connectivity Analysis
**Course:** BCSE308P Computer Networks Laboratory
**Team:** Ram Prasath R P (24BCE0176), Nehal Arun (24BCT0151)

Everything lives in `~/Desktop/CN_Project`. Print this or keep it on a second screen.

---

# PART 1 — WHAT TO DO ON THE DAY

## 1.1 Thirty minutes before

**Step 1. Start the system.**

```bash
cd ~/Desktop/CN_Project
./run.sh
```

This does three things: starts the database, runs the analysis, opens the dashboard
in your browser. If you see the classification tables printed and a browser window
appears, you are ready. **Leave that browser tab open.**

**Step 2. Check the phone.**

- `dist/wifi-survey-v1.3.apk` installed
- Open the app, confirm the **GATEWAY** box is filled in (it should read `172.16.64.1`
  on campus). If blank, you are not on Wi-Fi.
- Press **SELF CHECK**. Every line should pass. If anything says DENIED or OFF, fix
  it now, not in front of your examiner.
- Run **one practice measurement** in the demo room so you know how long it takes
  and that it works there.

**Step 3. Have these open and ready:**

| What | Where |
|---|---|
| Dashboard | browser tab, already loaded |
| Terminal | sitting in `~/Desktop/CN_Project` |
| Phone | unlocked, app open, screen timeout set long |
| This document | second screen or printed |

**Step 4. Decide your opening sentence** and say it out loud once to yourself.

## 1.2 Every command you might need

```bash
# Start everything and open the dashboard
./run.sh

# Import a new export from the phone, then analyse
./run.sh ~/Downloads/wifi_survey_20261019_1930.json

# Just reopen the dashboard without re-running anything
open dashboard/index.html

# Connect to the database by hand
source .pgenv
psql -d wifi_survey

# Start the database if run.sh did not
/opt/homebrew/opt/postgresql@14/bin/pg_ctl \
  -D /opt/homebrew/var/postgresql@14 -o "-p 5433 -k /tmp" -l .pg/server.log start

# Run only the analysis, no import
source .pgenv && .venv/bin/python analysis/pipeline.py

# Import only one time window from a cumulative export
.venv/bin/python scripts/import_session.py FILE.json --window evening

# Wipe the measurements but keep the reference points
source .pgenv
psql -d wifi_survey -c "TRUNCATE measurements, sessions, classifications CASCADE;"

# Rebuild the app
cd android && ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
  ~/cn-toolchain/gradle-8.11.1/bin/gradle :app:assembleRelease

# Run the unit tests
cd android && ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
  ~/cn-toolchain/gradle-8.11.1/bin/gradle :app:test
```

**Note on the port.** Something else on this Mac already owns PostgreSQL's usual port
5432 and asks for a password we do not have. Rather than disturb it, our database runs
on **5433**. `source .pgenv` sets that for you; `run.sh` handles it automatically.

## 1.3 The demo, step by step

### Step 1 — The problem (30 seconds, nothing on screen)

> "Everyone knows which corridors have bad Wi-Fi. What nobody can tell you is *why*.
> You might be too far from the access point, or forty other people might be sharing
> the channel. To a user those look identical, but they need opposite fixes. Adding an
> access point helps one and makes the other worse. This project tells them apart."

Do not open anything yet. Just say it.

### Step 2 — Run a live measurement (90 seconds)

Pick a point, press **RUN MEASUREMENT**, and narrate while it runs:

> "It scans the beacons first, then pings the gateway, then downloads while pinging
> again *during* the download, then uploads, then scans a second time to see whether
> the channel got busier because of us."

When the card appears, point at three lines:

- **RSSI** — "that is signal strength, what every Wi-Fi app on a phone shows you"
- **ratio** — "idle latency against latency under load. This is how we detect congestion"
- **NEIGHBOURS** — "every access point we can hear, and how many of them publish how
  busy they are"

### Step 3 — The two maps

Switch to the dashboard. Top section.

> "Same floor, coloured two ways. Left is signal strength. Right is the speed we
> actually measured. Where those disagree, signal is not the problem."

Point at **A1 and C5**: both −68 dBm, identical signal. A1 gets 19.2 Mbps, C5 gets
3.6 Mbps. Five times the difference at the same signal strength.

### Step 4 — The diagnosis grid

> "Each point gets a category. Signal strength on one axis, how congested the medium
> is on the other."

Three of six are capacity limited, one is coverage limited, two are mixed.

### Step 5 — The control point finding (your strongest result)

Scroll to the worst-points table. Point at **C5**.

> "C5 is our control point. −68 dBm, decent signal, and the access point reports the
> channel as **0.78 percent occupied** — essentially empty air. But idle latency is
> 9 milliseconds and under load it is 305. A thirty-four times increase.
>
> An empty channel cannot cause that. So the queueing is not in the Wi-Fi at all, it
> is upstream, in the campus uplink. That is exactly what a control point is for, and
> we would never have seen it without one."

This is the most interesting thing in your data. Lead with it.

### Step 6 — The recommendation

> "Every point gets an action attached. The important one is this: if a point is
> capacity limited, adding another access point **on the same channel makes it worse**,
> because the new one joins the same contention domain. The instinct is to add
> hardware. The instinct is wrong."

End there.

### Step 7 — If there is time

Show the correlation table, and say plainly that with six points the numbers are
indicative only. Show the control-measurement row.

---

# PART 2 — WHAT EVERY FILE IS

```
CN_Project/
├── run.sh                    one command: start db, analyse, open dashboard
├── SETUP.md                  what to install, how to rebuild elsewhere
├── README.md                 project overview and phase status
├── .pgenv                    sets PGPORT=5433 etc. "source .pgenv"
│
├── android/                  the survey app, written in Kotlin
│   └── app/src/main/java/edu/vit/wifisurvey/
│       ├── BssLoadParser.kt      decodes beacon element 11. Pure, unit-tested
│       ├── RadioMath.kt          channel/band from frequency, the 7.1 thresholds
│       ├── WifiScanner.kt        the thin Android glue that needs a real device
│       ├── PingOutputParser.kt   turns ping text into numbers. Pure, unit-tested
│       ├── ThroughputMath.kt     Mbps arithmetic, slow-start discard. Pure
│       ├── PingRunner.kt         runs the system ping, falls back to TCP timing
│       ├── ThroughputRunner.kt   timed HTTPS download and upload
│       ├── MeasurementRunner.kt  orchestrates the 8 stages
│       ├── MeasurementStore.kt   appends each result to a file, exports JSON
│       ├── ReferencePoints.kt    the 27 grid points
│       ├── Measurement.kt        the data model and its JSON shape
│       └── MainActivity.kt       the screen
│   └── app/src/test/...          JUnit tests, run on a laptop with no phone
│
├── db/
│   ├── schema.sql            all 7 tables
│   ├── reference_points.json the grid layout
│   └── exports/              raw exports from the phone, kept for reproducibility
│
├── scripts/
│   ├── seed_points.py        loads reference points, validates them first
│   ├── import_session.py     phone JSON -> PostgreSQL
│   └── make_demo_data.py     generates sample data (not used now, real data is in)
│
├── analysis/
│   └── pipeline.py           joins, correlates, scores, classifies, writes results
│
├── dashboard/
│   ├── template.html         the page source
│   ├── index.html            generated, with the data baked in. Open this
│   └── results.json          the same data as a plain file
│
├── tools/
│   └── grid.html             place and name survey points, export JSON
│
├── docs/                     this file, the demo script, Review 1 doc, figures
└── dist/                     the built APK
```

**The three-way split in the app is deliberate.** `BssLoadParser.kt`, `RadioMath.kt`,
`PingOutputParser.kt` and `ThroughputMath.kt` contain no `android.*` imports at all,
so every piece of arithmetic that matters is testable on a plain laptop. Only
`WifiScanner.kt`, `PingRunner.kt` and `ThroughputRunner.kt` need a real device. That
is why beacon parsing could be proven correct before anyone walked anywhere.

---

# PART 3 — WHAT EVERY NUMBER MEANS

## 3.1 The seven database tables

| Table | Holds | Rows now |
|---|---|---|
| `reference_points` | the 27 grid locations, one marked as control | 27 |
| `sessions` | one survey run: a time window, plus that session's control reading | 1 |
| `measurements` | one press of the button: all the performance numbers | 6 |
| `bss_load` | channel utilisation and station count from the serving AP's beacon | 6 |
| `neighbours` | every access point visible at that point | 164 |
| `classifications` | the verdict and recommendation per point | 6 |
| `medium_capture_stats` | frame-level data from simulation. Empty, Track S was cut | 0 |

## 3.2 Every measured field

| Field | What it is | How we get it |
|---|---|---|
| `rssi_dbm` | signal strength. Negative; closer to zero is stronger | `WifiInfo.getRssi()` |
| `band`, `channel` | 2.4 / 5 GHz, and which channel | derived from frequency |
| `tx_link_speed_mbps` | the rate the phone and AP negotiated | `WifiInfo.getTxLinkSpeedMbps()` |
| `dl_mbps` / `ul_mbps` | real throughput, first 2 s discarded | timed HTTPS transfer |
| `idle_latency_ms` | median round trip to the gateway, link quiet | 50 pings |
| `idle_latency_p95_ms` | the slow tail of those pings | 95th percentile |
| `loaded_latency_ms` | median round trip **during** the download | 40 pings, concurrent |
| `latency_ratio` | loaded ÷ idle. Computed by the database itself | generated column |
| `jitter_ms` | how uneven the replies are | ping `mdev` |
| `packet_loss_pct` | pings sent that never came back | ping summary |
| `recovery_latency_ms` | latency after the load stops | 20 pings |
| `channel_util_pct` | how much of the time the channel is occupied, 0-100 | beacon element 11 |
| `station_count` | devices attached to that AP | beacon element 11 |
| `latency_method` | `icmp` normally, `tcp_connect` if ICMP is blocked | recorded per row |
| `note` | what the phone reported failing, verbatim | app error text |

## 3.3 The thresholds, and where they come from

**Signal strength** (Section 7.1 of the plan):

| Band | Range | Meaning |
|---|---|---|
| adequate | ≥ −67 dBm | reliable for data and voice |
| marginal | −67 to −75 | usable, degrading |
| weak | < −75 | below normal enterprise design floors |

**Medium congestion** — normally channel utilisation from the beacon:

| Band | Range |
|---|---|
| light | < 30% |
| moderate | 30 – 50% |
| contended | > 50% |

**When BSS Load is unavailable**, the loaded/idle latency ratio substitutes:

| Band | Range |
|---|---|
| light | < 3× |
| moderate | 3 – 5× |
| contended | > 5× |

The −67 dBm and 50% figures come from Cisco and Aruba deployment guides. They are
conventional operating thresholds, **not** anything defined by a standard, and the
report says so.

## 3.4 The classification matrix

|  | medium light | medium moderate | medium contended |
|---|---|---|---|
| **RSSI adequate** (≥ −67) | unconstrained | mixed | **capacity limited** |
| **RSSI marginal** (−67 to −75) | mixed | mixed | capacity limited |
| **RSSI weak** (< −75) | **coverage limited** | coverage limited | mixed (both) |

## 3.5 The quality score

```
quality = 0.30 × download
        + 0.20 × upload
        + 0.25 × (inverse loaded latency)
        + 0.15 × (inverse jitter)
        + 0.10 × (inverse packet loss)
```

Every metric is min-max normalised to 0–100 across all points first, and inverted where
lower is better. A transfer that timed out scores **zero** for that component rather
than being excluded, because a failed transfer is a result, not missing data. If a
latency figure is genuinely absent, the remaining weights are renormalised.

Weights are a judgement call: responsiveness is weighted above raw throughput because
that is what users actually perceive.

---

# PART 4 — YOUR ACTUAL RESULTS

Six points, afternoon window, 19 October 2026. One repetition each.

| Point | Where | RSSI | Band | Link | Down | Up | Idle | Loaded | Ratio | Loss | Util | Verdict | Score |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A1 | NW stairwell | −68 | 5 GHz ch52 | 260 | 19.2 | 12.7 | 1.9 | 19.8 | 10.3× | 0% | 0.78% | capacity | 100.0 |
| A5 | outside Lab 102 | −51 | 2.4 ch9 | 144 | 16.8 | 12.1 | 14.9 | 67.3 | 4.5× | 0% | — | mixed | 91.2 |
| A9 | outside Class 104 | −75 | 2.4 ch6 | 57 | 3.9 | 12.5 | 28.8 | 337 | 11.7× | 0% | — | capacity | 53.1 |
| C1 | corridor west | −71 | 2.4 ch1 | 86 | 0.8 | failed | 702 | 389 | 0.6× | 44% | 63.5% | mixed | 0.0 |
| C5 | corridor centre **(control)** | −68 | 5 GHz ch169 | 180 | 3.7 | 4.9 | 9.0 | 305 | 33.9× | 0% | 0.78% | capacity | 35.2 |
| C9 | corridor | −76 | 2.4 ch1 | 43 | failed | failed | 10.2 | 23.0 | 2.3× | 2% | — | coverage | 49.1 |

## 4.1 The three things worth pointing at

**A1 versus C5.** Identical signal, −68 dBm both. A1 delivers 19.2 Mbps, C5 delivers
3.6. If signal strength explained performance, these would match. They differ by five
times. **This is the whole thesis of the project in two rows.**

**C5's latency ratio of 33.9×.** 9 ms idle, 305 ms loaded, on a channel the AP reports
as 0.78% occupied. Empty air cannot queue. The bottleneck is the campus uplink, not the
wireless hop. The control point caught something no survey point could have.

**C9 at −76 dBm failed both transfers entirely.** Weakest signal, lowest link rate
(43 Mbps), and neither the download nor the upload completed in 15 seconds. That is a
genuine coverage problem, and it looks different from C5's congestion problem.

## 4.2 Known problems with this data — say these before you are asked

**One window only.** Afternoon. The morning-versus-evening comparison, which is the
strongest evidence the method can produce, is missing because there is only one pass.
The time-of-day table on the dashboard is empty for this reason.

**One repetition.** No median, no spread, so no stability indicator.

**C1 is not trustworthy.** Idle latency 702 ms, loaded 389 ms. Loaded is *lower* than
idle, which is physically backwards, and packet loss was 44%. The link was failing
during that run. It should be re-measured.

**Three points have no BSS Load.** A5, A9 and C9 did not identify their serving AP
(`WifiInfo` returned no BSSID, which Android 12 sometimes does), so the beacon could not
be matched. The analysis fell back to the latency ratio for those.

**C9's RSSI disagrees with itself.** `WifiInfo` reports −76 dBm; the scan shows the same
AP at −48. A 28 dB discrepancy, probably a stale reading. Re-measure.

**n = 6.** Correlations at this sample size are indicative at best. Do not present them
as findings.

---

# PART 5 — HOW TO EXPLORE THE DATA

## 5.1 In the dashboard

| Section | What it shows |
|---|---|
| **Signal next to speed** | two grids, same floor. Click the time tabs to switch window |
| **What is wrong at each point** | the category per point, colour coded |
| **Any metric, any time** | pick any measured field and see it on the grid |
| **Same spot, different time of day** | needs two or more windows. Empty right now |
| **Worst points with a diagnosis** | ranked by quality score, with the recommendation |
| **Correlations** | Pearson beside Spearman, with n stated |
| **Control measurement** | the control reading per session |

## 5.2 In the database

```bash
source .pgenv
psql -d wifi_survey
```

**Everything at every point:**

```sql
SELECT r.label, r.place_name, m.rssi_dbm, m.band, m.channel,
       round(m.dl_mbps,1) AS down, round(m.ul_mbps,1) AS up,
       m.idle_latency_ms AS idle, m.loaded_latency_ms AS loaded,
       round(m.latency_ratio,1) AS ratio, m.packet_loss_pct AS loss
FROM measurements m JOIN reference_points r ON r.id = m.ref_point_id
ORDER BY m.taken_at;
```

**The verdict and why:**

```sql
SELECT r.label, c.category, round(c.quality_score,1) AS score,
       c.primary_cause, c.recommendation
FROM classifications c JOIN reference_points r ON r.id = c.ref_point_id
ORDER BY c.quality_score;
```

**How many APs we could hear, and how many publish their load:**

```sql
SELECT r.label,
       count(*) AS aps_visible,
       count(*) FILTER (WHERE n.is_serving) AS serving,
       (SELECT count(*) FROM bss_load b WHERE b.measurement_id = m.id) AS with_bss_load
FROM measurements m
JOIN reference_points r ON r.id = m.ref_point_id
JOIN neighbours n ON n.measurement_id = m.id
GROUP BY r.label, m.id ORDER BY r.label;
```

**Co-channel contention — who else is on our channel:**

```sql
SELECT r.label, m.channel AS our_channel,
       count(*) FILTER (WHERE n.channel = m.channel AND NOT n.is_serving) AS others_same_channel
FROM measurements m
JOIN reference_points r ON r.id = m.ref_point_id
JOIN neighbours n ON n.measurement_id = m.id
GROUP BY r.label, m.channel ORDER BY 3 DESC;
```

**What failed and where:**

```sql
SELECT r.label, m.note FROM measurements m
JOIN reference_points r ON r.id = m.ref_point_id
WHERE m.note IS NOT NULL;
```

**The busiest access points we can hear:**

```sql
SELECT n.bssid, n.ssid, n.channel, max(n.rssi_dbm) AS best_rssi, count(*) AS times_seen
FROM neighbours n GROUP BY 1,2,3 ORDER BY times_seen DESC, best_rssi DESC LIMIT 15;
```

## 5.3 The raw files

- `db/exports/` — exactly what came off the phone, untouched
- `dashboard/results.json` — everything the analysis produced, readable in any editor

---

# PART 6 — SEVENTY QUESTIONS AND ANSWERS

## A. The project itself (1–10)

**1. What problem does this solve?**
Telling apart two causes of bad Wi-Fi that look identical to a user: being too far from
the access point, and too many devices sharing the channel. They need opposite fixes.

**2. Why does it matter which one it is?**
If an area is out of range, adding an access point helps. If it is congested, adding one
on the same channel makes it *worse*, because the new AP joins the same contention
domain and adds another transmitter competing for airtime.

**3. What is your main contribution?**
Measuring signal and congestion at the same place and the same second, on ordinary
hardware, and producing a per-location diagnosis rather than just a heatmap.

**4. Who would use this?**
A campus network team deciding where to spend money, or any department that wants to
know whether a complaint about Wi-Fi is a coverage problem or a capacity problem.

**5. What makes it different from a speed test app?**
A speed test tells you it is slow. It has no idea why. We record the radio conditions
at the same instant and tie them to the result.

**6. What makes it different from a Wi-Fi analyser app?**
An analyser shows signal strength live and forgets it. Nothing is tied to a location,
nothing is compared across time, and there is no performance measurement at all.

**7. What makes it different from Ekahau or NetSpot?**
Those are excellent and expensive, and usually need a supported external adapter. They
also mainly map signal. We work on a phone anyone already owns and we output a cause,
not just a colour.

**8. Is this novel research?**
No, and we do not claim that. Indoor wireless surveying is well established. What is new
here is the combination at a student-project level and the fact it needs no special
hardware.

**9. What would you do with more time?**
Four time windows instead of one, three repetitions per point, the full 27-point grid,
and the ns-3 simulation to establish causation rather than correlation.

**10. Could the department actually use this output?**
The ranked worst-points list with a cause attached, yes. The sample size would need to
be much larger before anyone spent money on it.

## B. Wi-Fi fundamentals (11–22)

**11. What is RSSI?**
Received Signal Strength Indicator, the power of the radio signal arriving at the phone,
in dBm. It is logarithmic and negative; −50 is roughly a thousand times more power than
−80.

**12. Why is it negative?**
The power arriving is a tiny fraction of a milliwatt. dBm expresses it on a log scale
relative to one milliwatt, so anything below a milliwatt is negative.

**13. What does 3 dB mean?**
Every 3 dB is a factor of two in power. −53 dBm is half the power of −50 dBm.

**14. Why is −67 dBm the threshold?**
It is the figure vendor design guides use for reliable data and voice coverage. It is a
convention, not a standard, and our report says so explicitly.

**15. What is a BSSID and how is it different from an SSID?**
SSID is the network name, which many access points share. BSSID is the MAC address of
one specific radio on one specific access point. We need BSSID to know which physical
box is serving a given spot.

**16. What is channel utilisation?**
The fraction of time the channel is occupied by any transmission. 0 means the air is
silent, 100 means it is saturated.

**17. Where does it come from?**
The BSS Load information element, element ID 11, inside the beacon frame the access
point broadcasts about ten times a second. Five bytes: station count, utilisation on a
0–255 scale, and admission capacity.

**18. Why do only some access points publish it?**
It is optional in the 802.11 standard, and only present when the AP supports QoS. Plenty
of hardware simply omits it. We found 3 of our 6 measurements carried it.

**19. What is co-channel interference?**
Two access points on the same channel can hear each other, so they take turns. They
share airtime instead of adding capacity.

**20. 2.4 GHz versus 5 GHz?**
2.4 travels further and penetrates walls better but has only three non-overlapping
channels and is crowded by other devices. 5 GHz is faster and much less congested but
has shorter range.

**21. Why is link speed higher than measured throughput?**
Link speed is the rate negotiated for when you get a turn to transmit. Throughput is
what you get after sharing airtime with everyone else, plus protocol overhead. A1 shows
260 Mbps link rate and 19.2 Mbps actual.

**22. What is bufferbloat?**
Excessive queueing in network buffers. When a link saturates, packets pile up in a queue
instead of being dropped, so latency explodes while throughput looks fine. It is why we
measure latency *during* the download, not only before it.

## C. Methodology (23–34)

**23. Why measure latency twice?**
Idle latency tells you very little. A link can look fine at rest and collapse the moment
anyone uses it. The ratio between loaded and idle is what matters, and it is the single
best congestion indicator we have when BSS Load is missing.

**24. Why ping the gateway rather than a website?**
The gateway is one hop past the access point, so a round trip to it is dominated by the
wireless link. It is also always reachable, even on networks that block client-to-client
traffic.

**25. Why does throughput go to a public endpoint?**
Because we could not get a wired host on the campus network. That means throughput is
end-to-end and includes the campus uplink, which we state openly.

**26. Is that not a serious weakness?**
It is a real limitation, and we control for it three ways: one endpoint for the whole
study, a control measurement at a fixed point every session, and all conclusions stated
as relative comparisons rather than absolute speeds.

**27. What is the control point for?**
To separate "this spot is bad" from "campus internet was bad today". C5 is measured
first in every session. If its number moves between sessions, the uplink moved, not the
building.

**28. Why discard the first two seconds of the transfer?**
TCP starts slowly and ramps up. Timing from the first byte would understate the link.
We measure the steady-state portion.

**29. Why only two scans per point?**
Android limits a foreground app to four Wi-Fi scans per two minutes, device wide. Two
scans per point stays safely inside that. The app also checks whether the results were
actually refreshed rather than served from cache.

**30. Why scan before and after the test?**
To see whether our own traffic changed the channel occupancy. At C5 the utilisation went
from 0.78% before to 45.9% after, which is us.

**31. Why fixed time windows?**
Because the comparison only means anything if the windows are consistent. A 9am pass
versus a 7pm pass is a real comparison. Two passes an hour apart is not.

**32. Why three repetitions in the plan?**
To take a median and report the spread as a stability indicator. We managed one in the
time available, which is a limitation we state.

**33. How do you know the phone itself is not the bottleneck?**
We record the negotiated link rate. At A1 it was 260 Mbps while we measured 19.2, so the
phone's radio was not the limit. Where link rate is low, as at C9's 43 Mbps, that is the
signal being weak, which is exactly what we are trying to detect.

**34. Why a grid instead of a floor plan?**
Simpler to set up, reproducible, and renders cleanly. A labelled grid cell is its own
location reference. Nothing in the analysis needs a floor plan image.

## D. The application (35–45)

**35. Why did you write your own app?**
Nothing existing records measurements against a fixed location and correlates them with
performance. Adapting an analyser app would have been more work than writing the logger,
because the part we need is not something analysers do.

**36. Why Android and not a laptop?**
macOS cannot read beacon information elements at all since Apple removed the airport
utility. Windows can through the Native Wifi API and Linux through `iw`, but Android
exposes it through a documented public API, and a phone is what our users actually hold.

**37. Why minimum Android 11?**
`ScanResult.getInformationElements()`, the call that exposes raw beacon elements, was
added in API level 30. Below that BSS Load is unreachable and the medium layer of the
project disappears. The app refuses to install rather than degrade silently.

**38. Why does a Wi-Fi app need location permission?**
Android treats visible access points as location data, because a list of nearby BSSIDs
can locate you. Without the permission, and without location services switched on
system-wide, `getScanResults()` returns an empty list with no error.

**39. How do you measure latency without root?**
Android apps cannot open raw ICMP sockets. `InetAddress.isReachable()` falls back to a
TCP probe that does not give real round trips. So we invoke the system `ping` binary and
parse its output, which gives min, average, max, mdev and loss in one go.

**40. What if ICMP is blocked?**
We fall back to timing TCP connects to a closed port. The SYN gets an immediate RST, so
the round trip is still valid. Every row records which method produced it, in
`latency_method`.

**41. What are the eight stages?**
Scan; read connection info; idle ping; download; loaded ping concurrent with the
download; upload; recovery ping; second scan.

**42. Why save to a file instead of a database on the phone?**
Each result is appended the instant it completes, so losing the app costs at most the
reading in progress. The plan specified Room; a line-appended file gives the same
guarantee with none of the build complexity. It is a documented simplification.

**43. How do you know the parser is correct?**
Forty-two assertions on a laptop JVM plus nine JUnit tests, against the worked byte
sequence in the standard, little-endian ordering, unsigned bytes above 127, truncated
elements, absent elements and padded elements. All pass.

**44. What does the SELF CHECK button do?**
Reports Wi-Fi state, location permission, location services, nearby-devices permission,
gateway, whether the scan was accepted or throttled, how many APs are visible and how
many carry BSS Load — and names the blocker if there is one.

**45. Is the APK signed?**
Debug-signed, which is acceptable for coursework submission. It verifies under APK
Signature Scheme v2.

## E. Data and analysis (46–56)

**46. Why PostgreSQL rather than a spreadsheet?**
A full survey is around 160 measurements, each with beacon and neighbour rows attached.
That is past where a spreadsheet stays reliable, and the relational joins make the
analysis straightforward.

**47. What stops duplicate imports?**
Every measurement carries a UUID generated on the phone, with a unique constraint. A
re-import updates the row instead of inserting a second one. We tested this by importing
the same file twice.

**48. Why both Pearson and Spearman?**
Pearson assumes a straight-line relationship. Spearman only assumes the order is
consistent, so it survives non-linear relationships and outliers. Reporting both, with n
stated, is more honest than picking the flattering one.

**49. Your correlations are weak. Why?**
n is 6. At that size correlations are indicative at best. We show them with the sample
size stated rather than hiding them, and we do not draw conclusions from them.

**50. How is the quality score built?**
Each metric min-max normalised to 0–100 across all points, inverted where lower is
better, then weighted: 30% download, 20% upload, 25% loaded latency, 15% jitter, 10%
loss.

**51. Why those weights?**
Interactive responsiveness is weighted above raw throughput because that is what people
actually notice. They are a judgement call and we say so.

**52. What happens when a measurement fails?**
A transfer that times out scores zero for that component, because a failed transfer is a
result. A missing latency figure is genuinely no information, so the remaining weights
are renormalised. C9 scores 49 rather than 98 because of this.

**53. What is `latency_ratio` and who computes it?**
Loaded latency divided by idle latency. It is a generated column — PostgreSQL computes
and stores it automatically, so it can never drift out of sync with its inputs.

**54. What is the `source` column in `medium_capture_stats`?**
A constraint that only accepts 'simulated' or 'captured'. It makes it impossible to
insert simulation output without labelling it. The table is empty because the simulation
was cut.

**55. How do you handle a point with no BSS Load?**
The pipeline counts how many measurements carry it. Below a threshold it switches the
medium axis to the latency ratio and prints a notice; the dashboard shows a banner
explaining the substitution.

**56. Why is the time-of-day table empty?**
It needs at least two time windows. We have one. That is the single biggest gap in the
current dataset.

## F. Results (57–64)

**57. What is your headline finding?**
A1 and C5 have identical signal strength, −68 dBm, and differ by five times in
throughput: 19.2 against 3.6 Mbps. Signal strength does not explain performance here.

**58. What is the most surprising thing you found?**
C5, the control point, shows a 33.9× latency increase under load on a channel the access
point reports as 0.78% occupied. Empty air cannot queue, so the bottleneck is upstream
of the Wi-Fi entirely — the campus uplink.

**59. How do you know C5 is not just a bad spot?**
Because the AP's own beacon says the channel is empty, and the signal is decent at −68.
Both of the local explanations are ruled out by the data.

**60. What is wrong at C9?**
Weakest signal in the set at −76, lowest link rate at 43 Mbps, and both transfers failed
to complete in 15 seconds. That is a coverage problem, and it looks nothing like C5's.

**61. Why is C1's score zero?**
It had the worst download at 0.8 Mbps, 44% packet loss, and the upload failed outright.
It is also the least trustworthy row in the set, because its loaded latency came out
*lower* than its idle latency, which is physically backwards.

**62. Which points are capacity limited?**
A1, A9 and C5 — all three have adequate or marginal signal but a loaded/idle ratio above
5×.

**63. Does signal strength predict performance in your data?**
Only partly, and that is the point. A5 has the best signal at −51 dBm and gets 16.8
Mbps; A1 has worse signal at −68 and gets more, 19.2 Mbps. If signal explained
everything, that could not happen.

**64. What if signal *had* explained everything?**
That would be a legitimate result, not a failure. It would mean the building is coverage
limited, which is a finding, and the classification framework would have produced it.

## G. Limitations and honesty (65–70)

**65. What are the biggest weaknesses of this work?**
One time window, one repetition, six points, and throughput that is not confined to the
wireless hop. In that order.

**66. Your proposal promised BSS Load. What happened?**
Most access points here do not advertise it — we get it on 3 of 6 measurements. It is
optional in the standard. Section 3.1 of our plan listed the fallback indicators in
advance, and the loaded/idle latency ratio is the second of them. The classification
matrix works identically with queueing delay on the medium axis.

**67. Your proposal promised ns-3 simulation. Where is it?**
Cut on schedule grounds. It was the top item on our own documented cut list, because
dropping it costs the frame-level indicators and the causal argument and nothing else.
The survey, the app, the analysis and the maps are untouched by that decision.

**68. Why does that make it acceptable to drop?**
Because we wrote the cut order down before we needed it, and chose a component that
could be removed cleanly. A component you can drop without breaking anything else is one
you can afford to attempt.

**69. Is any of this data fabricated?**
No. Every row in the database came off the phone on 19 October. The raw export is kept
in `db/exports/` unmodified. During development we used generated sample data to build
the pipeline, and the dashboard carried a visible banner saying so; that banner is gone
now because the data is real.

**70. What is the one thing you would fix first?**
A second time window. The morning-versus-evening comparison at unchanged signal is the
strongest evidence this method can produce, and it is the only major piece the current
dataset is missing.

---

# PART 7 — IF SOMETHING BREAKS

| Problem | Fix |
|---|---|
| Dashboard is blank | `./run.sh` rebuilds the page |
| `connection refused` from psql | `./run.sh` starts the database for you |
| `password authentication failed` | you are hitting port 5432. `source .pgenv` first |
| App shows no Wi-Fi | press **SELF CHECK**. Usually Location services are off |
| App gateway box is blank | not on Wi-Fi, or type it from Settings → Wi-Fi → Gateway |
| App says CACHED results | scan limit hit, wait two minutes |
| Download fails mid-demo | no internet. Latency and beacon data still record fine |
| Import fails on a new file | check the point labels exist in `reference_points` |

**Worst case:** `dashboard/index.html` is a single self-contained file with the data
baked into it. It opens by double-click with no database, no server and no network. If
everything else fails, open that.

---

# PART 8 — THE THREE SENTENCES TO REMEMBER

1. **"A1 and C5 have the same signal strength and five times the difference in speed."**
   That is the project.

2. **"C5's channel is 0.78 percent busy and its latency still goes up thirty-four times
   under load, so the bottleneck is not the Wi-Fi."** That is the best thing in the data.

3. **"If a spot is congested, adding an access point on the same channel makes it
   worse."** That is the useful conclusion.
