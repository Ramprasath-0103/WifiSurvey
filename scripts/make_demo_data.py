#!/usr/bin/env python3
"""
Generate a realistic SAMPLE survey, in exactly the format the phone exports.

Why this exists: so the whole laptop pipeline (import, analysis, dashboard)
can be built and demonstrated before the real walk happens. Replace the output
with your real export tomorrow and every downstream step is identical.

The numbers are modelled, not measured. They are produced from a simple
physical story:
  * signal falls with distance from the serving access point
  * the PHY rate the link can negotiate falls with signal
  * actual throughput is that rate shared with everyone else on the channel
  * latency under load rises sharply as the channel fills (bufferbloat)

That gives a building with both kinds of problem in it, which is the point:
some places are slow because they are far away, others are slow because they
are busy, and signal strength alone cannot tell them apart.
"""
import argparse, json, random, uuid
from datetime import datetime, timedelta

random.seed(20261018)      # reproducible

# label -> (rssi at the point, which AP serves it)
POINTS = {
    "C5":  (-42, "AP1"),   # control: right under the busy AP
    "A3":  (-48, "AP1"),
    "C3":  (-51, "AP1"),
    "A5":  (-54, "AP1"),
    "A1":  (-57, "AP1"),
    "B6":  (-60, "AP1"),
    "C1":  (-63, "AP1"),
    "C7":  (-59, "AP1"),
    "C10": (-71, "AP1"),
    "D5":  (-66, "AP2"),
    "E6":  (-77, "AP2"),
    "E10": (-83, "AP2"),
}

# AP1 serves the teaching side and gets hammered in the evening.
# AP2 covers the quiet end: never busy, but the far points are out of range.
UTIL = {
    "AP1": {"morning": 52, "afternoon": 41, "evening": 74, "night": 14},
    "AP2": {"morning": 21, "afternoon": 17, "evening": 29, "night":  7},
}
STATIONS = {
    "AP1": {"morning": 24, "afternoon": 18, "evening": 37, "night": 5},
    "AP2": {"morning":  9, "afternoon":  7, "evening": 12, "night": 2},
}
BSSID = {"AP1": "a4:2b:8c:11:04:f0", "AP2": "a4:2b:8c:11:09:3d"}
CHAN  = {"AP1": 36, "AP2": 44}
START = {"morning": 9, "afternoon": 13, "evening": 19, "night": 23}


def phy_rate(rssi):
    """Negotiated link rate in Mbps. Rate adaptation steps down as signal drops."""
    for cut, rate in ((-45, 433), (-52, 390), (-58, 300), (-64, 200),
                      (-70, 130), (-76, 72), (-82, 29)):
        if rssi >= cut:
            return rate
    return 12


def measure(point, rssi, ap, window, rep, session_uuid, taken, bss=True):
    util = UTIL[ap][window] + random.uniform(-3, 3)
    util = max(2, min(96, util))
    rate = phy_rate(rssi)

    # Airtime left for us, times protocol efficiency. This is where a strong
    # signal still ends up slow: the channel is simply occupied.
    share = max(0.05, 1 - util / 100.0)
    dl = rate * share * 0.52 * random.uniform(0.9, 1.1)
    ul = dl * random.uniform(0.35, 0.5)

    idle = 7 + (abs(rssi) - 40) * 0.32 + random.uniform(-1.2, 1.8)
    loaded = idle * (1 + (util / 100.0) ** 2 * 11) * random.uniform(0.9, 1.15)
    jitter = 1.1 + (util / 100.0) * 7 + max(0, (abs(rssi) - 65)) * 0.22
    loss = max(0.0, (abs(rssi) - 70) * 0.35 + (util - 60) * 0.06) * random.uniform(0.6, 1.4)

    neighbours = []
    for other, orssi in ((ap, rssi), ("AP2" if ap == "AP1" else "AP1", rssi - random.randint(8, 22))):
        neighbours.append({
            "bssid": BSSID[other], "ssid": "VIT-WiFi",
            "channel": CHAN[other], "frequency_mhz": 5000 + CHAN[other] * 5,
            "rssi_dbm": int(orssi), "is_serving": other == ap,
            "bss_load": ({
                "station_count": STATIONS[other][window],
                "channel_util_255": int(UTIL[other][window] * 255 / 100),
                "channel_util_pct": round(UTIL[other][window], 2),
                "admission_capacity": 15625,
            } if bss else None),
        })
    # a couple of unrelated networks, as any real scan would see
    for i in range(random.randint(2, 5)):
        neighbours.append({
            "bssid": f"de:ad:be:ef:{i:02x}:{random.randint(0,255):02x}",
            "ssid": random.choice(["eduroam", "VIT-Guest", "hostel-wifi", None]),
            "channel": random.choice([1, 6, 11, 36, 44, 149]),
            "frequency_mhz": 2412, "rssi_dbm": random.randint(-90, -70),
            "is_serving": False, "bss_load": None,
        })

    serving_load = neighbours[0]["bss_load"]
    if serving_load:
        post_load = dict(serving_load)
        post_load["channel_util_255"] = min(255, int(serving_load["channel_util_255"] * 1.06))
        post_load["channel_util_pct"] = round(post_load["channel_util_255"] * 100 / 255, 2)
    else:
        post_load = None

    return {
        "uuid": str(uuid.uuid4()),
        "session_uuid": session_uuid,
        "window_label": window,
        "point_label": point,
        "repetition": rep,
        "taken_at": taken.strftime("%Y-%m-%dT%H:%M:%S+05:30"),
        "is_control": point == "C5",
        "ssid": "VIT-WiFi", "bssid": BSSID[ap],
        "frequency_mhz": 5000 + CHAN[ap] * 5, "channel": CHAN[ap], "band": "5",
        "rssi_dbm": int(rssi + random.uniform(-1.5, 1.5)),
        "tx_link_speed_mbps": rate, "rx_link_speed_mbps": rate,
        "dl_mbps": round(dl, 2), "ul_mbps": round(ul, 2),
        "dl_target_host": "speed.cloudflare.com",
        "idle_latency_ms": round(idle, 2),
        "idle_latency_p95_ms": round(idle * random.uniform(1.4, 2.2), 2),
        "loaded_latency_ms": round(loaded, 2),
        "recovery_latency_ms": round(idle * random.uniform(1.0, 1.2), 2),
        "jitter_ms": round(jitter, 2),
        "packet_loss_pct": round(loss, 2),
        "gateway_ip": "10.1.0.1", "latency_method": "icmp",
        "bss_load_pre": serving_load, "bss_load_post": post_load,
        "neighbours": neighbours,
        "device_model": "SAMPLE DATA - replace with real export",
        "android_sdk": 34,
        "note": None,
    }


def main(out="db/demo_export.json", reps=2, bss=True):
    rows = []
    day = datetime(2026, 10, 18)
    for window, hour in START.items():
        sess = str(uuid.uuid4())
        t = day.replace(hour=hour, minute=5)
        # control point first, every session, as the protocol requires
        order = ["C5"] + [p for p in POINTS if p != "C5"]
        for point in order:
            rssi, ap = POINTS[point]
            for rep in range(1, reps + 1):
                rows.append(measure(point, rssi, ap, window, rep, sess, t, bss))
                t += timedelta(minutes=2, seconds=30)
    json.dump({"exported_at": datetime.now().isoformat(),
               "measurement_count": len(rows),
               "generated_by": "scripts/make_demo_data.py - SAMPLE DATA, not measured",
               "measurements": rows}, open(out, "w"), indent=1)
    print(f"wrote {out}: {len(rows)} measurements "
          f"({len(POINTS)} points x {len(START)} windows x {reps} reps)"
          + ("" if bss else "  [no BSS Load - matches an AP that does not advertise it]"))


if __name__ == "__main__":
    ap_ = argparse.ArgumentParser()
    ap_.add_argument("--no-bss-load", action="store_true",
                     help="omit the BSS Load element, as most consumer APs do")
    ap_.add_argument("--out", default="db/demo_export.json")
    a_ = ap_.parse_args()
    main(a_.out, bss=not a_.no_bss_load)
