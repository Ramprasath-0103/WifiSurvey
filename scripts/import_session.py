#!/usr/bin/env python3
"""
Load a survey export from the phone into PostgreSQL.

Input is the JSON the Android app writes via EXPORT TO DOWNLOADS:
    { "exported_at": ..., "measurement_count": N, "measurements": [ ... ] }

Safe to run more than once on the same file. Measurements are keyed on the
UUID the phone generated, so a re-import updates rows instead of duplicating
them. That matters because you will export more than once during a day.

Usage:
    python3 scripts/import_session.py exports/wifi_survey_20261018_2130.json
    python3 scripts/import_session.py file.json --places db/my_places.json
"""
import argparse, json, os, sys, uuid as _uuid
import psycopg2
import psycopg2.extras


def connect(db):
    return psycopg2.connect(
        dbname=db,
        host=os.environ.get("PGHOST", "/tmp"),
        port=os.environ.get("PGPORT", "5433"),
    )


def upsert_session(cur, uuid, window, started_at):
    cur.execute("""
        INSERT INTO sessions (uuid, window_label, started_at)
        VALUES (%s, %s, %s)
        ON CONFLICT (uuid) DO UPDATE SET window_label = EXCLUDED.window_label
        RETURNING id
    """, (uuid, window, started_at))
    return cur.fetchone()[0]


# The phone omits a key entirely when a stage failed (a timed-out download has
# no dl_mbps at all), so every optional field needs a default before insert.
MEASUREMENT_FIELDS = (
    "ssid bssid frequency_mhz channel band rssi_dbm tx_link_speed_mbps rx_link_speed_mbps "
    "dl_mbps ul_mbps dl_target_host idle_latency_ms idle_latency_p95_ms loaded_latency_ms "
    "jitter_ms packet_loss_pct recovery_latency_ms gateway_ip latency_method "
    "device_model android_sdk repetition note"
).split()


def normalise(r):
    """Fill in every optional field the insert expects, defaulting to None."""
    out = {k: None for k in MEASUREMENT_FIELDS}
    out.update(r)
    if out.get("repetition") is None:
        out["repetition"] = 1
    # -1 / -127 are the phone's "not connected" sentinels, not real readings
    if out.get("rssi_dbm") in (-127, 0):
        out["rssi_dbm"] = None
    for k in ("frequency_mhz", "channel", "tx_link_speed_mbps", "rx_link_speed_mbps"):
        if out.get(k) is not None and out[k] < 0:
            out[k] = None
    return out


def point_ids(cur):
    cur.execute("SELECT label, id FROM reference_points")
    return dict(cur.fetchall())


def load(path, db, places_path=None, verbose=True, only_window=None, since=None):
    doc = json.load(open(path))
    rows = doc["measurements"] if isinstance(doc, dict) else doc
    if only_window:
        before = len(rows)
        rows = [r for r in rows if r.get("window_label") == only_window]
        if verbose:
            print(f"  filtered to window '{only_window}': {len(rows)} of {before}")
    if since:
        before = len(rows)
        rows = [r for r in rows if str(r.get("taken_at", ""))[:10] >= since]
        if verbose:
            print(f"  filtered to on/after {since}: {len(rows)} of {before}")

    # The app always writes repetition 1, so visiting a point twice in one
    # session collides with UNIQUE(session, point, repetition). Number repeat
    # visits in the order they were taken rather than dropping either reading.
    seen = {}
    for r in sorted(rows, key=lambda x: x.get("taken_at", "")):
        key = (r.get("session_uuid"), r.get("window_label"), r.get("point_label"))
        seen[key] = seen.get(key, 0) + 1
        if seen[key] > 1:
            r["repetition"] = seen[key]
            if verbose:
                print(f"  {r['point_label']} visited {seen[key]}x this session "
                      f"-> recorded as repetition {seen[key]}")
    if not rows:
        print("no measurements in file", file=sys.stderr)
        return 1

    conn = connect(db)
    cur = conn.cursor()

    # optional: rename the placeholder place descriptions to the real ones
    if places_path:
        places = json.load(open(places_path))
        for label, place in places.items():
            cur.execute("UPDATE reference_points SET place_name=%s WHERE label=%s",
                        (place, label))
        if verbose:
            print(f"  renamed {len(places)} place descriptions")

    pts = point_ids(cur)
    unknown = sorted({r["point_label"] for r in rows} - set(pts))
    if unknown:
        print(f"ERROR: these point labels are not in reference_points: {unknown}", file=sys.stderr)
        print("       add them with tools/grid.html + scripts/seed_points.py first", file=sys.stderr)
        return 1

    sessions, imported, skipped = {}, 0, 0

    for r in rows:
        # One app launch can cover more than one time window, so a session is
        # keyed on (app session, window). Derive a stable UUID for that pair so
        # re-importing the same file lands on the same session row.
        skey = (r["session_uuid"], r["window_label"])
        if skey not in sessions:
            derived = str(_uuid.uuid5(_uuid.NAMESPACE_URL,
                                      f'{r["session_uuid"]}:{r["window_label"]}'))
            sessions[skey] = upsert_session(cur, derived, r["window_label"], r["taken_at"])
        sid = sessions[skey]

        rn = normalise(r)
        cur.execute("""
            INSERT INTO measurements (
                uuid, session_id, ref_point_id, taken_at, repetition,
                ssid, bssid, frequency_mhz, channel, band, rssi_dbm,
                tx_link_speed_mbps, rx_link_speed_mbps,
                dl_mbps, ul_mbps, dl_target_host,
                idle_latency_ms, idle_latency_p95_ms, loaded_latency_ms,
                jitter_ms, packet_loss_pct, recovery_latency_ms,
                gateway_ip, latency_method, device_model, android_sdk, note)
            VALUES (%(uuid)s, %(sid)s, %(pid)s, %(taken_at)s, %(repetition)s,
                %(ssid)s, %(bssid)s, %(frequency_mhz)s, %(channel)s, %(band)s, %(rssi_dbm)s,
                %(tx_link_speed_mbps)s, %(rx_link_speed_mbps)s,
                %(dl_mbps)s, %(ul_mbps)s, %(dl_target_host)s,
                %(idle_latency_ms)s, %(idle_latency_p95_ms)s, %(loaded_latency_ms)s,
                %(jitter_ms)s, %(packet_loss_pct)s, %(recovery_latency_ms)s,
                %(gateway_ip)s, %(latency_method)s, %(device_model)s, %(android_sdk)s, %(note)s)
            ON CONFLICT (uuid) DO UPDATE SET
                dl_mbps = EXCLUDED.dl_mbps, ul_mbps = EXCLUDED.ul_mbps,
                idle_latency_ms = EXCLUDED.idle_latency_ms,
                loaded_latency_ms = EXCLUDED.loaded_latency_ms,
                rssi_dbm = EXCLUDED.rssi_dbm
            RETURNING id
        """, dict(rn, uuid=r["uuid"], taken_at=r["taken_at"],
                  sid=sid, pid=pts[r["point_label"]]))
        mid = cur.fetchone()[0]

        cur.execute("DELETE FROM bss_load WHERE measurement_id=%s", (mid,))
        for phase in ("pre", "post"):
            b = r.get(f"bss_load_{phase}")
            if b and r.get("bssid"):
                cur.execute("""
                    INSERT INTO bss_load
                      (measurement_id, phase, bssid, station_count, channel_util_255, admission_capacity)
                    VALUES (%s,%s,%s,%s,%s,%s)
                """, (mid, phase, r["bssid"], b.get("station_count"),
                      b.get("channel_util_255"), b.get("admission_capacity")))

        cur.execute("DELETE FROM neighbours WHERE measurement_id=%s", (mid,))
        seen = set()
        for n in r.get("neighbours", []):
            if n["bssid"] in seen:
                continue
            seen.add(n["bssid"])
            cur.execute("""
                INSERT INTO neighbours
                  (measurement_id, phase, bssid, ssid, channel, frequency_mhz, rssi_dbm, is_serving)
                VALUES (%s,'pre',%s,%s,%s,%s,%s,%s)
            """, (mid, n["bssid"], n.get("ssid"), n.get("channel"),
                  n.get("frequency_mhz"), n.get("rssi_dbm"), n.get("is_serving", False)))
        imported += 1

    # Each session's control reading, used later to tell a congested campus
    # uplink apart from a genuinely bad survey point (plan 4.3).
    cur.execute("""
        UPDATE sessions s SET
          control_dl_mbps  = c.dl, control_rssi_dbm = c.rssi
        FROM (
          SELECT m.session_id, AVG(m.dl_mbps) dl, AVG(m.rssi_dbm)::int rssi
          FROM measurements m JOIN reference_points r ON r.id = m.ref_point_id
          WHERE r.is_control GROUP BY m.session_id
        ) c WHERE c.session_id = s.id
    """)
    conn.commit()

    if verbose:
        cur.execute("SELECT window_label, count(*) FROM sessions s "
                    "JOIN measurements m ON m.session_id=s.id GROUP BY 1 ORDER BY 1")
        print(f"  imported {imported} measurements")
        for w, c in cur.fetchall():
            print(f"    {w:10s} {c:3d}")
        cur.execute("SELECT count(*) FROM sessions WHERE control_dl_mbps IS NULL")
        missing = cur.fetchone()[0]
        if missing:
            print(f"  warning: {missing} session(s) have no control-point reading")
    cur.close(); conn.close()
    return 0


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("json_file")
    ap.add_argument("--db", default=os.environ.get("PGDATABASE", "wifi_survey"))
    ap.add_argument("--places", help="optional JSON mapping label -> real place name")
    ap.add_argument("--window", help="import only this time window (morning/afternoon/evening/night)")
    ap.add_argument("--since", help="ignore measurements taken before this date (YYYY-MM-DD)")
    a = ap.parse_args()
    sys.exit(load(a.json_file, a.db, a.places, only_window=a.window, since=a.since))
