> **✅ JÁ INTEGRADO** — este material foi mesclado em `phone/src/main/res/`
> e `contacts/src/main/res/` (ver seção "TEMA CLÁSSICO" no
> `STATUS-PROJETO.md` pra detalhes de como cada arquivo foi tratado,
> incluindo as 3 colisões de nome que apareceram). Esta pasta fica só como
> referência histórica do material original recebido — não precisa mais
> copiar nada daqui pro projeto.

# Entrega — Tema clássico do Android 1.0/1.6 (Phone + Contacts)

## O que é isto

Você me disse que o tema visual original (a "caixinha" do discador, cores
e texturas clássicas) não aparecia mais no app rodando em Android
moderno, porque antes ele vinha do tema global do sistema operacional
Donut — não do APK. Você mandou o SDK oficial `android-sdk-linux_x86-1.0_r1`
como fonte, e a partir dele eu montei um **tema próprio do app**, que
recria a aparência clássica sem depender do tema do Android instalado no
aparelho.

## De onde veio o material

Do zip que você mandou, a peça-chave foi
`tools/lib/res/default/` — o pacote de recursos do framework que o AAPT
usava para resolver tudo que é `@android:...` durante a compilação.
De lá extraí:

- `values/themes.xml` e `values/styles.xml` (o "Theme" escuro clássico
  do sistema, `TextAppearance.*`, `Widget.*`)
- `values/colors.xml` (as cores base: `background_dark`,
  `bright_foreground_dark`, `dim_foreground_dark`, etc.)
- `color/*.xml` (12 `ColorStateList` de texto, com os estados
  pressed/selected/disabled preservados)
- **602 drawables** de `drawable/` + 2 de `drawable-land/`

Cruzei essa base com **todos** os `@android:drawable/`,
`@android:color/`, `?android:attr/` e `@android:style/` que os layouts
atuais de Phone e Contacts realmente usam, e só então montei o pacote
final — sem trazer nada supérfluo, mas sem deixar nenhuma referência
quebrada.

## Duas peças que o SDK 1.0 não tinha e recriei manualmente

O código do Phone/Contacts que estamos portando é da revisão **1.6
(Donut)**, mas o SDK que você mandou é da revisão **1.0** — duas
peças usadas pelos layouts foram introduzidas só depois do 1.0:

- **`dark_header.9.png`** — faixa sólida cinza-escura (`#ff404040`),
  usada como fundo do cabeçalho "Manage conference" no `incall_screen.xml`.
  Recriei como 9-patch simples, cor consistente com o resto do tema
  escuro (`background_dark` = `#ff191919`).
- **`title_bar_tall.9.png`** — variante mais alta da barra de título,
  usada em `view_contact.xml` e `call_detail.xml`. Recriei a partir do
  `title_bar.9.png` real do SDK 1.0 (mesma cor sólida `#818181`, mesma
  lógica de esticamento), só com a altura de conteúdo maior.

Se você tiver o código-fonte de uma revisão um pouco mais nova (1.5 ou
1.6) depois, dá pra trocar essas duas pelas peças 100% originais — hoje
elas são uma reconstrução fiel, não os arquivos originais literais.

## O que está no zip

```
tema-classico-res/              <- isto é um pacote de recursos "res/"
    drawable/                      602 arquivos (9-patches, XMLs, PNGs)
    drawable-land/                 2 arquivos
    color/                         12 ColorStateList de texto
    values/
        colors.xml                 cores base (background_dark, etc.)
        styles.xml                 TextAppearance.*, Widget.* (prefixo Classic.)
        themes.xml                 Classic.Theme e Classic.Theme.NoTitleBar

manifests-e-styles-editados/    <- referência do que eu já mudei
    phone/
        AndroidManifest.xml        já com android:theme="@style/Classic.Theme"
        styles.xml                 já com os 4 TextAppearance.DialerLine*
                                    apontando pro tema clássico
    contacts/
        AndroidManifest.xml        já com android:theme="@style/Classic.Theme"
        styles.xml                 já com TallTitleBarTheme herdando do
                                    tema clássico em vez do tema do sistema
```

## Como incluir no projeto principal — passo a passo

1. **Copie a pasta `tema-classico-res/`** para dentro de
   `app-project/phone/src/main/res/` — ou seja, o conteúdo de
   `tema-classico-res/drawable/` se mescla com
   `phone/src/main/res/drawable/` (idem para `drawable-land/`, `color/`,
   `values/`). **Copie a mesma pasta também para
   `app-project/contacts/src/main/res/`** — os dois módulos precisam do
   pacote completo, porque cada um compila seu próprio `R.java`.

   Atenção a conflitos de nome: nenhum arquivo deste pacote deveria
   colidir com o que já existe nos dois módulos (usei nomes exatamente
   iguais aos do SDK 1.0 original, que por definição não existiam nos
   módulos antes). Se o merge do Gradle acusar duplicata em algum
   arquivo específico, é sinal de que esse recurso já tinha sido
   parcialmente trazido antes — nesse caso, mantenha a versão deste
   pacote (é a mais completa e testada).

2. **Substitua os dois `AndroidManifest.xml`** pelos que estão em
   `manifests-e-styles-editados/phone/` e
   `manifests-e-styles-editados/contacts/` — ou, se você já tiver outras
   mudanças nesses manifests desde a última entrega, aplique manualmente
   só os três diffs:
   - `<application ...>` ganhou `android:theme="@style/Classic.Theme"`
     (nos dois módulos)
   - `InCallScreen` (Phone) e `DialtactsActivity` (Contacts): o
     `android:theme` mudou de `@android:style/Theme.NoTitleBar` para
     `@style/Classic.Theme.NoTitleBar`

3. **Substitua os dois `values/styles.xml` locais** (o `styles.xml` que
   já existia em cada módulo, não o do pacote de tema) pelos que estão
   em `manifests-e-styles-editados/*/styles.xml` — ou aplique manualmente:
   - Phone: as 4 definições `TextAppearance.DialerLine1/2` e
     `TextAppearance.EmergencyDialerLine1/2` trocaram o `parent` de
     `@android:style/TextAppearance.Widget.Button` para
     `@style/Classic.TextAppearance.Widget.Button`
   - Contacts: `TallTitleBarTheme` trocou o `parent` de
     `android:Theme.NoTitleBar` para `@style/Classic.Theme.NoTitleBar`
     (sem essa troca, `ViewContactActivity` e `CallDetailActivity`
     continuariam com aparência moderna mesmo com o resto do tema
     aplicado — o `parent` explícito sobrescreve a herança do
     `<application>`)

4. **Compile normalmente.** Nenhuma classe Java precisa mudar por causa
   do tema — é 100% recursos (`res/`) e manifest.

## O que NÃO foi tocado

Nenhum layout, string, dimensão ou drawable *específico* do Phone/Contacts
foi alterado — só os recursos de **framework** (`@android:...`) que esses
layouts já referenciavam. O comportamento (motor de chamada,
`DonutCallManager`, `InCallScreen`, etc.) da entrega anterior continua
exatamente como estava.
