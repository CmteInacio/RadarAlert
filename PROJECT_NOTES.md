# RadarAlert — Notas do Projeto (memória de contexto)

> Documento gerado para retomar o desenvolvimento em uma próxima sessão.
> Última atualização: sessão 3 (depuração do primeiro build no Android
> Studio).

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
   320x170, driver ST7789 via **TFT_eSPI**. Usuário já usou essa placa em
   outros projetos e já tem a pinagem configurada — sem pendência aqui.
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
15. **Fonte de dados já tem campo de sentido**: formato real da fonte do
    usuário é `X,Y,TYPE,SPEED,DirType,Direction` (ex.:
    `-54.580523,-20.480856,1,30,1,235`), onde `X`=longitude, `Y`=latitude,
    `SPEED`=velocidade máxima, `Direction`=sentido em graus (0-359). É o
    formato de POI usado por GPS **iGO8/Amigo** para bases de radares
    (comum em bancos de dados brasileiros redistribuídos nesse formato).
    - **`TYPE` resolvido**: análise do arquivo real (`RadarFixo_maparadar.txt`,
      11.775 linhas) mostra `TYPE=1` em 100% dos registros — export já vem
      filtrado só com radares fixos (confirmado pelo usuário). Não precisa
      de mapeamento de código.
    - **`DirType` — hipótese refinada** (baseada na distribuição real dos
      dados: `DirType=0` em 1.211 linhas, `DirType=1` em 9.060, `DirType=2`
      em 1.504; `Direction` preenchido 0-359 nos três grupos): o campo
      corresponde à opção de 3 estados que a comunidade preenche ao
      cadastrar o radar (sentido único / ambos os sentidos / não
      informado):
      - `DirType=1` (maioria) → **sentido único**: só considera o radar se
        o rumo do usuário estiver a ±90° de `Direction`.
      - `DirType=2` → **ambos os sentidos**: considera se o rumo estiver a
        ±90° de `Direction` OU de `Direction+180°`.
      - `DirType=0` → **não informado**: trata como omnidirecional (sem
        filtro de sentido) — mais seguro alertar de mais do que perder um
        alerta real por falta de dado.
      Ainda não confirmado 100% cruzando com a fonte comunitária (cruzar
      alguns pontos manualmente no site de origem validaria de vez).
16. **Repositório em monorepo**: projeto terá duas linguagens (Kotlin no
    Android, C++ no firmware ESP32) no mesmo repositório. Estrutura definida
    em "Convenção de pastas do repositório" abaixo.
17. **PlatformIO** confirmado como toolchain do firmware ESP32 (`/esp32`).

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

**Regra do `DirectionFilter` considerando `DirType`/`Direction` (formato
iGO8/Amigo, 3 estados — ver item 15 das decisões para a análise dos dados
reais)**:
- `DirType=1` (sentido único): só considera o radar candidato se o rumo
  atual do usuário estiver dentro de ±90° do valor de `Direction`.
- `DirType=2` (ambos os sentidos): candidato se o rumo estiver a ±90° de
  `Direction` **ou** de `Direction+180°`.
- `DirType=0` (não informado): trata como omnidirecional — não filtra por
  sentido.
- Em todos os casos, o estado "aproximando → cruzado" continua sendo
  calculado pelo rumo do próprio usuário até o ponto do radar (independente
  do `DirType`).

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

Formato bruto da fonte de dados do usuário:
```csv
X,Y,TYPE,SPEED,DirType,Direction
-54.580523,-20.480856,1,30,1,235
```
Mapeamento para o schema interno do app:

| Campo fonte | Campo interno      | Observação                              |
|-------------|---------------------|------------------------------------------|
| `X`         | `longitude`         | —                                        |
| `Y`         | `latitude`          | —                                        |
| `SPEED`     | `velocidade_maxima`  | —                                        |
| `Direction` | `sentido`            | graus 0-359, usado pelo `DirectionFilter` |
| `TYPE`      | `tipo`               | **a confirmar**: qual valor = "fixo"?     |
| `DirType`   | (ainda não mapeado)  | **a confirmar**: função do campo          |

```csv
id,rodovia,km,uf,latitude,longitude,velocidade_maxima,sentido,tipo,fonte,atualizado_em
1,BR-101,220.5,SC,-27.5954,-48.5480,80,180,fixo,DNIT,2026-07-01
2,BR-116,45.2,RS,-29.1685,-51.1795,60,0,fixo,DNIT,2026-07-01
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
- ESP32: Arduino core + `BluetoothSerial.h` + `TFT_eSPI`/`LovyanGFX` (ST7789,
  320x170, paisagem) para o display Ideaspark

## Convenção de pastas do repositório (monorepo)

Repositório único com duas linguagens (Kotlin no app, C++ no firmware).
Estrutura proposta na raiz:
```
/android      → projeto Gradle (Kotlin)
/esp32        → firmware PlatformIO (C++)
/design       → logo, mockups, paleta (usuário já está colocando arquivos aqui)
/data-raw     → planilhas/CSVs originais antes da conversão
PROJECT_NOTES.md
```
Cada pasta de código tem sua própria toolchain isolada; `/design` e
`/data-raw` ficam fora de qualquer build.

## Scaffold inicial criado (marco desta sessão)

Estrutura completa criada e commitada:
- `/data-raw/RadarFixo_maparadar.csv` — export real do usuário (11.775
  linhas, formato iGO8/Amigo) + README explicando a fonte.
- `/tools/convert_radares.py` — converte o export para o schema interno,
  filtra pelo bounding box de RS/SC e gera hash de versão. Rodado de
  verdade contra o arquivo real: **1.359 radares mantidos** dentro do
  bounding box, gravados em `/data/radares_rs_sc.csv` + `/data/VERSION.txt`.
- `/android` — projeto Gradle (Kotlin) com o domínio completo
  (`GeoMath`, `ProximityCalculator`, `DirectionFilter`, `HeadingTracker`,
  `SpeedSmoother`, `GpsIntervalStrategy`, `RadarStateEngine`), camada de
  dados (Room + `GitCsvFetcher` + `RadarRepository`, `LocationRepository`,
  `BluetoothRepository`), `RadarForegroundService`, `SplashActivity` (vídeo
  mp4) e `SpeedDisplayActivity` (Compose, paleta da logo, ícone de BT
  piscando quando desconectado).
- `/esp32` — projeto PlatformIO com `main.cpp` (BluetoothSerial + TFT_eSPI,
  320x170 paisagem, estados "só velocidade" / "com radar" / "BT
  desconectado piscando") e `Protocol.h` (parser do protocolo de texto).

**O que foi de fato verificado nesta sessão** (sem Android SDK nem
PlatformIO disponíveis neste ambiente): o domínio (`GeoMath`,
`DirectionFilter`, `RadarStateEngine`) foi copiado para um projeto Kotlin/JVM
isolado e rodado com `gradle test` de verdade — **12 testes passando**,
incluindo o cenário completo de "aproxima → alerta por faixa de cor → cruza
e cessa o alerta" e o de "ignora radar de sentido único na pista
contrária". Isso pegou um bug real: com tolerância de ±90°, o filtro de
radares "ambos os sentidos" ficava sempre verdadeiro (as duas faixas
cobrem o círculo inteiro) — ajustado para ±80° para deixar uma folga real
de exclusão de tráfego perpendicular.

**O que NÃO foi verificado** (sem SDK/toolchain no ambiente): compilação
real do módulo Android via Gradle (falta Android SDK) e do firmware via
PlatformIO. O código segue as APIs corretas (FusedLocationProviderClient,
Room, BluetoothSocket SPP, TFT_eSPI), mas só será validado de fato ao abrir
no Android Studio / compilar o firmware.

## Pendências conhecidas do scaffold

1. `ESP32_MAC_ADDRESS` em `RadarForegroundService.kt` está com um valor
   placeholder (`00:00:00:00:00:00`) — precisa ser trocado pelo endereço
   real do ESP32 (idealmente numa tela de pareamento, não fixo no código).
2. `include/TFT_eSPI_User_Setup.h` no `/esp32` tem pinagem genérica de
   exemplo — substituir pela configuração real já usada em projetos
   anteriores com essa mesma placa Ideaspark.
3. ~~`res/raw/splash.mp4` não existia~~ **Resolvido**: o vídeo real do
   usuário já está commitado em `android/app/src/main/res/raw/splash.mp4`
   (~1,3MB, veio de um backup dele — ver "Sessão 3" abaixo pro histórico
   completo do imprevisto).
4. ~~Gradle wrapper não foi gerado~~ **Resolvido**: `gradlew`, `gradlew.bat`
   e `gradle/wrapper/gradle-wrapper.jar` foram adicionados (ver "Wrapper do
   Gradle" abaixo — era a causa raiz de o Android Studio não reconhecer o
   projeto como Android e pedir "Java Main Class" ao rodar).
5. (Opcional, baixa prioridade) Validar a hipótese de `DirType` cruzando
   2-3 pontos do CSV com o site comunitário de origem.
6. Trocar `applicationId`/pacote (`com.radaralert.app`) se o usuário
   preferir outro nome, e revisar a URL do `GitCsvFetcher`
   (`raw.githubusercontent.com/CmteInacio/RadarAlert/main/...`) contra o
   branch real que vai hospedar os dados em produção.
7. Build ainda não confirmado como 100% verde pelo usuário — última rodada
   de `./gradlew assembleDebug --stacktrace` falhou por causa do item 3
   (antes de resolvido). Próxima ação: usuário roda o build de novo agora
   que o vídeo real está no lugar e reporta o resultado.

## Wrapper do Gradle (resolvido)

O projeto foi criado sem `gradlew`/`gradlew.bat`/`gradle-wrapper.jar`. Sem
isso, o Android Studio não consegue sincronizar o projeto como um projeto
Gradle/Android de verdade — e ao tentar rodar, ele cai no comportamento de
"rodar arquivo Kotlin avulso", que pede uma "Java Main Class" (por isso o
erro relatado pelo usuário). Corrigido nesta sessão:
- `gradle-wrapper.jar` extraído de uma instalação local do Gradle 8.14.3
  (é um jar padrão, idêntico ao gerado por `gradle wrapper`).
- `gradle-wrapper.properties` apontando para Gradle 8.7 (versão mínima
  exigida pelo AGP 8.5.0 usado em `android/build.gradle.kts`).
- `gradlew`/`gradlew.bat` escritos manualmente (scripts padrão, praticamente
  inalterados entre versões recentes do Gradle).
- Testado com `./gradlew --version` neste ambiente: o script funcionou
  corretamente até a etapa de baixar a distribuição do Gradle — travou só
  porque a política de rede deste sandbox bloqueia esse download específico
  (não deve ocorrer na rede normal do usuário).

**Depois de puxar essa atualização**: no Android Studio, feche e reabra o
projeto (ou "File > Sync Project with Gradle Files") para ele detectar o
wrapper e sincronizar de verdade — isso vai baixar o Gradle 8.7 e os plugins
Android/Kotlin (precisa de internet, pode demorar alguns minutos na
primeira vez). Depois disso, o dropdown de configuração de execução no
topo do Studio deve mostrar "app" (com ícone do Android) em vez de pedir
uma classe Java — se ainda pedir, é sinal de tentar rodar um arquivo `.kt`
individual (clique na seta verde do arquivo) em vez de selecionar a
configuração "app" e rodar num emulador/dispositivo.

## Sessão 3 — depuração do primeiro build no Android Studio

Usuário abriu o projeto no Android Studio pela primeira vez e foi resolvendo
uma cadeia de erros, um de cada vez:

1. **"Java Main Class" ao rodar`** → causa raiz: faltava o Gradle wrapper
   (não tinha sido gerado no scaffold inicial). Sem ele o Studio não
   sincroniza o projeto como Android de verdade. Resolvido gerando
   `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` manualmente (ver seção
   "Wrapper do Gradle" acima).
2. **Ícone do Android com X vermelho / `RadarAlert.app.androidTest` inválido,
   "Cannot obtain the package"** → faltava `testInstrumentationRunner` no
   `defaultConfig` do `app/build.gradle.kts`. Adicionado
   (`androidx.test.runner.AndroidJUnitRunner`) + dependências mínimas de
   `androidTest` (`androidx.test.ext:junit`, `espresso-core`).
3. **`JAVA_HOME is not set`** ao rodar `./gradlew` num terminal externo →
   não é bug do projeto, é ambiente local: resolvido usando o terminal
   embutido do Android Studio (que já usa o JDK dele) e ajustando o PATH.
4. **Erro real de compilação**: `SplashActivity.kt:18:71 Unresolved
   reference: raw` → `res/raw/splash.mp4` não existia (a classe `R.raw` só
   é gerada se houver ao menos um arquivo na pasta `res/raw`). Criei um
   placeholder de texto só pra destravar a compilação (o app cairia direto
   pra tela principal, já que `SplashActivity` trata erro de reprodução).
5. **Imprevisto à parte**: o usuário já tinha colocado o vídeo real na
   pasta antes disso, mas nunca tinha commitado — ao "limpar o diretório"
   antes de um `git pull`, o vídeo (não commitado) foi apagado do disco.
   Como ele tinha feito backup por conta própria, restaurou o arquivo real
   e substituiu o placeholder. **O vídeo real agora está commitado** em
   `android/app/src/main/res/raw/splash.mp4`.
6. Também durante essa sessão: o usuário tentou criar a pasta `/design`
   pela interface web do GitHub digitando só `design` como nome, o que
   criou um **arquivo vazio** (não uma pasta) — removido depois. Lição
   registrada: pra criar pasta pelo GitHub web, o caminho completo do
   arquivo precisa ser digitado (ex.: `design/logo.png`).

**Lição de processo para não repetir**: arquivos binários grandes (vídeo,
imagens) adicionados localmente devem ser commitados e enviados (`git add`
+ `commit` + `push`) o quanto antes — ficar com eles só no diretório de
trabalho é frágil a qualquer limpeza/reset acidental.

**Onde paramos**: aguardando o usuário rodar `./gradlew assembleDebug
--stacktrace` (ou o Run do Studio) de novo agora que o vídeo real está
commitado, para confirmar se o build passa limpo ou se aparece um próximo
erro.

## Estado do repositório

Branch de trabalho: `claude/android-gps-realtime-location-2oymhb`. Scaffold
inicial de `/android`, `/esp32`, `/data-raw`, `/data` e `/tools` criado e
commitado nesta sessão — ver seção acima para o que foi verificado.
