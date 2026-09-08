# STATUS DO PROJETO — Recompilação Android 1.6 Donut (Phone + Contacts)

## OBJETIVO DO PROJETO (leia isso primeiro, sempre)

Recompilar os apps **Phone** e **Contacts** do Android 1.6 (Donut, tag
`android-1.6_r1` do AOSP) como dois **APKs instaláveis sem root** em Android
moderno (minSdk 23, target/compileSdk 34), **mantendo o layout visual de 2009
intacto** (telas, botões, cores, ícones da tela de chamada). Por baixo, o
código pode e deve ser moderno — a Rota A escolhida foi reescrever o motor de
chamada usando `android.telecom.InCallService` (API pública), já que o motor
GSM/CDMA/RIL interno do Android não é mais acessível a nenhum app normal, em
nenhuma versão. `applicationId` customizados: Contacts =
`com.xaulinxs.donut.contatos`, Phone = `com.xaulinxs.donut.telefoneantigo`.
Build: Termux, Gradle 9.6.1, AGP 8.7, aapt2 do pacote Termux — sem Android
Studio.

**Estado geral agora: o projeto NÃO compila ainda.** Ninguém rodou
`./gradlew assembleDebug` neste projeto até hoje — tudo abaixo foi escrito e
revisado manualmente (chaves balanceadas, imports conferidos, API cruzada
verificada à mão), mas sem retorno real de compilador. Espere precisar de uma
rodada de correção de erros de compilação quando isso rodar pela primeira vez
no Termux.

---

## MÓDULO CONTACTS — ✅ COMPLETO (17 de 17 arquivos, ~11.826 linhas)

Todos os 17 arquivos originais foram migrados de `android.provider.Contacts`
(API descontinuada) para `android.provider.ContactsContract` (API atual), e de
`com.android.internal.telephony.*`/`ITelephony` para APIs públicas
(`TelephonyManager`, `TelecomManager`).

- **Fundação criada:** `compat/CallerInfo.java`, `compat/TelephonyCompat.java`
- **Migração mais arriscada:** `EditContactActivity.java` (2202 linhas) —
  `create()` reescrito com `RawContacts`+`StructuredName` (contato local, sem
  conta de sync), `save()` com upsert de nome/nota via tabela `Data`, todo
  insert usando `RAW_CONTACT_ID` e `MIMETYPE` explícitos.
- **`ContactEntryAdapter.java`**: reescrito com `MatrixCursor` que mescla
  Email/Endereço/IM (três tabelas modernas separadas) num cursor só, no
  formato que o resto do código já esperava — evitou reescrever
  `ViewContactActivity`/`EditContactActivity` do zero.
- **Decisão de produto tomada com o Saulo:** lista principal de contatos
  mostra uma linha por número de telefone (não por contato) — mesmo
  comportamento da tela de "escolher telefone".
- **Excluídos do build** (features que o próprio Android removeu, não
  migração): `ContactsGroupSyncSelector.java`, `ContactsLiveFolders.java`
  (Live Folders não existe mais no sistema). Ficam em `_excluded_do_build/`.
- **Achado grande:** `VCardExporter.java` dependia de dois pacotes
  **removidos** do Android (`android.syncml.pim.PropertyNode`,
  `android.util.CharsetUtils`), não só "internos" — mas a geração de vCard já
  era manual via `StringBuilder`, então o impacto real foi pequeno.
- **Pendente:** o `AndroidManifest.xml` do Contacts ainda não recebeu a
  limpeza final (permissões, `sharedUserId`, etc.) — só o do Phone recebeu um
  ajuste pontual até agora (registro do `InCallService`).

---

## MÓDULO PHONE — ✅ COMPLETO (56 arquivos originais, ~18.376 linhas no total)

### Arquitetura de telefonia (a "Rota A" de verdade)

Corrigi uma decisão arquitetural no meio do processo: **não precisamos de
`ConnectionService` próprio** (isso só seria necessário se o app fosse dono de
um jeito próprio de ligar, tipo VoIP). Para usar o **SIM normal do aparelho**
— o objetivo real — a peça certa e suficiente é o `InCallService`: o sistema
já gerencia o rádio/SIM por baixo (isso nunca muda, nenhum app acessa isso
diretamente) e entrega objetos `android.telecom.Call` pro nosso app desenhar a
UI em cima. É o `InCallService` que dá "de graça" o ícone que volta pra tela
de chamada — comportamento nativo, não precisou ser programado à mão.

Arquivos criados em `telecom/`:
- `DonutInCallService.java` — registrado no `AndroidManifest.xml`, é a peça
  real usada.
- `DonutCallManager.java` — ponte/singleton: recebe eventos de `Call` do
  sistema e os republica num formato simples (`Listener`) pra UI antiga
  (`CallNotifier`/`InCallScreen`/`CallCard`) consumir sem conhecer
  `android.telecom` diretamente.
- `DonutConnectionService.java` / `DonutConnection.java` — **criados mas não
  usados no caminho principal**, mantidos só como referência caso um dia se
  queira um "phone account" próprio.

### ✅ Arquivos prontos e revisados (chaves balanceadas, sem resíduo de API interna)

Migração mecânica (24 arquivos sem nenhuma dependência de API interna, mais
correções pontuais):
`BluetoothCmeError`, `IccMissingPanel`, `CarrierLogo`, `OutgoingCallReceiver`,
`ProcessOutgoingCallTest`, `CdmaDisplayInfo`, `IccPanel`, `GetPin2Screen`,
`ButtonGridLayout`, `EditPinPreference`, `Profiler`, `FdnList`,
`DTMFTwelveKeyDialerView`, `CdmaPhoneCallState`, `DeleteFdnContactScreen`
(corrigido: `Window.setFeatureInt`/`PROGRESS_VISIBILITY_*` removido do SDK,
virou no-op), `ADNList` (mesma correção), `SimContacts` (reescrito:
`RawContacts`+`StructuredName` pra criar contato, `PhoneLookup` pra buscar),
`EmergencyDialer`.

Núcleo de chamada (a parte mais arriscada — vieram de outras sessões Claude,
**revisados por mim antes de aceitar**, com pelo menos 2 bugs reais
corrigidos):
- `CallCard.java`, `InCallScreen.java`, `DTMFTwelveKeyDialer.java`,
  `CallFeaturesSetting.java`, `InCallMenu.java`, `InCallMenuView.java`,
  `InCallMenuItemView.java` — **religados por pedido explícito do Saulo**: o
  menu de opções da tela de chamada agora fica **sempre visível** (não
  depende mais de tecla física de Menu, que nem existe nos aparelhos
  modernos) — são os controles reais da tela de chamada.
- `PhoneUtils.java` — **reescrito do zero** (1727→~250 linhas): virou um
  wrapper fino sobre `DonutCallManager`/`android.telecom.Call`. A maior parte
  da lógica GSM/CDMA original não existe mais porque o sistema já resolve
  isso.
- `PhoneApp.java` — **reescrito do zero** (1198→~200 linhas), bem mais
  enxuto: mantém singleton, `Ringer`+`CallNotifier`, rastreamento da
  `InCallScreen` ativa, intents de call log/tela de chamada, wake lock via
  `PowerManager` público (não mais o "poke lock" interno). **Não** instancia
  `NotificationMgr`/`BluetoothHandsfree`/`PhoneInterfaceManager` (nenhum dos
  três migrado ainda) — evitou puxar essa cascata pra dentro desta entrega.
- `CallNotifier.java` — **reescrito do zero** (1193→~140 linhas): vira
  `DonutCallManager.Listener`, toca/para o toque de chamada recebida.
  Funcionalidades CDMA (call waiting tone, signal info) foram cortadas —
  Android moderno não expõe esses eventos de RIL a apps normais.
- `Ringer.java` — corrigido: removido `IHardwareService` (LED de notificação,
  API interna sem substituto público), construtor mudou de `Ringer(Phone)`
  pra `Ringer(Context)`.
- `compat/CallerInfo.java`, `compat/CallerInfoAsyncQuery.java` — criados
  (mesmo padrão do Contacts). **Bug real encontrado e corrigido**: duas
  sessões diferentes usaram assinaturas incompatíveis pra
  `onQueryComplete()` (uma com 2 argumentos, outra com 3) — padronizei em 2
  argumentos e corrigi `DTMFTwelveKeyDialer.java`.

**Excluídos do build** (código morto/obsoleto confirmado, não só "difícil"):
`DialtactsActivity.java` do Phone (comentário do próprio arquivo já dizia
"old cruft", não referenciado em lugar nenhum — nem no manifest, nem em
testes).

### ✅ Rodada de migração "mecânica" (21 de 21 arquivos resolvidos, decisão
### de produto aplicada)

A entrega correspondente estimava 32 arquivos pendentes e "a maioria mecânica"; a
contagem real por grep achou **26**, e a investigação a fundo (checagem na
documentação atual do `TelephonyManager`) mostrou que boa parte esbarrava
numa parede de plataforma, não numa simples troca de API. O Saulo decidiu:
**layouts que ainda existem ficam no app** com os botões de ação virando um
aviso; **API que não existe mais e não tem equivalente sai do código-fonte**,
documentada aqui. Com isso, os 26 (na real, 27 contando o `NetworkQueryService`
que só apareceu depois de separado do `NetworkSetting`) foram todos
resolvidos hoje. Só ficaram de fora, por decisão anterior, os 5 arquivos do
grupo Bluetooth Handsfree (ver "Peixes grandes" abaixo).

**Mecânicos de verdade (6 arquivos)** — trocas diretas, mesmo padrão já
estabelecido (`android.provider.Contacts` → `ContactsContract`,
`Window.setFeatureInt` → no-op, `PhoneFactory` → `TelephonyManager`/nada):
`EditPhoneNumberPreference`, `EditFdnContactScreen`, `SpecialCharSequenceMgr`
(tela do `*#06#` — o IMEI real também não dá mais pra ler em app comum desde
o Android 10, então virou fallback "não disponível" em vez de travar),
`EmergencyCallbackMode` (a lógica de sair do modo callback já vinha
comentada desde 2009, só sobrava um import solto), `OutgoingCallBroadcaster`
(não usava API interna nenhuma de verdade — só um import solto de
`com.android.internal.telephony.Phone` e a classe `android.util.Config`, que
foi removida do SDK moderno e virou uma constante local).

**Layout mantido + botão virando aviso "Eu até consigo portar isso aqui, só
não faço milagre" (9 arquivos)** — novo helper `compat/NotPortedYet.java`
(`Toast` por enquanto; quando o tema clássico entrar dá pra virar uma
caixinha no estilo do diálogo antigo). Todos por causa da mesma parede:
gestão de PIN/PUK/FDN do SIM e seleção manual de operadora são
`signature`-only, sem NENHUM equivalente público em nenhuma versão do
Android, com ou sem root (a permissão é do processo de sistema mesmo).
- `ChangeIccPinScreen`, `EnableIccPinScreen`, `IccPinUnlockPanel`,
  `IccNetworkDepersonalizationPanel`, `EnableFdnScreen` — telas inteiras
  viraram aviso (o layout original continua 100% intacto).
- `FdnSetting` — só os botões de habilitar FDN / trocar PIN2 viram aviso; o
  terceiro item da tela (lista de entradas FDN) continua funcionando normal,
  porque já usa `content://icc/fdn` do sistema, sem depender de PIN2.
- `NetworkSetting` — botões de "buscar redes" e "selecionar automaticamente"
  viram aviso.
- `CdmaOptions` — os dois `ListPreference` de roaming/subscription CDMA
  viram aviso (o terceiro item, cell broadcast, continua indo pro
  `CellBroadcastSms` normalmente).
- `CellBroadcastSms` — as ~30 categorias de canal continuam todas na tela
  (layout intocado), mas qualquer toque vira aviso em vez de tentar
  configurar o RIL (CDMA-only, sem equivalente público de qualquer forma).
- `Settings.java` (tela-mãe) — os dois toggles (roaming, prefer 2G) e o
  roaming CDMA viram aviso e voltam pro estado desmarcado; os atalhos de
  "seleção de operadora" e "APN" continuam navegando normal.
  GSM-vs-CDMA agora é decidido por `TelephonyManager.getPhoneType()`
  (API pública) em vez de `PhoneFactory`.
- `GsmUmtsOptions` — só tirou um import solto de `PhoneFactory` que não era
  usado; a tela em si só navega pra `NetworkSetting` (já resolvido).

**Excluídos do build de vez (5 arquivos), API sem equivalente e sem UI
própria pra virar aviso** — movidos pra `phone/_excluded_do_build/`,
removidos do `AndroidManifest.xml`:
- `CallTime.java` — o próprio comentário de migração do `CallCard.java` já
  dizia que não é mais usado.
- `FakePhoneActivity.java` — não estava no manifest, usa
  `com.android.internal.telephony.test.SimulatedRadioControl`, que só existe
  em emulador com RIL simulado.
- `IccProvider.java` — nossa própria implementação do `ContentProvider` de
  `authority="icc"`, redundante (o sistema já expõe esse mesmo authority) e
  que provavelmente colidiria na instalação.
- `NetworkQueryService.java` + `INetworkQueryService.aidl` +
  `INetworkQueryServiceCallback.aidl` — Service que fazia o scan de
  operadoras via RIL interno; sem UI própria, só era chamado pelo
  `NetworkSetting` (já stubado, não depende mais dele).
- `EmergencyCallHandler.java` — retry de chamada de emergência com rádio
  desligado (`registerForServiceStateChanged`/`setRadioPower`); é um
  `ProgressDialog` transiente sem tela/layout próprio, e não é referenciado
  por nenhum arquivo já migrado — nada aciona ele hoje de qualquer forma.

### ✅ Peixes grandes — TODOS RESOLVIDOS

Os 5 arquivos que dependiam de fato de API interna sem equivalente público
(`NotificationMgr`, `PhoneInterfaceManager`, e o subsistema Bluetooth
Handsfree — `BluetoothHandsfree`, `BluetoothAtPhonebook`,
`BluetoothHeadsetService`) foram fechados em duas sessões:

**`NotificationMgr.java`** — reescrito numa sessão anterior (notificação de
chamada em andamento/chamada perdida via `NotificationManagerCompat`, em vez
de `StatusBarManager` interno). **Faltava uma pendência real**, achada e
corrigida só agora: `PhoneApp.onCreate()` nunca chamava
`NotificationMgr.init(this)` — o arquivo existia mas não era ligado, então
`NotificationMgr.getDefault()` sempre devolvia `null` em runtime. Corrigido.

**`PhoneInterfaceManager.java`** (658→~230 linhas) — o arquivo original era
inteiro um `ITelephony.Stub`, registrado como serviço de sistema `"phone"`
via `ServiceManager.addService()`. Isso não tem substituto possível pra um
app comum (a trava é do Binder do Android, não da versão do SDK — nenhum
processo sem UID de sistema registra um serviço com nome reservado). Nada no
projeto de fato instanciava essa classe (só comentários), então virou uma
classe utilitária estática, mesmo espírito do `PhoneUtils.java`: mantidos
`dial`/`call`/`endCall`/`answerRingingCall`/`silenceRinger`/
`showCallScreen`/`isIdle`/`isOffhook`/`isRinging`/
`cancelMissedCallsNotification`/`getVoiceMessageCount` (únicos com uso real
ou wrapper barato); cortados sem substituto (nenhum tinha 2º call site fora
do AIDL morto): PIN/PUK do SIM, rádio on/off, APN/dados móveis, cell
location, MMI/USSD cru, ERI CDMA, service location, data state/activity.

Efeito colateral encontrado no processo: `SpecialCharSequenceMgr.java`
(arquivo não migrado ainda) chamava `app.phone.handlePinMmi()` — campo
`phone` que já não existe no `PhoneApp.java` reescrito, então esse arquivo
já não compilava de qualquer jeito. Corrigido: a sequência MMI de PIN/PUK
(`**04`/`**05...#`) ainda é reconhecida (não vaza pro discador como número
normal), mas agora só mostra `NotPortedYet`.

**Subsistema Bluetooth Handsfree** (`BluetoothHandsfree.java`,
`BluetoothAtPhonebook.java`, `BluetoothHeadsetService.java`) — implementavam
a stack de AT commands Bluetooth (HSP/HFP) rodada pelo próprio app
(`AtCommandHandler`/`AtParser`/`HeadsetBase`/`ScoSocket`/
`BluetoothAudioGateway`), toda removida do SDK público — o Android moderno
faz esse papel de Audio Gateway no próprio serviço de sistema de Bluetooth.
Sem chamador real fora dos próprios arquivos (`PhoneApp.showBluetoothIndication()`
já retornava `false` fixo, esperando esse momento). Excluídos do build,
removido o `<service>` do manifest. Substituído pelo roteamento de áudio
Bluetooth via `CallAudioState.ROUTE_BLUETOOTH` (API pública do Telecom, mesmo
mecanismo que já resolve o viva-voz) — plugado em `DonutCallManager` (2
getters + 1 setter) → `PhoneUtils` (3 wrappers) → `PhoneApp.
showBluetoothIndication()` → `InCallMenu`/`InCallScreen`, que já estavam
fiados esperando essa fonte real (só trocaram o stub `false`/no-op).

**Conferência final de pendências (nesta sessão, fora do escopo do
Bluetooth mas achado durante a varredura)**: dois resíduos de manifest que
quebrariam a primeira compilação real —
- `AndroidManifest.xml` do Phone citava `<activity android:name=
  "DataRoamingReenable">` e `<activity android:name="RoamingSetting">`, mas
  esses dois arquivos `.java` **nunca existiram no projeto**, nem antes da
  migração — resíduo puro do manifest original do AOSP. Removidos.
- `AndroidManifest.xml` do Contacts citava `<activity android:name=
  "ContactsGroupSyncSelector">`, e pior: `ContactsListActivity.java` ainda
  tinha uma referência direta ativa (`syncIntent.setClass(this,
  ContactsGroupSyncSelector.class)`) a essa classe, que já tinha sido movida
  pra `_excluded_do_build/` numa sessão anterior — isso quebraria o `javac`
  de verdade (o bloco está atrás de `if (mSyncEnabled)`, sempre `false` em
  runtime, mas o Java compila os dois lados do `if` mesmo assim). Item de
  menu removido, `<activity>` removida do manifest.

Também adicionada a permissão `POST_NOTIFICATIONS` (API 33+) no manifest do
Phone e o pedido em runtime em `InCallScreen.onCreate()` (via
`ActivityCompat.requestPermissions`), já que sem isso a notificação do
`NotificationMgr` recém-ligado simplesmente não apareceria em Android 13+.

### ⏳ AndroidManifest.xml do Phone — limpeza grande pendente

Já recebeu o registro do `<service>` do `DonutInCallService`, a permissão
`POST_NOTIFICATIONS`, e a remoção de 2 `<activity>` órfãs (ver acima). Ainda
precisa:
- Remover `sharedUserId="android.uid.phone"` — é um UID de sistema, nenhum
  APK normal consegue usar isso, mesmo assinado.
- Remover/trocar permissões `system`/`signature`-only que um app normal nunca
  vai receber (`MODIFY_PHONE_STATE`, `DEVICE_POWER`, `STATUS_BAR`,
  `INTERNAL_SYSTEM_WINDOW`, `WRITE_SECURE_SETTINGS` etc.) — declarar não
  quebra o build, mas são letra morta.
- Adicionar as permissões reais que o app precisa:
  `READ_PHONE_STATE`/`READ_PHONE_NUMBERS`, `CALL_PHONE`,
  `READ_CONTACTS`/`WRITE_CONTACTS`, `RECORD_AUDIO` (áudio em chamada),
  `ANSWER_PHONE_CALLS`, e o `<intent-filter>` de discador padrão
  (`ACTION_DIAL`, `ACTION_CALL`, `ACTION_VIEW` com `tel:`).

---

## TEMA CLÁSSICO (Android 1.0/1.6) — ✅ INTEGRADO

Os 604 drawables + 2 drawable-land + 22 color state lists do pacote
(`_tema-classico-pendente-integracao/tema-classico-res/`) foram copiados pra
dentro de `phone/src/main/res/` e `contacts/src/main/res/`. Checagem de
colisão de nome (feita por comparação de listagem, arquivo por arquivo) achou
**3 colisões reais em 631**, tratadas assim:

- **`picture_emergency.png`** (Phone) — bytes idênticos (mesmo md5) nos dois
  lados. Sem risco, mantido como estava.
- **`sym_action_map.png`** e **`sym_action_sms.png`** (Contacts) — bytes
  **diferentes** dos que já existiam. Os arquivos já presentes no módulo são
  os ícones de ação originais do Donut 1.6 (referenciados direto por
  `R.drawable.sym_action_map`/`sym_action_sms` em `ViewContactActivity.java`
  e `CallDetailActivity.java`, parte do módulo Contacts já ✅ completo). Os
  do pacote de tema vêm do *framework* do SDK 1.0 (`@android:drawable/...`),
  mesmo nome por coincidência mas artefato diferente. **Decisão: mantive os
  originais do Contacts, não copiei a versão do pacote de tema** — evita
  trocar um ícone específico do app por um genérico de sistema sem
  necessidade, numa parte do código que já estava fechada.

`values/colors.xml` e `values/styles.xml` do pacote **não substituíram** os
arquivos dos módulos (que já tinham conteúdo próprio: cores do dialer/DTMF
no Phone, cores de overlay de ícone no Contacts) — o conteúdo do pacote foi
**mesclado dentro** dos arquivos existentes de cada módulo, sem colisão de
nome de resource (`Classic.*` nos estilos, nomes genéricos como
`background_dark`/`black`/`white` nas cores — nenhum coincidia com o que já
existia). `values/themes.xml` não existia em nenhum dos dois módulos, então
foi cópia direta.

Manifests e `styles.xml` locais receberam exatamente os 3 diffs descritos no
`LEIA-ME-tema-classico.md` (`android:theme="@style/Classic.Theme"` no
`<application>` dos dois módulos; `InCallScreen`/`DialtactsActivity` trocando
pra `@style/Classic.Theme.NoTitleBar`; os 4 `TextAppearance.DialerLine*`/
`EmergencyDialerLine*` do Phone e o `TallTitleBarTheme` do Contacts trocando
o `parent` pro tema clássico) — **não usei os `AndroidManifest.xml` prontos
do pacote de referência**, porque eles eram de antes da limpeza de hoje
(ainda tinham `IccProvider`, `NetworkQueryService`, `EmergencyCallHandler`
declarados) — apliquei só os diffs de tema por cima do manifest atual.

Todos os 387 XMLs de recurso dos dois módulos + os dois `AndroidManifest.xml`
foram validados como XML bem-formado depois da integração (achei e corrigi
um `--` dentro de comentário do manifest do Phone, que é inválido em XML).
Nenhuma classe Java precisou mudar — é 100% recurso e manifest, como o
`LEIA-ME` já avisava.

---

## PRÓXIMOS PASSOS, EM ORDEM

1. Limpeza grande do `AndroidManifest.xml` do Phone (permissões,
   `sharedUserId`, intent-filter de discador padrão) e do Contacts.
2. Copiar `gradlew`/`gradle-wrapper.jar`/`gradle-wrapper.properties` de outro
   projeto do Saulo que já compila (ainda não incluído em nenhum zip).
3. **Primeira tentativa real de compilação no Termux** — vai ser a primeira
   vez que um compilador de verdade vê este código. Esperem uma rodada de
   correção de erros; nada disso foi testado, só revisado manualmente (chaves/
   parênteses balanceados e XMLs bem-formados foram conferidos à mão em toda
   a árvore ativa, mas isso não substitui um `javac`/`aapt2` de verdade).

