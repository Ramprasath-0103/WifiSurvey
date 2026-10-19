#!/usr/bin/env python3
"""
Phase 1 — load reference points into PostgreSQL.

Input is the JSON exported by tools/grid.html. This script is the ONLY
supported path into reference_points, because it validates the things that
quietly ruin a survey later:

  * labels unique                 -> otherwise measurements join to the wrong point
  * exactly one control point     -> Plan 4.3; without it a session is undefendable
  * 25-30 points                  -> Plan Phase 1 done-when test
  * grid cells valid and unique   -> two points in one cell is always a mistake
  * known location_type values    -> keeps the dashboard filters honest

Usage:
    python3 scripts/seed_points.py db/reference_points.json [--db wifi_survey] [--dry-run]
"""
import argparse, json, subprocess, sys

VALID_TYPES = {"corridor", "stairwell", "lift_lobby", "room_entrance", "open_area"}
MIN_POINTS, MAX_POINTS = 25, 30
ROWL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
CELL = 80        # px per grid step; render coords are derived, never hand-entered


def derive_xy(p):
    """Grid cell -> render pixel coords. The dashboard draws in this space."""
    return p["grid_col"] * CELL, (ROWL.index(p["grid_row"]) + 1) * CELL


def validate(points, strict=True):
    """Return (errors, warnings). Errors block the load; warnings do not."""
    errors, warnings = [], []

    if not points:
        return ["no points in file"], []

    labels = [p.get("label") for p in points]
    dupes = {l for l in labels if labels.count(l) > 1}
    if dupes:
        errors.append(f"duplicate labels: {sorted(dupes)}")
    if any(not l for l in labels):
        errors.append("one or more points have an empty label")

    controls = [p["label"] for p in points if p.get("is_control")]
    if len(controls) == 0:
        errors.append("no control point set (Plan 4.3 requires exactly one)")
    elif len(controls) > 1:
        errors.append(f"{len(controls)} control points set, need exactly one: {controls}")

    for p in points:
        lbl = p.get("label", "?")
        for k in ("grid_row", "grid_col"):
            if p.get(k) is None:
                errors.append(f"{lbl}: missing '{k}'")
        r = p.get("grid_row")
        if r is not None and (not isinstance(r, str) or r not in ROWL):
            errors.append(f"{lbl}: grid_row must be a single letter A-Z, got {r!r}")
        c = p.get("grid_col")
        if c is not None and (not isinstance(c, int) or c < 1):
            errors.append(f"{lbl}: grid_col must be a positive integer, got {c!r}")
        t = p.get("location_type")
        if t and t not in VALID_TYPES:
            errors.append(f"{lbl}: unknown location_type '{t}'")

    cells = [(p.get("grid_row"), p.get("grid_col")) for p in points]
    occupied = {c for c in cells if cells.count(c) > 1}
    if occupied:
        errors.append("two points share a grid cell: " +
                      ", ".join(f"{c[0]}{c[1]}" for c in sorted(occupied, key=str)))

    n = len(points)
    if not (MIN_POINTS <= n <= MAX_POINTS):
        msg = f"{n} points; Plan Phase 1 asks for {MIN_POINTS}-{MAX_POINTS}"
        (errors if strict else warnings).append(msg)

    return errors, warnings


def to_sql(points):
    q = lambda v: "NULL" if v in (None, "") else "'" + str(v).replace("'", "''") + "'"
    rows = []
    for p in points:
        x, y = derive_xy(p)
        rows.append("  ({},{},{},{},{},{},{},{})".format(
            q(p["label"]), q(p["grid_row"]), int(p["grid_col"]),
            q(p.get("place_name")), x, y,
            q(p.get("location_type") or "corridor"),
            "TRUE" if p.get("is_control") else "FALSE"))
    return ("BEGIN;\nDELETE FROM reference_points;\n"
            "INSERT INTO reference_points "
            "(label,grid_row,grid_col,place_name,x_px,y_px,location_type,is_control)"
            " VALUES\n" + ",\n".join(rows) + ";\nCOMMIT;\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("json_file")
    ap.add_argument("--db", default="wifi_survey")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--allow-any-count", action="store_true",
                    help="downgrade the 25-30 point rule to a warning (pilot use)")
    a = ap.parse_args()

    doc = json.load(open(a.json_file))
    points = doc["points"] if isinstance(doc, dict) else doc

    errors, warnings = validate(points, strict=not a.allow_any_count)
    for w in warnings:
        print(f"  warning: {w}")
    if errors:
        print("VALIDATION FAILED:", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        return 1

    sql = to_sql(points)
    if a.dry_run:
        print(sql)
        return 0

    r = subprocess.run(["psql", "-q", "-v", "ON_ERROR_STOP=1", "-d", a.db],
                       input=sql, text=True, capture_output=True)
    if r.returncode != 0:
        print(r.stderr, file=sys.stderr)
        return 1

    ctl = next(p["label"] for p in points if p.get("is_control"))
    print(f"loaded {len(points)} reference points into '{a.db}'  (control point: {ctl})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
