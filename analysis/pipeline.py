#!/usr/bin/env python3
"""
Turn measurements into findings.

  database -> join -> median per point/window -> correlate -> score -> classify
           -> write classifications back -> dashboard/results.json

The classification matrix is Section 7.2 of the plan: signal strength on one
axis, how busy the channel is on the other. That is the whole argument of the
project, since the two problems look identical to a user but need opposite fixes.
"""
import json, os, sys, warnings

MEDIUM_COL, MEDIUM_SRC = "channel_util_pct", "bss_load"
import pandas as pd
warnings.filterwarnings('ignore')
import psycopg2
from scipy import stats

RSSI_ADEQUATE, RSSI_MARGINAL = -67, -75
UTIL_LIGHT, UTIL_MODERATE = 30.0, 50.0
# Fallback when no access point advertises BSS Load (plan 3.1 and 7.2).
# Latency measured while the link is saturated, divided by latency when idle.
# Queueing delay rises sharply once a channel is contended, so this separates
# a busy medium from a weak signal without needing anything from the beacon.
RATIO_LIGHT, RATIO_MODERATE = 3.0, 5.0

WEIGHTS = {"dl": 0.30, "ul": 0.20, "loaded_latency": 0.25, "jitter": 0.15, "loss": 0.10}

RECOMMEND = {
    "coverage_limited":
        "Add an access point here, or move the existing one closer. Turning up "
        "transmit power would widen coverage but also widen the contention area.",
    "capacity_limited":
        "Do NOT add an access point on the same channel, that makes it worse. "
        "Re-channel, enable band steering, or add capacity on a non-overlapping channel.",
    "mixed":
        "Both signal and contention are marginal here. Investigate on site before "
        "spending anything.",
    "unconstrained":
        "Nothing to do.",
}


def connect(db):
    return psycopg2.connect(dbname=db,
                            host=os.environ.get("PGHOST", "/tmp"),
                            port=os.environ.get("PGPORT", "5433"))


def rssi_cat(r):
    if pd.isna(r): return "unknown"
    return "adequate" if r >= RSSI_ADEQUATE else "marginal" if r >= RSSI_MARGINAL else "weak"


def util_cat(u):
    if pd.isna(u): return "unknown"
    return "light" if u < UTIL_LIGHT else "moderate" if u <= UTIL_MODERATE else "contended"


def ratio_cat(x):
    """Same three bands, derived from loaded/idle latency instead of the beacon."""
    if pd.isna(x): return "unknown"
    return "light" if x < RATIO_LIGHT else "moderate" if x <= RATIO_MODERATE else "contended"


def classify(rssi, medium_value, medium_source="bss_load"):
    """Section 7.2. Returns (category, one-line cause)."""
    r = rssi_cat(rssi)
    u = util_cat(medium_value) if medium_source == "bss_load" else ratio_cat(medium_value)
    if r == "unknown" or u == "unknown":
        return "mixed", "not enough data"
    table = {
        ("adequate", "light"): ("unconstrained", "signal is fine and the channel is quiet"),
        ("adequate", "moderate"): ("mixed", "signal is fine, channel is starting to fill"),
        ("adequate", "contended"): ("capacity_limited", "signal is fine, the channel is crowded"),
        ("marginal", "light"): ("mixed", "signal is a bit weak but the channel is quiet"),
        ("marginal", "moderate"): ("mixed", "signal and channel are both middling"),
        ("marginal", "contended"): ("capacity_limited", "crowded channel is the bigger problem"),
        ("weak", "light"): ("coverage_limited", "too far from the access point"),
        ("weak", "moderate"): ("coverage_limited", "weak signal dominates"),
        ("weak", "contended"): ("mixed", "weak signal and a crowded channel together"),
    }
    return table[(r, u)]


def norm(series, invert=False):
    """Min-max to 0..100 across all points. Invert where lower is better."""
    s = series.astype(float)
    lo, hi = s.min(), s.max()
    if pd.isna(lo) or hi == lo:
        return pd.Series([50.0] * len(s), index=s.index)
    out = (s - lo) / (hi - lo) * 100
    return (100 - out) if invert else out


def main(db="wifi_survey", out="dashboard/results.json"):
    conn = connect(db)

    m = pd.read_sql("""
        SELECT m.*, r.label, r.place_name, r.grid_row, r.grid_col, r.is_control,
               s.window_label, s.control_dl_mbps
        FROM measurements m
        JOIN reference_points r ON r.id = m.ref_point_id
        JOIN sessions s         ON s.id = m.session_id
    """, conn)
    if m.empty:
        print("no measurements in the database", file=sys.stderr); return 1

    bl = pd.read_sql("""SELECT measurement_id, channel_util_pct, station_count
                        FROM bss_load WHERE phase='pre'""", conn)
    nb = pd.read_sql("""SELECT measurement_id, channel, is_serving FROM neighbours""", conn)

    m = m.merge(bl, left_on="id", right_on="measurement_id", how="left")

    # co-channel count: how many other APs share our serving channel
    serving = nb[nb.is_serving].set_index("measurement_id")["channel"].to_dict()
    cc = (nb.assign(sv=nb.measurement_id.map(serving))
            .query("channel == sv and not is_serving")
            .groupby("measurement_id").size().rename("cochannel_count"))
    m = m.merge(cc, left_on="id", right_index=True, how="left")
    m["cochannel_count"] = m["cochannel_count"].fillna(0)

    m["latency_ratio"] = m.loaded_latency_ms / m.idle_latency_ms.replace(0, pd.NA)

    # Does anything here actually advertise BSS Load? Week 0 Check 2 may have
    # failed, in which case the medium layer comes from queueing delay instead.
    global MEDIUM_COL, MEDIUM_SRC
    have_util = pd.to_numeric(m.get("channel_util_pct"), errors="coerce").notna().sum()
    if have_util >= max(4, 0.25 * len(m)):
        MEDIUM_COL, MEDIUM_SRC = "channel_util_pct", "bss_load"
    else:
        MEDIUM_COL, MEDIUM_SRC = "latency_ratio", "latency_ratio"
        print(f"\n  NOTE: only {have_util} of {len(m)} measurements carry BSS Load.")
        print( "        Falling back to the loaded/idle latency ratio as the medium")
        print( "        indicator, per plan sections 3.1 and 7.2.")
    # throughput relative to that session's control reading, so a slow campus
    # uplink on one evening does not get blamed on a survey point (plan 4.3)
    m["dl_vs_control"] = m.dl_mbps / m.control_dl_mbps.replace(0, pd.NA)

    numeric = ["rssi_dbm", "dl_mbps", "ul_mbps", "idle_latency_ms", "loaded_latency_ms",
               "latency_ratio", "jitter_ms", "packet_loss_pct", "channel_util_pct",
               "station_count", "cochannel_count", "tx_link_speed_mbps", "dl_vs_control"]
    for c in numeric:
        m[c] = pd.to_numeric(m[c], errors="coerce")

    # median across repetitions
    per = (m.groupby(["label", "place_name", "grid_row", "grid_col", "is_control", "window_label"])
             [numeric].median().reset_index())
    overall = (per.groupby(["label", "place_name", "grid_row", "grid_col", "is_control"])
                 [numeric].median().reset_index())

    # ---- quality score -------------------------------------------------
    overall["q_dl"]   = norm(overall.dl_mbps)
    overall["q_ul"]   = norm(overall.ul_mbps)
    overall["q_lat"]  = norm(overall.loaded_latency_ms, invert=True)
    overall["q_jit"]  = norm(overall.jitter_ms, invert=True)
    overall["q_loss"] = norm(overall.packet_loss_pct, invert=True)
    def weighted(row, parts):
        """Weighted score over whatever components exist.

        A timed-out download leaves dl_mbps missing. Dropping the whole point
        would be worse than scoring it on what did succeed, so the remaining
        weights are renormalised and the gap is recorded instead of hidden.
        """
        num = den = 0.0
        for col, w in parts:
            v = row.get(col)
            if v is not None and not pd.isna(v):
                num += w * v
                den += w
        return round(num / den, 1) if den > 0 else None

    base = [("q_dl", WEIGHTS["dl"]), ("q_ul", WEIGHTS["ul"]),
            ("q_lat", WEIGHTS["loaded_latency"]), ("q_jit", WEIGHTS["jitter"]),
            ("q_loss", WEIGHTS["loss"])]
    # An absent throughput number means the transfer did not finish inside the
    # time cap. That is a result, not a gap, so it scores zero. Excluding it
    # would let a point that failed to move any data at all score well on the
    # latency it did manage to measure.
    overall.loc[overall.dl_mbps.isna(), "q_dl"] = 0.0
    overall.loc[overall.ul_mbps.isna(), "q_ul"] = 0.0
    # A missing latency figure is genuinely no information, so it is left out
    # of the weighting rather than counted as bad.
    for c, src in (("q_lat", "loaded_latency_ms"), ("q_jit", "jitter_ms"),
                   ("q_loss", "packet_loss_pct")):
        overall.loc[overall[src].isna(), c] = float("nan")
    overall["quality_score"] = overall.apply(lambda r: weighted(r, base), axis=1)
    overall["missing_metrics"] = overall.apply(
        lambda r: ", ".join(n for n, c in (("download", "dl_mbps"), ("upload", "ul_mbps"),
                                           ("latency", "loaded_latency_ms"),
                                           ("jitter", "jitter_ms"), ("loss", "packet_loss_pct"))
                            if pd.isna(r[c])), axis=1)
    # same score computed on control-normalised throughput, as a robustness check
    overall["q_dl_n"] = norm(overall.dl_vs_control)
    overall.loc[overall.dl_vs_control.isna(), "q_dl_n"] = 0.0
    base_n = [("q_dl_n", WEIGHTS["dl"])] + base[1:]
    overall["quality_score_norm"] = overall.apply(lambda r: weighted(r, base_n), axis=1)

    wcats = per.apply(lambda r: classify(r.rssi_dbm, r[MEDIUM_COL], MEDIUM_SRC), axis=1)
    per["category"] = [c[0] for c in wcats]
    per["primary_cause"] = [c[1] for c in wcats]

    cats = overall.apply(lambda r: classify(r.rssi_dbm, r[MEDIUM_COL], MEDIUM_SRC), axis=1)
    overall["category"] = [c[0] for c in cats]
    overall["primary_cause"] = [c[1] for c in cats]

    # Operationally what matters is the worst the point ever gets, not its average.
    sev = {"unconstrained": 0, "mixed": 1, "coverage_limited": 2, "capacity_limited": 3}
    peak = (per.assign(_s=per.category.map(sev))
              .sort_values("_s").groupby("label").last()[["category", "primary_cause", "window_label"]])
    overall["peak_category"] = overall.label.map(peak.category)
    overall["peak_cause"] = overall.label.map(peak.primary_cause)
    overall["peak_window"] = overall.label.map(peak.window_label)
    overall["recommendation"] = overall.peak_category.map(RECOMMEND)

    # ---- correlations --------------------------------------------------
    indicators = ["rssi_dbm", "channel_util_pct", "station_count",
                  "cochannel_count", "tx_link_speed_mbps", "latency_ratio"]
    outcomes = ["dl_mbps", "ul_mbps", "loaded_latency_ms", "jitter_ms", "packet_loss_pct"]
    corr = []
    for i in indicators:
        for o in outcomes:
            d = per[[i, o]].dropna()
            if len(d) < 4:
                continue
            pr, pp = stats.pearsonr(d[i], d[o])
            sr, sp = stats.spearmanr(d[i], d[o])
            corr.append({"indicator": i, "outcome": o, "n": int(len(d)),
                         "pearson_r": round(float(pr), 3), "pearson_p": round(float(pp), 4),
                         "spearman_r": round(float(sr), 3), "spearman_p": round(float(sp), 4)})

    # ---- time of day effect at unchanged signal -------------------------
    # Walls do not move between 9am and 7pm. If throughput falls while RSSI
    # holds steady, the medium is the only thing left to explain it.
    deltas = []
    for label, g in per.groupby("label"):
        if len(g) < 2:
            continue
        best = g.loc[g.dl_mbps.idxmax()]
        worst = g.loc[g.dl_mbps.idxmin()]
        drop = (best.dl_mbps - worst.dl_mbps) / best.dl_mbps * 100 if best.dl_mbps else 0
        deltas.append({
            "label": label,
            "best_window": best.window_label, "best_dl": round(float(best.dl_mbps), 1),
            "best_rssi": int(best.rssi_dbm),
            "best_util": None if pd.isna(best.channel_util_pct) else round(float(best.channel_util_pct), 1),
            "best_ratio": None if pd.isna(best.latency_ratio) else round(float(best.latency_ratio), 2),
            "worst_window": worst.window_label, "worst_dl": round(float(worst.dl_mbps), 1),
            "worst_rssi": int(worst.rssi_dbm),
            "worst_util": None if pd.isna(worst.channel_util_pct) else round(float(worst.channel_util_pct), 1),
            "worst_ratio": None if pd.isna(worst.latency_ratio) else round(float(worst.latency_ratio), 2),
            "drop_pct": round(float(drop), 1),
            "rssi_change_db": int(abs(best.rssi_dbm - worst.rssi_dbm)),
            "util_change_pct": None if pd.isna(worst.channel_util_pct) or pd.isna(best.channel_util_pct)
                               else round(float(worst.channel_util_pct - best.channel_util_pct), 1),
            "ratio_change": None if pd.isna(worst.latency_ratio) or pd.isna(best.latency_ratio)
                            else round(float(worst.latency_ratio - best.latency_ratio), 2),
            # the interesting case: speed collapsed but the signal did not move
            "signal_stable": bool(abs(best.rssi_dbm - worst.rssi_dbm) <= 3),
        })
    deltas.sort(key=lambda d: -d["drop_pct"])

    # ---- write classifications back ------------------------------------
    cur = conn.cursor()
    cur.execute("SELECT label, id FROM reference_points")
    pid = dict(cur.fetchall())
    cur.execute("SELECT id FROM sessions ORDER BY id")
    sids = [r[0] for r in cur.fetchall()]
    cur.execute("DELETE FROM classifications")
    for _, r in overall.iterrows():
        for sid in sids:
            cur.execute("""INSERT INTO classifications
                (session_id, ref_point_id, quality_score, quality_score_norm,
                 category, primary_cause, recommendation)
                VALUES (%s,%s,%s,%s,%s,%s,%s)
                ON CONFLICT (session_id, ref_point_id) DO UPDATE
                  SET quality_score=EXCLUDED.quality_score, category=EXCLUDED.category""",
                (sid, pid[r.label],
                 None if pd.isna(r.quality_score) else float(r.quality_score),
                 None if pd.isna(r.quality_score_norm) else float(r.quality_score_norm),
                 r.peak_category, r.peak_cause, r.recommendation))
    conn.commit()

    # ---- dashboard payload ---------------------------------------------
    def clean(df):
        return json.loads(df.where(pd.notna(df), None).to_json(orient="records"))

    windows = sorted(per.window_label.unique(),
                     key=lambda w: ["morning", "afternoon", "evening", "night"].index(w)
                     if w in ["morning", "afternoon", "evening", "night"] else 9)

    sess = pd.read_sql("SELECT window_label, control_dl_mbps, control_rssi_dbm "
                       "FROM sessions ORDER BY id", conn)

    payload = {
        "generated_at": pd.Timestamp.now().isoformat(timespec="seconds"),
        "sample_data": bool((m.device_model.astype(str).str.contains("SAMPLE")).any()),
        "windows": windows,
        "grid": {"rows": 6, "cols": 10},
        "points": clean(overall),
        "per_window": clean(per),
        "correlations": corr,
        "time_of_day": deltas,
        "controls": clean(sess),
        "medium_source": MEDIUM_SRC,
        "medium_column": MEDIUM_COL,
        "thresholds": {"rssi_adequate": RSSI_ADEQUATE, "rssi_marginal": RSSI_MARGINAL,
                       "util_light": UTIL_LIGHT, "util_moderate": UTIL_MODERATE,
                       "ratio_light": RATIO_LIGHT, "ratio_moderate": RATIO_MODERATE},
        "weights": WEIGHTS,
        "counts": {"measurements": int(len(m)), "points": int(len(overall)),
                   "windows": len(windows)},
    }
    os.makedirs(os.path.dirname(out), exist_ok=True)
    json.dump(payload, open(out, "w"), indent=1)
    # Build a single self-contained page with the data inlined, so it opens by
    # double-click from anywhere. No web server, no relative script to resolve,
    # nothing to break when the file gets emailed or copied onto a pen drive.
    ddir = os.path.dirname(out)
    tpl = os.path.join(ddir, "template.html")
    if os.path.exists(tpl):
        html = open(tpl).read().replace(
            "/*__RESULTS__*/", "window.RESULTS = " + json.dumps(payload) + ";")
        with open(os.path.join(ddir, "index.html"), "w") as f:
            f.write(html)

    # ---- console summary -----------------------------------------------
    print(f"\n  {len(m)} measurements, {len(overall)} points, {len(windows)} windows")
    print(f"  medium indicator: {'BSS Load channel utilisation' if MEDIUM_SRC=='bss_load' else 'loaded/idle latency ratio (BSS Load unavailable)'}")
    print(f"\n  CLASSIFICATION AT EACH POINT'S WORST WINDOW")
    for cat, n in overall.peak_category.value_counts().items():
        pts = ", ".join(sorted(overall[overall.peak_category == cat].label))
        print(f"    {cat:18s} {n:2d}   {pts}")
    print(f"\n  HOW CATEGORIES SHIFT BY TIME OF DAY")
    piv = per.pivot_table(index="window_label", columns="category",
                          values="label", aggfunc="count").fillna(0).astype(int)
    for w in windows:
        if w in piv.index:
            bits = "  ".join(f"{c}={piv.loc[w, c]}" for c in piv.columns if piv.loc[w, c])
            print(f"    {w:10s} {bits}")
    print(f"\n  WORST 5 POINTS BY QUALITY SCORE")
    for _, r in overall.dropna(subset=["quality_score"]).nsmallest(6, "quality_score").iterrows():
        mv = r[MEDIUM_COL]
        mtxt = f"util {mv:4.0f}%" if MEDIUM_SRC == "bss_load" else f"ratio {mv:4.1f}x"
        print(f"    {r.label:4s} {r.quality_score:5.1f}  {r.category:18s} "
              f"rssi {r.rssi_dbm:>4.0f}dBm  {mtxt}  "
              f"dl {'   n/a' if pd.isna(r.dl_mbps) else format(r.dl_mbps, '6.1f')}Mbps"
              + (f"   [failed: {r.missing_metrics}]" if r.missing_metrics else ""))
    stable = [d for d in deltas if d["signal_stable"] and d["drop_pct"] > 20]
    if stable:
        print(f"\n  SPEED COLLAPSED WHILE SIGNAL HELD STEADY  <- the capacity finding")
        for d in stable[:5]:
            print(f"    {d['label']:4s} {d['best_dl']:5.1f} -> {d['worst_dl']:5.1f} Mbps "
                  f"({d['drop_pct']:4.0f}% down, {d['best_window']} to {d['worst_window']})  "
                  f"RSSI moved {d['rssi_change_db']} dB, " +
                  (f"channel use +{d['util_change_pct']:.0f}%" if d.get('util_change_pct') is not None
                   else f"latency ratio +{d.get('ratio_change', 0):.1f}x"))
    print(f"\n  STRONGEST CORRELATIONS WITH DOWNLOAD SPEED")
    for c in sorted([c for c in corr if c["outcome"] == "dl_mbps"],
                    key=lambda c: -abs(c["pearson_r"]))[:4]:
        print(f"    {c['indicator']:20s} pearson {c['pearson_r']:+.3f}  "
              f"spearman {c['spearman_r']:+.3f}  (n={c['n']})")
    print(f"\n  wrote {out}")
    conn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main(os.environ.get("PGDATABASE", "wifi_survey")))
