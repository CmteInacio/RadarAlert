# RadarAlert — Notas do Projeto (memória de contexto)

> Documento gerado para retomar o desenvolvimento em uma próxima sessão.
> Última atualização: 2026-07-18

## Objetivo do projeto

App Android pessoal (não será publicado na Play Store) que sinaliza quando o
usuário está a **300m ou menos** de um radar fixo de velocidade, usando uma
base de coordenadas importada de um CSV versionado no git. O app roda em
segundo plano (mesmo com o smartphone bloqueado) e se conecta via
**Bluetooth Classic (SPP)** a um **ESP32** com display, que mostra a
velocidade atual e, quando dentro do raio, a velocidade máxima da via e uma
cor de fundo conforme a proximidade:

- **Amarelo**: ≤ 300m
- **Laranja**: ≤ 200m
- **Vermelho**: ≤ 100m

O projeto também serve como aprendizado pessoal (conexões Bluetooth,
tratamento/atualização de dados, GPS em background) e como **backup do
Waze**, que é o app principal usado hoje.

## Decisões já alinhadas com o usuário

1. **App pessoal, sem Play Store** — sem preocupação com revisão de
   permissões da Google; liberar manualmente restrições de bateria e
   autostart no aparelho é aceitável e tranquilo.
2. **Fonte dos dados dos radares: DNIT** (planilha Excel oficial, disponível
   para download). O usuário baixa manualmente antes de pegar a estrada,
   converte para CSV e sobe pro git. O app verifica no início se há versão
   nova no repositório remoto e atualiza o cache local se necessário.
   - **Ponto de atenção**: DNIT cobre rodovias federais (BR-xxx). Rodovias
     estaduais do RS (ERS-xxx, mantidas pelo DAER) e SC (SC-xxx) podem
     precisar de fonte complementar no futuro para cobertura completa —
     não bloqueante agora.
3. **Escopo geográfico inicial: apenas RS e SC** — poucas centenas de
   pontos, permite cálculo de distância por força bruta (Haversine) sem
   necessidade de geohash/indexação espacial.
4. **Indicador de desconexão BT**: ícone grande de Bluetooth em vermelho no
   display do ESP32 enquanto tenta reconectar.
5. **Suavização de velocidade**: usar média móvel simples sobre as últimas
   leituras de `Location.getSpeed()` para evitar oscilação de cor/velocidade
   por ruído de GPS.
6. **Intervalo de GPS dinâmico**: aumentar o intervalo de consulta do GPS
   quando a velocidade estiver abaixo de ~30 km/h (economia de bateria),
   voltando a 1s em velocidades maiores.
7. **Somente radares fixos** — no Brasil é permitido alertar sobre radares
   fixos (posição pública/sinalizada); radares móveis ficam fora de escopo
   (e teriam implicação legal diferente).
8. **Tela do celular + ESP32 funcionam em paralelo, sempre** (não é um
   fallback condicional). Se o background do Android for problemático,
   existe a opção de manter uma tela sempre ligada no próprio celular
   (mostrando velocidade/cor) com beeps de proximidade — mas o ESP32
   continua recebendo dados independentemente disso. A arquitetura já
   prevê isso desde o início: um único fluxo de estado (`RadarState`) com
   múltiplos consumidores (Bluetooth sender, UI do celular, som).

## Principais dificuldades/pontos fracos identificados

- **Manutenção da base de radares**: maior risco de longo prazo é a base
  ficar desatualizada (mitigado pelo fluxo manual de atualização via git
  antes de viagens).
- **App killers dos fabricantes** (Xiaomi/Samsung/Huawei/Asus): mesmo com
  foreground service, pode ser necessário liberar manualmente exceções de
  bateria — sem solução 100% programática.
- **Precisão de GPS**: erro típico de alguns metros a poucas dezenas de
  metros em áreas urbanas/túneis — relevante justamente no raio de 100m.
  Mitigado parcialmente pela média móvel de velocidade (a suavização não
  resolve erro de posição, só de leitura de velocidade — vale lembrar
  disso ao testar o raio de disparo das cores).
- **Bluetooth em background**: Android also restringe operações BT em
  segundo plano em versões recentes; precisa de `BLUETOOTH_CONNECT`
  (Android 12+) e lógica de reconexão com backoff.
- **Bateria**: GPS de alta frequência + BT conectado continuamente +
  foreground service = consumo alto; mitigado pelo intervalo dinâmico de
  GPS.

## Desenho técnico (arquitetura proposta)

### Componentes
```
App Android (Kotlin) <--BT Classic (SPP)--> ESP32 + display
        |  ^
        |  | fetch CSV na abertura do app
        v  |
Repo Git (radares_rs_sc.csv, atualizado manualmente a partir do DNIT)
```

### Estrutura de pastas/camadas (Android)
```
data/
  radar/
    RadarDao.kt              // Room
    RadarEntity.kt
    RadarRepository.kt        // decide: cache local ou busca no git
    GitCsvFetcher.kt          // baixa CSV + compara versão (hash/VERSION)
  location/
    LocationRepository.kt     // wrapper do FusedLocationProviderClient
  bluetooth/
    BluetoothRepository.kt    // conecta/reconecta/envia ao ESP32 (SPP)

domain/
  ProximityCalculator.kt      // Haversine, radar mais próximo + distância
  SpeedSmoother.kt            // média móvel da velocidade
  GpsIntervalStrategy.kt      // intervalo do GPS conforme velocidade atual
  RadarState.kt               // data class: speed, maxSpeed, distance, colorState

service/
  RadarForegroundService.kt   // orquestra tudo, expõe SharedFlow<RadarState>

presentation/
  SpeedDisplayActivity.kt     // tela do celular (observa o mesmo Flow)
```

### Schema do CSV
```csv
id,rodovia,km,uf,latitude,longitude,velocidade_maxima,tipo,fonte,atualizado_em
1,BR-101,220.5,SC,-27.5954,-48.5480,80,fixo,DNIT,2026-07-01
2,BR-116,45.2,RS,-29.1685,-51.1795,60,fixo,DNIT,2026-07-01
```
Um `VERSION.txt` (ou hash do commit) permite ao app comparar versão local x
remota antes de baixar novamente.

### Protocolo Bluetooth (texto simples, fácil de debugar)
```
SPEED:78;MAXSPEED:60;DIST:150;STATE:ORANGE\n
```
- Envio a cada leitura processada.
- Heartbeat `PING\n` a cada 2s; ESP32 sem receber nada por ~5s assume
  desconexão → ícone BT vermelho piscando + tentativa de reconexão.
- Lado Android: reconexão com backoff (2s, 4s, 8s...) ao detectar
  `ACTION_ACL_DISCONNECTED`.

### App em segundo plano
- `RadarForegroundService` do tipo `location`, com notificação fixa
  (obrigatório a partir do Android 10+).
- Sem `WorkManager` (é para tarefas periódicas, não stream contínuo) — tudo
  roda no próprio foreground service via `LocationCallback`.

### Stack sugerida
- Kotlin + Jetpack (Room, Lifecycle/Flow, Foreground Service)
- `FusedLocationProviderClient` (Google Play Services)
- `BluetoothAdapter`/`BluetoothSocket` nativo (SPP), sem libs externas
- ESP32: Arduino core + `BluetoothSerial.h` + lib de display (a definir
  conforme o hardware escolhido)

## Em aberto / próximos passos

1. **Definir o display do ESP32** (TFT colorido vs. OLED monocromático) —
   isso muda a lib do firmware e se a "cor de fundo" será uma cor real ou
   um indicador visual equivalente (ex. padrão de piscar) em display
   monocromático.
2. Criar o script/processo de conversão do Excel do DNIT para o CSV padrão
   definido acima.
3. Criar a estrutura inicial do projeto Android (módulos listados acima).
4. Criar o firmware base do ESP32 (recepção BT + parsing do protocolo +
   lógica de display).
5. Testar a lógica de proximidade e suavização de velocidade antes de
   integrar o Bluetooth (MVP sem hardware, só com logs/tela).

## Estado do repositório

Repositório `CmteInacio/RadarAlert` estava vazio (sem commits) no momento
desta conversa. Branch de trabalho: `claude/android-gps-realtime-location-2oymhb`.
Nenhum código foi implementado ainda — a conversa até aqui foi 100% de
alinhamento de requisitos e desenho de arquitetura.
