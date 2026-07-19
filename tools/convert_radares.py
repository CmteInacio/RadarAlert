#!/usr/bin/env python3
"""Converte o export iGO8/Amigo de radares (data-raw) para o CSV interno do
app (data/radares_rs_sc.csv), filtrando apenas pontos dentro do bounding box
de RS/SC.

Uso:
    python3 tools/convert_radares.py [entrada.csv] [saida.csv]

Padrão: le data-raw/RadarFixo_maparadar.csv e grava data/radares_rs_sc.csv
"""
import csv
import hashlib
import sys
from datetime import date
from pathlib import Path

# Bounding box aproximado cobrindo RS + SC (retângulo, não a fronteira real).
LAT_MIN, LAT_MAX = -33.8, -25.9
LON_MIN, LON_MAX = -57.7, -48.3

DIR_TYPE_MAP = {
    "0": "DESCONHECIDO",
    "1": "UNICO",
    "2": "AMBOS",
}

FONTE = "iGO8/Amigo - mapa de radar"


def convert(input_path: Path, output_path: Path) -> tuple[int, int]:
    rows_out = []
    total = 0
    with input_path.open(newline="", encoding="utf-8") as f_in:
        reader = csv.DictReader(f_in)
        for row in reader:
            total += 1
            lat = float(row["Y"])
            lon = float(row["X"])
            if not (LAT_MIN <= lat <= LAT_MAX and LON_MIN <= lon <= LON_MAX):
                continue
            dir_type = DIR_TYPE_MAP.get(row["DirType"], "DESCONHECIDO")
            rows_out.append(
                {
                    "id": len(rows_out) + 1,
                    "latitude": lat,
                    "longitude": lon,
                    "velocidade_maxima": int(row["SPEED"]),
                    "sentido_tipo": dir_type,
                    "direcao_graus": float(row["Direction"]),
                    "fonte": FONTE,
                    "atualizado_em": date.today().isoformat(),
                }
            )

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as f_out:
        writer = csv.DictWriter(
            f_out,
            fieldnames=[
                "id",
                "latitude",
                "longitude",
                "velocidade_maxima",
                "sentido_tipo",
                "direcao_graus",
                "fonte",
                "atualizado_em",
            ],
        )
        writer.writeheader()
        writer.writerows(rows_out)

    return total, len(rows_out)


def write_version(output_path: Path, version_path: Path) -> None:
    digest = hashlib.sha256(output_path.read_bytes()).hexdigest()
    version_path.write_text(digest + "\n", encoding="utf-8")


def main() -> None:
    repo_root = Path(__file__).resolve().parent.parent
    input_path = Path(sys.argv[1]) if len(sys.argv) > 1 else repo_root / "data-raw" / "RadarFixo_maparadar.csv"
    output_path = Path(sys.argv[2]) if len(sys.argv) > 2 else repo_root / "data" / "radares_rs_sc.csv"

    total, kept = convert(input_path, output_path)
    write_version(output_path, output_path.parent / "VERSION.txt")

    print(f"Lidos: {total}")
    print(f"Mantidos (dentro do bounding box RS/SC): {kept}")
    print(f"Gravado em: {output_path}")


if __name__ == "__main__":
    main()
