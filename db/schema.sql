-- =====================================================================
--  Real-Time Network Quality Mapping and Connectivity Analysis
--  BCSE308P Computer Networks Lab
--  Schema per Implementation Plan Revision 2, Section 4.1
--
--  Rev 2 note: server_capture_stats is intentionally absent. There is no
--  server in the measurement path, so no tcpdump retransmission ratio.
-- =====================================================================

DROP TABLE IF EXISTS classifications        CASCADE;
DROP TABLE IF EXISTS medium_capture_stats   CASCADE;
DROP TABLE IF EXISTS neighbours             CASCADE;
DROP TABLE IF EXISTS bss_load               CASCADE;
DROP TABLE IF EXISTS measurements           CASCADE;
DROP TABLE IF EXISTS sessions               CASCADE;
DROP TABLE IF EXISTS reference_points       CASCADE;

-- ---------------------------------------------------------------------
-- The 25-30 marked spots on the survey grid. Populated in Phase 1.
-- ---------------------------------------------------------------------
CREATE TABLE reference_points (
    id            SERIAL PRIMARY KEY,
    label         TEXT NOT NULL UNIQUE,      -- 'C5' - the grid cell it sits in
    -- Grid position. This is the authoritative spatial reference: no floor-plan
    -- image is used, and the survey covers a single floor.
    -- grid_row is a letter (A, B, C ...), grid_col a number (1, 2 ...).
    grid_row      TEXT NOT NULL,
    grid_col      INT  NOT NULL,
    place_name    TEXT,                      -- 'corridor outside Lab 101'
    -- Render coordinates, derived from the grid by scripts/seed_points.py.
    -- Kept so the dashboard has a single, simple coordinate space to draw in.
    x_px          INT  NOT NULL,
    y_px          INT  NOT NULL,
    location_type TEXT,                      -- corridor | stairwell | lift_lobby | room_entrance | open_area
    is_control    BOOLEAN NOT NULL DEFAULT FALSE,  -- Rev 2: the fixed per-session control spot (4.3)
    notes         TEXT,
    UNIQUE (grid_row, grid_col)
);

COMMENT ON COLUMN reference_points.is_control IS
  'Exactly one point should be TRUE. Section 4.3: the fixed strong-signal spot measured at the start of every session to detect campus-uplink variation.';

-- ---------------------------------------------------------------------
-- One survey run: a building, a time window, a date.
-- ---------------------------------------------------------------------
CREATE TABLE sessions (
    id               SERIAL PRIMARY KEY,
    uuid             UUID NOT NULL UNIQUE,   -- generated on the phone
    -- four windows across one day gives a stronger load comparison than two
    window_label     TEXT NOT NULL CHECK (window_label IN
                        ('morning','afternoon','evening','night')),
    started_at       TIMESTAMPTZ NOT NULL,
    ended_at         TIMESTAMPTZ,
    surveyor         TEXT,
    -- Rev 2 Section 4.3: control measurement, taken at the fixed control spot
    -- at the start of every session. Without it, campus-uplink congestion
    -- cannot be distinguished from building congestion.
    control_dl_mbps  NUMERIC(8,2),
    control_rssi_dbm INT,
    notes            TEXT
);

-- ---------------------------------------------------------------------
-- One press of [RUN MEASUREMENT]. The 8-stage sequence of Section 4.2.
-- ---------------------------------------------------------------------
CREATE TABLE measurements (
    id                  SERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE,   -- phone-generated; makes import idempotent
    session_id          INT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    ref_point_id        INT NOT NULL REFERENCES reference_points(id),
    taken_at            TIMESTAMPTZ NOT NULL,
    repetition          INT NOT NULL DEFAULT 1,

    -- Stage 1-2: Wi-Fi context
    ssid                TEXT,
    bssid               TEXT,
    frequency_mhz       INT,
    channel             INT,
    band                TEXT CHECK (band IN ('2.4','5','6')),
    rssi_dbm            INT,
    tx_link_speed_mbps  INT,
    rx_link_speed_mbps  INT,

    -- Stage 4/6: throughput. Rev 2 = end-to-end, not hop-isolated.
    dl_mbps             NUMERIC(8,2),
    ul_mbps             NUMERIC(8,2),
    dl_target_host      TEXT,                  -- record it; the report must state the endpoint

    -- Stage 3/5/7: latency to the default gateway (wireless-hop confined)
    idle_latency_ms     NUMERIC(8,2),          -- median RTT, unloaded
    idle_latency_p95_ms NUMERIC(8,2),
    loaded_latency_ms   NUMERIC(8,2),          -- median RTT, during download
    latency_ratio       NUMERIC(8,3) GENERATED ALWAYS AS
                        (loaded_latency_ms / NULLIF(idle_latency_ms, 0)) STORED,
    jitter_ms           NUMERIC(8,2),          -- ping mdev
    packet_loss_pct     NUMERIC(5,2),
    recovery_latency_ms NUMERIC(8,2),          -- stage 7: did the link return to baseline?
    gateway_ip          TEXT,
    latency_method      TEXT CHECK (latency_method IN ('icmp','tcp_connect')),

    device_model        TEXT,
    android_sdk         INT,

    UNIQUE (session_id, ref_point_id, repetition)
);

-- ---------------------------------------------------------------------
-- BSS Load from beacon IE 11, one row per scan phase (pre-load, post-load).
-- This is the medium-level layer. Without it the project is degraded (3.1).
-- ---------------------------------------------------------------------
CREATE TABLE bss_load (
    id                 SERIAL PRIMARY KEY,
    measurement_id     INT NOT NULL REFERENCES measurements(id) ON DELETE CASCADE,
    phase              TEXT NOT NULL CHECK (phase IN ('pre','post')),
    bssid              TEXT NOT NULL,
    station_count      INT,
    channel_util_255   INT CHECK (channel_util_255 BETWEEN 0 AND 255),
    channel_util_pct   NUMERIC(5,2) GENERATED ALWAYS AS
                       (channel_util_255 * 100.0 / 255) STORED,
    admission_capacity INT,
    UNIQUE (measurement_id, phase, bssid)
);

-- ---------------------------------------------------------------------
-- Every BSSID visible at the point, for co-channel contention analysis.
-- ---------------------------------------------------------------------
CREATE TABLE neighbours (
    id             SERIAL PRIMARY KEY,
    measurement_id INT NOT NULL REFERENCES measurements(id) ON DELETE CASCADE,
    phase          TEXT NOT NULL DEFAULT 'pre' CHECK (phase IN ('pre','post')),
    bssid          TEXT NOT NULL,
    ssid           TEXT,
    channel        INT,
    frequency_mhz  INT,
    rssi_dbm       INT,
    is_serving     BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (measurement_id, phase, bssid)
);

-- ---------------------------------------------------------------------
-- Frame-level statistics. Populated by [TRACK S] ns-3 runs, and by
-- [TRACK A] monitor-mode captures if an adapter ever appears.
-- The source column is load-bearing: never report a row without it.
-- ---------------------------------------------------------------------
CREATE TABLE medium_capture_stats (
    id                    SERIAL PRIMARY KEY,
    source                TEXT NOT NULL CHECK (source IN ('simulated','captured')),
    scenario_label        TEXT,               -- e.g. 'sweep_contenders_n08'
    seed                  INT,
    sim_rssi_dbm          INT,                -- modelled RSSI, simulated rows
    sim_contenders        INT,                -- background stations, simulated rows
    session_id            INT REFERENCES sessions(id),          -- NULL for simulated
    ref_point_id          INT REFERENCES reference_points(id),  -- NULL for simulated
    window_start          TIMESTAMPTZ,
    window_end            TIMESTAMPTZ,
    throughput_mbps       NUMERIC(8,2),
    mean_delay_ms         NUMERIC(8,2),
    jitter_ms             NUMERIC(8,2),
    packet_loss_pct       NUMERIC(5,2),
    retry_ratio           NUMERIC(6,4),
    airtime_utilisation   NUMERIC(5,2),
    unique_stations       INT,
    median_datarate_mbps  NUMERIC(8,2),
    beacon_interval_sd_us NUMERIC(10,2)
);

COMMENT ON COLUMN medium_capture_stats.source IS
  'Never report a row from this table without this column. Section 1.3: every figure sourced from Track S is labelled simulated in its caption.';

-- ---------------------------------------------------------------------
-- Output of the analysis pipeline. One verdict per point per session.
-- ---------------------------------------------------------------------
CREATE TABLE classifications (
    id                SERIAL PRIMARY KEY,
    session_id        INT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    ref_point_id      INT NOT NULL REFERENCES reference_points(id),
    quality_score     NUMERIC(5,2),           -- on raw throughput
    quality_score_norm NUMERIC(5,2),          -- on control-normalised throughput (7.3)
    category          TEXT CHECK (category IN
                        ('coverage_limited','capacity_limited','unconstrained','mixed')),
    primary_cause     TEXT,
    recommendation    TEXT,
    UNIQUE (session_id, ref_point_id)
);

-- ---------------------------------------------------------------------
-- Indexes for the dashboard and analysis joins
-- ---------------------------------------------------------------------
CREATE INDEX idx_meas_session      ON measurements(session_id);
CREATE INDEX idx_meas_point        ON measurements(ref_point_id);
CREATE INDEX idx_meas_band         ON measurements(band);
CREATE INDEX idx_bss_meas          ON bss_load(measurement_id);
CREATE INDEX idx_neigh_meas        ON neighbours(measurement_id);
CREATE INDEX idx_neigh_channel     ON neighbours(channel);
CREATE INDEX idx_class_session     ON classifications(session_id);
CREATE INDEX idx_mcs_source        ON medium_capture_stats(source);
CREATE INDEX idx_mcs_scenario      ON medium_capture_stats(scenario_label);
