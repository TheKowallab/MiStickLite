#!/usr/bin/env python3
"""ADB PSS sampling; optional debug status check. Never changes settings or records keys."""
import argparse
import csv
import datetime
import json
import re
import statistics
import subprocess
import time
from pathlib import Path

PACKAGE = "pl.misticklite"
FIELDS = ("sample", "state", "measured_at", "status", "pid", "total_pss_kib", "java_heap_pss_kib", "native_heap_pss_kib",
          "launcher_state", "service_state", "redirect_mode")


def parse_control_status(output):
    values = dict(re.findall(r"\b(ok|mode|service|launcher|pendingPairs)=([A-Za-z0-9]+)\b", output))
    if values.get("ok") != "true" or values.get("mode") not in ("ON", "OFF") or values.get("service") not in ("CONNECTED", "DISCONNECTED") or values.get("launcher") not in ("CLOSED", "FINISHING", "FOREGROUND", "BACKGROUND"):
        raise ValueError("Brak poprawnego potwierdzenia debug status; nie zapisano surowego wyniku ADB.")
    if values["launcher"] != "CLOSED":
        raise ValueError("Pulpit nie jest zamknięty. Użyj close-launcher i poczekaj na launcher=CLOSED.")
    if values.get("pendingPairs") != "0":
        raise ValueError("Aktywny gest HOME. Zwolnij klawisz przed pomiarem.")
    return {"launcher_state": values["launcher"], "service_state": values["service"], "redirect_mode": values["mode"]}


def read_closed_status(serial):
    result = subprocess.run(["adb", "-s", serial, "shell", "am", "broadcast", "--user", "current", "--receiver-foreground",
        "-n", "pl.misticklite/.HomeRedirectDebugReceiver", "-a", "pl.misticklite.debug.HOME_REDIRECT", "--es", "command", "status"],
        check=True, capture_output=True, text=True, timeout=30)
    return parse_control_status(result.stdout)


def parse_meminfo(dump):
    if "No process found" in dump or "No processes found" in dump:
        return {"status": "not_running", "pid": "", "total_pss_kib": "", "java_heap_pss_kib": "", "native_heap_pss_kib": ""}
    total = None
    for pattern in (r"TOTAL PSS:\s*([\d,]+)", r"^\s*TOTAL\s+([\d,]+)\b", r"^\s*TOTAL:\s*([\d,]+)"):
        match = re.search(pattern, dump, re.MULTILINE)
        if match:
            total = int(match.group(1).replace(",", ""))
            break
    if total is None:
        # Do not print or save the raw dump on failure.
        raise ValueError("Nie rozpoznano TOTAL PSS; nie zapisano surowego dumpsys.")
    def number(pattern):
        match = re.search(pattern, dump, re.MULTILINE)
        return int(match.group(1).replace(",", "")) if match else ""
    return {"status": "running", "pid": number(r"MEMINFO in pid\s+(\d+)"), "total_pss_kib": total,
            "java_heap_pss_kib": number(r"^\s*Java Heap:\s*([\d,]+)"),
            "native_heap_pss_kib": number(r"^\s*Native Heap:\s*([\d,]+)")}


def summarize(path):
    with Path(path).open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    values = [int(row["total_pss_kib"]) for row in rows if row["status"] == "running" and row["total_pss_kib"]]
    return {"samples": len(rows), "running_samples": len(values),
            "pids": sorted({row["pid"] for row in rows if row["pid"]}),
            "median_total_pss_kib": statistics.median(values) if values else None,
            "launcher_states": sorted({row.get("launcher_state", "") for row in rows} - {""}),
            "service_states": sorted({row.get("service_state", "") for row in rows} - {""}),
            "redirect_modes": sorted({row.get("redirect_mode", "") for row in rows} - {""})}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    collect = commands.add_parser("collect", help="Wyłącznie odczyt dumpsys meminfo wskazanego procesu")
    collect.add_argument("serial", help="Dokładny serial ADB; skrypt nie łączy się automatycznie z TV")
    collect.add_argument("state", choices=("off", "on-idle", "on-after-keys", "off-again", "diagnostic-ui-closed", "redirect-ui-closed"), help="Stan ręcznie ustawiony i sprawdzony przez użytkownika")
    collect.add_argument("--samples", type=int, default=5)
    collect.add_argument("--interval", type=float, default=3.0)
    collect.add_argument("--output", type=Path, required=True)
    collect.add_argument("--require-launcher-closed", action="store_true", help="Sprawdź debug status przed każdą próbką; może uruchomić proces receivera, ale nie otwiera UI")
    compare = commands.add_parser("compare", help="Porównanie lokalnych CSV, bez ADB")
    compare.add_argument("before", type=Path)
    compare.add_argument("after", type=Path)
    args = parser.parse_args()
    if args.command == "compare":
        before, after = summarize(args.before), summarize(args.after)
        a, b = before["median_total_pss_kib"], after["median_total_pss_kib"]
        print(json.dumps({"before": before, "after": after, "delta_process_pss_kib": b - a if a is not None and b is not None else None,
                          "note": "Różnica PSS całego wspólnego procesu, nie wyizolowana pamięć usługi. Nie porównuj różnych scenariuszy UI/procesu."}, ensure_ascii=False, indent=2))
        return
    if not 1 <= args.samples <= 60 or not 0 <= args.interval <= 60:
        parser.error("--samples: 1..60; --interval: 0..60 sekund")
    if args.state in ("diagnostic-ui-closed", "redirect-ui-closed") and not args.require_launcher_closed:
        parser.error("Scenariusz UI-closed wymaga --require-launcher-closed")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Refuse to overwrite earlier measurements.
    with args.output.open("x", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        for i in range(args.samples):
            technical = {"launcher_state": "", "service_state": "", "redirect_mode": ""}
            if args.require_launcher_closed:
                technical = read_closed_status(args.serial)
                expected = {"diagnostic-ui-closed": "OFF", "redirect-ui-closed": "ON"}.get(args.state)
                if expected and (technical["service_state"] != "CONNECTED" or technical["redirect_mode"] != expected):
                    raise ValueError("Stan usługi/trybu zmienił się podczas pomiaru. Sprawdź status i powtórz serię.")
            result = subprocess.run(["adb", "-s", args.serial, "shell", "dumpsys", "meminfo", PACKAGE],
                                    check=True, capture_output=True, text=True, timeout=30)
            row = parse_meminfo(result.stdout)
            if args.require_launcher_closed and read_closed_status(args.serial) != technical:
                raise ValueError("Stan zmienił się podczas odczytu PSS; próbki nie zapisano.")
            writer.writerow(dict(row, **technical, sample=i + 1, state=args.state,
                                 measured_at=datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")))
            handle.flush()
            if i + 1 < args.samples:
                time.sleep(args.interval)
    print(f"Zapisano wyłącznie liczniki pamięci do {args.output}")


if __name__ == "__main__":
    main()
