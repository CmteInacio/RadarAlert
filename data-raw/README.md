# data-raw

Arquivos originais, no formato exportado pela fonte, antes de qualquer
conversão. Nada aqui é tocado por nenhuma build (Android ou ESP32) — servem
só de entrada para os scripts em `/tools`.

## RadarFixo_maparadar.csv

- **Fonte**: exportação do site comunitário de mapeamento de radares (o
  usuário baixa manualmente antes de viagens).
- **Formato**: POI padrão iGO8/Amigo — `X,Y,TYPE,SPEED,DirType,Direction`.
  - `X` = longitude, `Y` = latitude
  - `TYPE` = sempre `1` neste arquivo (export já filtrado só p/ radar fixo)
  - `SPEED` = velocidade máxima da via no ponto (km/h)
  - `DirType` = como a comunidade classificou o sentido do radar:
    `0` = não informado, `1` = sentido único, `2` = ambos os sentidos
  - `Direction` = rumo em graus (0-359) associado ao radar
- **Como atualizar**: baixe uma exportação nova do mesmo site, substitua
  este arquivo e rode `tools/convert_radares.py` para regerar o CSV usado
  pelo app em `/data`.
