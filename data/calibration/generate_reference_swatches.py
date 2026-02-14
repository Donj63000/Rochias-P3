#!/usr/bin/env python3
"""Génère reference-swatches.csv à partir des acquisitions Lab contrôlées."""
from __future__ import annotations

import csv
import math
from collections import defaultdict
from pathlib import Path
from statistics import median

ROOT = Path(__file__).resolve().parent
INPUT_FILE = ROOT / "reference-swatches-acquisitions.csv"
OUTPUT_FILE = ROOT / "reference-swatches.csv"


def delta_e_cie76(lab_1: tuple[float, float, float], lab_2: tuple[float, float, float]) -> float:
    return math.sqrt(
        (lab_1[0] - lab_2[0]) ** 2 + (lab_1[1] - lab_2[1]) ** 2 + (lab_1[2] - lab_2[2]) ** 2
    )


def trend_note(ppm: int) -> str:
    if ppm <= 50:
        return "jaune net"
    if ppm <= 300:
        return "atténuation progressive (gris-jaune)"
    return "terne gris/brun"


def main() -> None:
    grouped: dict[int, list[dict[str, str]]] = defaultdict(list)

    with INPUT_FILE.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            grouped[int(row["ppm"])].append(row)

    output_rows: list[dict[str, str]] = []
    for ppm in sorted(grouped):
        rows = grouped[ppm]
        if len(rows) < 3:
            raise ValueError(f"Patch {ppm} ppm: minimum 3 acquisitions requis.")

        versions = {r["calibration_version"] for r in rows}
        protocols = {r["capture_protocol"] for r in rows}
        if len(versions) != 1 or len(protocols) != 1:
            raise ValueError(f"Patch {ppm} ppm: versions/protocoles incohérents.")

        l_values = [float(r["L_star"]) for r in rows]
        a_values = [float(r["a_star"]) for r in rows]
        b_values = [float(r["b_star"]) for r in rows]

        med_l = median(l_values)
        med_a = median(a_values)
        med_b = median(b_values)

        med_lab = (med_l, med_a, med_b)
        sigma = median([delta_e_cie76((l, a, b), med_lab) for l, a, b in zip(l_values, a_values, b_values)])

        output_rows.append(
            {
                "calibration_version": versions.pop(),
                "capture_protocol": protocols.pop(),
                "ppm": str(ppm),
                "L_star": f"{med_l:.2f}",
                "a_star": f"{med_a:.2f}",
                "b_star": f"{med_b:.2f}",
                "sigma_deltaE": f"{sigma:.2f}",
                "notes": trend_note(ppm),
            }
        )

    with OUTPUT_FILE.open("w", newline="", encoding="utf-8") as f:
        fieldnames = [
            "calibration_version",
            "capture_protocol",
            "ppm",
            "L_star",
            "a_star",
            "b_star",
            "sigma_deltaE",
            "notes",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(output_rows)


if __name__ == "__main__":
    main()
