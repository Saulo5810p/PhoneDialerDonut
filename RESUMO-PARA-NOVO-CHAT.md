# Resumo do projeto — pra colar num chat novo

## O que é

Recompilar os apps **Phone** e **Contacts** do Android 1.6 (Donut, tag
`android-1.6_r1` do AOSP) como dois **APKs modernos independentes**,
instaláveis **sem root** (minSdk 23, compileSdk 34), **mantendo o layout
visual de 2009 intacto** (telas, botões, cores, ícones). Por baixo, o motor
de chamada antigo (GSM/CDMA/RIL interno) foi reescrito com
`android.telecom.InCallService` (API pública), já que o motor original não é
mais acessível a nenhum app comum, em nenhuma versão do Android.

- `applicationId`: Contacts = `com.xaulinxs.donut.contatos`, Phone =
  `com.xaulinxs.donut.telefoneantigo`
- Build no **Termux** (Galaxy A35), Gradle 9.6.1, AGP 8.7, aapt2 do pacote
  Termux — sem PC, sem Android Studio
- **O projeto ainda não compilou nenhuma vez.** Tudo foi escrito e revisado
  manualmente (chaves balanceadas, imports conferidos), mas sem retorno real
  de compilador ainda.

## Estado atual, por módulo

**Contacts — ✅ completo** (17/17 arquivos). Migrado de
`android.provider.Contacts` pra `ContactsContract`, de API interna de
telefonia pra `TelephonyManager`/`TelecomManager`. Lista principal mostra uma
linha por número de telefone (decisão de produto). `AndroidManifest.xml`
ainda não recebeu a limpeza final.

**Phone — 🔶 quase completo.** Motor de chamada novo em `telecom/`
(`DonutInCallService` é a peça real usada; `DonutCallManager` faz a ponte pra
UI antiga). Núcleo de chamada religado com o menu de opções sempre visível
(pedido do Saulo, já que não tem mais tecla física de Menu). Só restam **5
arquivos** com API interna de verdade — o "peixe grande": `NotificationMgr`,
`PhoneInterfaceManager`, e o subsistema Bluetooth Handsfree inteiro
(`BluetoothHandsfree`, `BluetoothAtPhonebook`, `BluetoothHeadsetService`).

**Tema clássico (Android 1.0/1.6) — ✅ integrado.** 604 drawables + 22 color
state lists + estilos/tema mesclados nos dois módulos. 3 colisões de nome
resolvidas (2 delas mantendo os ícones originais do Contacts em vez dos do
pacote de tema, que eram diferentes por baixo do mesmo nome).

## Decisão de produto importante (vale pra qualquer arquivo novo)

Pra telas que dependem de API `signature`-only sem NENHUM equivalente
público (gestão de PIN/PUK/FDN do SIM, seleção manual de operadora, etc. —
coisas que nem com root dá pra fazer, porque a permissão é do processo de
sistema mesmo): **manter o layout original no app**, mas os botões de ação
viram um aviso — "Eu até consigo portar isso aqui, só não faço milagre"
(`Toast` via helper `phone/.../compat/NotPortedYet.java`, dá pra virar uma
caixinha no estilo do tema clássico depois). Já **API que não existe mais e
não tem layout próprio pra virar aviso** (serviço em background, provider
redundante, etc.) — aí sim sai do código-fonte, vai pra
`_excluded_do_build/` de cada módulo, e fica documentado no
`STATUS-PROJETO.md`.

## Próximos passos, em ordem

1. Migrar os "peixes grandes" finais: `NotificationMgr.java`,
   `PhoneInterfaceManager.java`, e o subsistema Bluetooth Handsfree
   (`BluetoothHandsfree.java`, `BluetoothAtPhonebook.java`,
   `BluetoothHeadsetService.java`).
2. Limpeza grande do `AndroidManifest.xml` do Phone (tirar
   `sharedUserId="android.uid.phone"` e permissões `system`/`signature`-only
   mortas; adicionar as permissões reais — `READ_PHONE_STATE`, `CALL_PHONE`,
   `ANSWER_PHONE_CALLS`, etc. — e o intent-filter de discador padrão) e do
   Contacts.
3. **Primeira tentativa real de compilação no Termux** — vai ser a primeira
   vez que um compilador vê este código. Espere uma rodada de correção de
   erros.
4. Copiar `gradlew`/`gradle-wrapper.jar`/`gradle-wrapper.properties` de outro
   projeto do Saulo que já compila (ainda não incluído em nenhum zip).

## Onde está tudo

O `STATUS-PROJETO.md` (dentro do zip do projeto) tem o detalhe completo,
arquivo por arquivo, de tudo que foi migrado, adaptado ou excluído — este
resumo aqui é só pra dar contexto rápido num chat novo. **Peça pro Claude
ler o `STATUS-PROJETO.md` antes de mexer em qualquer código-fonte.**
