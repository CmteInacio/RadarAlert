# data-raw

Arquivos originais, no formato exportado pela fonte, antes de qualquer
conversão. Nada aqui é tocado por nenhuma build (Android ou ESP32) — servem
só de entrada para os scripts em `/tools`.

## `*_maparadar.csv` (RadarFixo, Lombada, PoliciaRodoviaria, Pedagio)

- **Fonte**: exportações do site comunitário de mapeamento de radares (o
  usuário baixa manualmente antes de viagens, um arquivo por categoria).
- **Formato**: POI padrão iGO8/Amigo — `X,Y,TYPE,SPEED,DirType,Direction`.
  - `X` = longitude, `Y` = latitude
  - `TYPE` = código numérico fixo por categoria de arquivo (descoberto
    empiricamente, ver `TYPE_TO_TIPO` em `tools/convert_radares.py`):
    `1` = radar fixo, `7` = polícia rodoviária, `8` = lombada eletrônica,
    `14` = pedágio
  - `SPEED` = velocidade máxima da via no ponto (km/h) — `0` em Lombada,
    Polícia e Pedágio (essas categorias não têm limite de velocidade
    associado na fonte)
  - `DirType` = como a comunidade classificou o sentido do ponto:
    `0` = não informado, `1` = sentido único, `2` = ambos os sentidos
  - `Direction` = rumo em graus (0-359) associado ao ponto
- **Como atualizar**: baixe exportações novas do mesmo site (mesmo nome de
  arquivo ou não, contanto que termine em `_maparadar.csv`), substitua os
  arquivos aqui e rode `tools/convert_radares.py` (sem argumentos) para
  mesclar todos e regerar o CSV usado pelo app em `/data`.
