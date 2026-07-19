# RadarAlert — Notas do Projeto (memória de contexto)

> Documento gerado para retomar o desenvolvimento em uma próxima sessão.
> Última atualização: sessão 2 (após definição de hardware do ESP32 e alerta
> direcional).

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
9. **Hardware do ESP32 definido**: placa Ideaspark com display colorido
   320x170 (driver provável ST7789, via TFT_eSPI/LovyanGFX, em modo
   paisagem). Precisa confirmar o pinout exato do modelo específico antes
   de codar o firmware.
10. **Vídeo de splash (mp4)** ao abrir o app Android — tela inicial com
    vídeo, depois segue para a tela principal.
11. **Paleta visual baseada na logo** (anexada pelo usuário): fundo escuro,
    detalhes em ciano/turquesa neon (aros, textos, ícones) e âmbar/laranja
    para o número da velocidade e ponteiro. O tema normal (sem radar
    próximo) usa essa paleta; ao entrar no raio de alerta, o fundo passa a
    usar as cores de proximidade (amarelo/laranja/vermelho) por cima desse
    tema.
12. **Layouts diferentes por dispositivo**: ESP32 e tela do celular mostram
    a mesma informação, mas com layout próprio — celular com visual
    "moderno", ESP32 adaptado à resolução 320x170.
13. **Exibição condicional no ESP32 e no celular**: mostrar somente a
    velocidade atual quando não houver radar próximo; ao entrar no raio de
    alerta, mostrar também a velocidade máxima permitida do radar, além da
    cor de fundo.
14. **Alerta direcional** (novidade desta sessão): o aviso deve soar/aparecer
    apenas enquanto o usuário está **se aproximando** do radar; ao
    cruzá-lo, o aviso cessa. Também não deve alertar para radares na
    **pista contrária** (via dupla com canteiro). Ver seção "Alerta
    direcional" abaixo para o desenho da solução.

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

## Alerta direcional (aproximando → cessa ao cruzar; ignora pista contrária)

Dois problemas distintos, duas soluções complementares:

1. **Cessar o aviso após cruzar o radar**: comparar o rumo atual do usuário
   (`Location.getBearing()`, confiável acima de ~3-5 km/h; alternativa é
   calcular o "initial bearing" manualmente entre os dois últimos pontos de
   GPS) com o rumo do usuário até o ponto do radar. Ângulo pequeno → radar à
   frente (aproximando). Quando esse ângulo passar de ~90° → radar ficou
   para trás → marca como "cruzado" e cessa o aviso, mesmo que a distância
   Haversine ainda esteja dentro do raio.
2. **Ignorar radar da pista contrária**: o rumo sozinho não é suficiente
   quando as pistas estão próximas (canteiro estreito), pois o ângulo até
   o ponto da pista oposta pode ser quase idêntico. Solução mais confiável:
   um campo **`sentido`** no CSV (graus 0-359 ou cardinal) indicando a que
   sentido de tráfego aquele radar se aplica. Só considerar o radar como
   candidato a alerta se o rumo atual do usuário estiver dentro de uma
   margem (ex.: ±90°) do sentido cadastrado. **Precisa verificar se a nova
   fonte de dados do usuário já traz esse campo** (bases oficiais como DNIT
   costumam listar radares por "sentido crescente/decrescente" do km); se
   não trouxer, vira um ponto em aberto a resolver (inferência é bem menos
   confiável).

Isso adiciona um novo componente ao domínio: `HeadingTracker` (calcula/lê o
rumo atual) e `DirectionFilter` (decide se um radar é candidato válido e se
o estado é "aproximando" ou "cruzado").

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
  HeadingTracker.kt           // rumo atual do dispositivo (bearing)
  DirectionFilter.kt          // filtra pista contrária + estado aproximando/cruzado
  RadarState.kt               // data class: speed, maxSpeed, distance, colorState

service/
  RadarForegroundService.kt   // orquestra tudo, expõe SharedFlow<RadarState>

presentation/
  SplashActivity.kt           // vídeo mp4 de abertura
  SpeedDisplayActivity.kt     // tela do celular (observa o mesmo Flow), layout moderno
```

### Schema do CSV
```csv
id,rodovia,km,uf,latitude,longitude,velocidade_maxima,sentido,tipo,fonte,atualizado_em
1,BR-101,220.5,SC,-27.5954,-48.5480,80,180,fixo,DNIT,2026-07-01
2,BR-116,45.2,RS,-29.1685,-51.1795,60,0,fixo,DNIT,2026-07-01
```
- `sentido`: rumo (graus 0-359) do tráfego que aquele radar fiscaliza — usado
  pelo `DirectionFilter` para ignorar radar da pista contrária. **A confirmar
  se a fonte de dados atual já fornece esse campo.**

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
- ESP32: Arduino core + `BluetoothSerial.h` + `TFT_eSPI`/`LovyanGFX` (ST7789,
  320x170, paisagem) para o display Ideaspark

## Convenção de pastas do repositório

Só o que estiver dentro do módulo Android (`app/src/main/...`) entra na
build/APK. Pastas na raiz do repo ficam versionadas mas fora da build:
- `/design` → logo, mockups, referências visuais
- `/data-raw` → planilhas originais (Excel do DNIT / outra fonte) antes da
  conversão para o CSV usado pelo app

## Em aberto / próximos passos

1. Confirmar se a fonte de dados atual fornece o campo **sentido** por
   radar (necessário para o `DirectionFilter` ignorar a pista contrária).
2. Levantar o pinout exato da placa Ideaspark específica usada (para
   configurar `TFT_eSPI`/`LovyanGFX` corretamente).
3. Criar o script/processo de conversão da planilha (DNIT + nova fonte de
   velocidade máxima) para o CSV padrão definido acima.
4. Criar a estrutura inicial do projeto Android (módulos listados acima,
   incluindo `HeadingTracker`/`DirectionFilter` e `SplashActivity`).
5. Criar o firmware base do ESP32 (recepção BT + parsing do protocolo +
   lógica de display 320x170 + estado "somente velocidade" vs "com radar
   próximo").
6. Testar a lógica de proximidade, direção e suavização de velocidade antes
   de integrar o Bluetooth (MVP sem hardware, só com logs/tela).
7. Preparar o vídeo de splash (mp4) e os assets de layout do celular
   (baseados na paleta da logo).

## Estado do repositório

Repositório `CmteInacio/RadarAlert` estava vazio (sem commits) no momento
desta conversa. Branch de trabalho: `claude/android-gps-realtime-location-2oymhb`.
Nenhum código foi implementado ainda — a conversa até aqui foi 100% de
alinhamento de requisitos e desenho de arquitetura.
