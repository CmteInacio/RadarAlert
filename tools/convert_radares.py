#!/usr/bin/env python3
"""Converte os exports iGO8/Amigo de radar fixo/lombada/polícia/pedágio
(data-raw) para o CSV interno do app (data/radares_rs_sc.csv), mesclando
todos os arquivos e filtrando apenas pontos dentro do bounding box de
RS/SC. O tipo de cada ponto é detectado automaticamente pelo código TYPE
do arquivo de origem (ver TYPE_TO_TIPO abaixo).

Uso:
    python3 tools/convert_radares.py [arquivo1.csv arquivo2.csv ...]

Padrão (sem argumentos): lê todos os *_maparadar.csv em data-raw/ e grava
em data/radares_rs_sc.csv.
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

# Código TYPE do formato iGO8/Amigo -> TipoAlerta do app (ver
# com.radaralert.app.domain.TipoAlerta). Descoberto empiricamente nos
# arquivos exportados do site: um código numérico fixo por categoria.
TYPE_TO_TIPO = {
    "1": "RADAR_FIXO",
    "7": "POLICIA_RODOVIARIA",
    "8": "LOMBADA_ELETRONICA",
    "14": "PEDAGIO",
}

FONTE = "iGO8/Amigo - mapa de radar"


def convert(input_paths: list[Path], output_path: Path) -> tuple[int, int, int]:
    rows_out = []
    total = 0
    skipped_tipo_desconhecido = 0

    for input_path in input_paths:
        with input_path.open(newline="", encoding="utf-8") as f_in:
            reader = csv.DictReader(f_in)
            for row in reader:
                total += 1
                lat = float(row["Y"])
                lon = float(row["X"])
                if not (LAT_MIN <= lat <= LAT_MAX and LON_MIN <= lon <= LON_MAX):
                    continue

                tipo = TYPE_TO_TIPO.get(row["TYPE"])
                if tipo is None:
                    skipped_tipo_desconhecido += 1
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
                        "tipo": tipo,
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
                "tipo",
                "fonte",
                "atualizado_em",
            ],
        )
        writer.writeheader()
        writer.writerows(rows_out)

    return total, len(rows_out), skipped_tipo_desconhecido


def write_version(output_path: Path, version_path: Path) -> None:
    digest = hashlib.sha256(output_path.read_bytes()).hexdigest()
    version_path.write_text(digest + "\n", encoding="utf-8")


def main() -> None:
    repo_root = Path(__file__).resolve().parent.parent
    output_path = repo_root / "data" / "radares_rs_sc.csv"

    args = sys.argv[1:]
    if args:
        input_paths = [Path(a) for a in args]
    else:
        input_paths = sorted((repo_root / "data-raw").glob("*_maparadar.csv"))

    total, kept, skipped = convert(input_paths, output_path)
    write_version(output_path, output_path.parent / "VERSION.txt")

    print(f"Arquivos lidos: {[p.name for p in input_paths]}")
    print(f"Linhas lidas no total: {total}")
    print(f"Mantidas (dentro do bounding box RS/SC): {kept}")
    if skipped:
        print(f"Ignoradas por código TYPE desconhecido: {skipped}")
    print(f"Gravado em: {output_path}")


if __name__ == "__main__":
    main()
