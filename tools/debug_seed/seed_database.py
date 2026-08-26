#!/usr/bin/env python3
"""Seeds a PrescriptionHub Room database. Used by the Gradle `seedDebugDatabase` action.

Usage:
    python3 seed_database.py --db /path/to/prescription_database
    python3 seed_database.py --db /path/to/prescription_database --today 2026-08-25
"""
import argparse
import random
import sqlite3
import sys
from datetime import date, datetime, timedelta

REQUIRED_TABLES = {"prescriptions", "prescription_snapshots", "dose_intake_records"}

# Trans pride flag palette
PINK = 0xFFF5A9B8
BLUE = 0xFF5BCEFA
PURPLE = 0xFF9C27B0
AMBER = 0xFFFFC107
GRAY = 0xFF9E9E9E


def dt(d, t="00:00:00"):
    return f"{d.isoformat()}T{t}"


def is_scheduled(d, sched_type, start_date, every_x):
    if sched_type == "DAILY":
        return True
    if sched_type == "EVERY_X_DAYS":
        if not every_x or every_x <= 0:
            return False
        delta = (d - start_date).days
        return delta >= 0 and delta % every_x == 0
    return False


def seed(db_path, today):
    yesterday = today - timedelta(days=1)

    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    existing_tables = {
        row[0]
        for row in cur.execute(
            "SELECT name FROM sqlite_master WHERE type='table'"
        ).fetchall()
    }
    missing = REQUIRED_TABLES - existing_tables
    if missing:
        conn.close()
        raise SystemExit(
            f"Database at {db_path} is missing table(s) {sorted(missing)}. "
            "Launch the debug app at least once first so Room can create its "
            "schema, then re-run this script."
        )

    # Idempotent: wipe only the tables we seed, leave everything else (e.g.
    # room_master_table, android_metadata) untouched.
    cur.execute("DELETE FROM dose_intake_records")
    cur.execute("DELETE FROM prescription_snapshots")
    cur.execute("DELETE FROM prescriptions")
    cur.execute(
        "DELETE FROM sqlite_sequence WHERE name IN "
        "('prescriptions','prescription_snapshots','dose_intake_records')"
    )

    hrt_start = today - timedelta(days=328)          # ~10.8 months of history
    dose_increase = today - timedelta(days=177)       # ~5.8 months ago
    progesterone_start = today - timedelta(days=116)  # ~3.8 months ago
    finasteride_end = hrt_start + timedelta(days=75)  # discontinued after ~2.5 months
    vitamin_start = hrt_start - timedelta(days=122)   # predates HRT

    prescriptions = [
        (1, "Estradiol", PINK, "2mg tablet, sublingual", "DAILY", "", None,
         "08:00:00,20:00:00", dose_increase.isoformat(), 15),
        (2, "Spironolactone", PURPLE, "100mg tablet", "DAILY", "", None,
         "08:00:00", hrt_start.isoformat(), 30),
        (3, "Progesterone", BLUE, "100mg capsule, oral micronized", "DAILY", "",
         None, "22:00:00", progesterone_start.isoformat(), 15),
        (4, "Vitamin D3", AMBER, "2000 IU", "EVERY_X_DAYS", "", 7,
         "09:00:00", vitamin_start.isoformat(), None),
    ]
    cur.executemany(
        """INSERT INTO prescriptions
           (id, name, color, dosis, scheduleType, daysOfWeek, everyXDays, timesOfDay, startDate, reminderLeadMinutes)
           VALUES (?,?,?,?,?,?,?,?,?,?)""",
        prescriptions,
    )

    snapshots = [
        (1, "Estradiol", PINK, "1mg tablet, sublingual",
         dt(hrt_start), dt(dose_increase),
         "DAILY", "", None, "08:00:00", hrt_start.isoformat(), 15),
        (1, "Estradiol", PINK, "2mg tablet, sublingual",
         dt(dose_increase), None,
         "DAILY", "", None, "08:00:00,20:00:00", dose_increase.isoformat(), 15),
        (2, "Spironolactone", PURPLE, "100mg tablet",
         dt(hrt_start), None,
         "DAILY", "", None, "08:00:00", hrt_start.isoformat(), 30),
        (3, "Progesterone", BLUE, "100mg capsule, oral micronized",
         dt(progesterone_start), None,
         "DAILY", "", None, "22:00:00", progesterone_start.isoformat(), 15),
        (4, "Vitamin D3", AMBER, "2000 IU",
         dt(vitamin_start), None,
         "EVERY_X_DAYS", "", 7, "09:00:00", vitamin_start.isoformat(), None),
        # Discontinued: tried early on, no longer in `prescriptions`
        (5, "Finasteride", GRAY, "1mg tablet",
         dt(hrt_start), dt(finasteride_end),
         "DAILY", "", None, "08:00:00", hrt_start.isoformat(), None),
    ]
    cur.executemany(
        """INSERT INTO prescription_snapshots
           (prescriptionId, name, color, dosis, validFrom, validTo,
            schedule_scheduleType, schedule_daysOfWeek, schedule_everyXDays,
            schedule_timesOfDay, schedule_startDate, schedule_reminderLeadMinutes)
           VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
        snapshots,
    )

    cur.execute(
        "SELECT id, schedule_startDate, schedule_everyXDays, schedule_timesOfDay, "
        "validFrom, validTo, schedule_scheduleType, name "
        "FROM prescription_snapshots ORDER BY id ASC"
    )
    snap_rows = cur.fetchall()

    records = []
    for (snap_id, sch_start, every_x, times_str, valid_from, valid_to, sched_type, name) in snap_rows:
        start_date = date.fromisoformat(sch_start)
        range_start = date.fromisoformat(valid_from.split("T")[0])
        range_end_exclusive = (
            date.fromisoformat(valid_to.split("T")[0]) if valid_to else (yesterday + timedelta(days=1))
        )
        times = [t for t in times_str.split(",") if t]

        d = range_start
        while d < range_end_exclusive and d <= yesterday:
            if is_scheduled(d, sched_type, start_date, every_x):
                days_in = (d - range_start).days
                for idx, t in enumerate(times):
                    base = 0.72 if days_in < 14 else 0.90
                    if idx == 1:
                        base -= 0.08
                    if name == "Finasteride":
                        days_before_end = (range_end_exclusive - d).days
                        if days_before_end <= 21:
                            base = 0.45
                    taken = random.random() < base
                    taken_at = None
                    if taken:
                        hh, mm, ss = map(int, t.split(":"))
                        jitter = random.randint(-5, 40)
                        sched_dt = datetime(d.year, d.month, d.day, hh, mm, ss) + timedelta(minutes=jitter)
                        taken_at = sched_dt.strftime("%Y-%m-%dT%H:%M:%S")
                    records.append((snap_id, d.isoformat(), t, 1 if taken else 0, taken_at))
            d += timedelta(days=1)

    cur.executemany(
        """INSERT INTO dose_intake_records (snapshotId, scheduledDate, scheduledTime, taken, takenAt)
           VALUES (?,?,?,?,?)""",
        records,
    )

    conn.commit()

    counts = {}
    for table in ("prescriptions", "prescription_snapshots", "dose_intake_records"):
        counts[table] = cur.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]

    cur.execute("PRAGMA wal_checkpoint(TRUNCATE)")

    conn.close()
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", required=True, help="Path to the pulled prescription_database file")
    parser.add_argument(
        "--today",
        help="Override 'today' as YYYY-MM-DD (default: actual current date). "
        "Mainly useful for reproducible testing of this script.",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=None,
        help="Random seed for reproducible adherence data (default: derived from --today so runs on the same day are stable).",
    )
    args = parser.parse_args()

    today = date.fromisoformat(args.today) if args.today else date.today()
    random.seed(args.seed if args.seed is not None else today.toordinal())

    counts = seed(args.db, today)
    print(
        f"Seeded {counts['prescriptions']} prescriptions, "
        f"{counts['prescription_snapshots']} snapshots, "
        f"{counts['dose_intake_records']} dose intake records "
        f"(as of {today.isoformat()})."
    )


if __name__ == "__main__":
    main()
