# Observer Notes: registro de progresso

> Atualizado ao final de cada etapa. Este arquivo acompanha todos os ZIPs de segurança.
> **Última atualização: mural Little Notes e remoção da Constelação (25/11/2026).**

## 1. Visão geral

Aplicativo desktop em **Java 17 / Swing (Java2D)**, projeto **Maven**, to-do list kawaii inspirada em jogos Flash dos anos 2000.
Requisitos acadêmicos: padrão **Observer** real, princípios **SOLID**, persistência local em **JSON (Gson)**, player de música local.
Regra de trabalho: etapas pequenas, cada uma com ZIP de segurança independente (`observer-notes-etapa-N.zip`, contendo o projeto inteiro).
**Não avançar de etapa sem autorização do usuário.**

## 2. Etapas

| Etapa | Conteúdo | Estado |
|---|---|---|
| 1 | Projeto Maven, assets organizados, `AssetCatalog`, janela com moldura em escala | Concluída |
| 2 | Domínio, publicador de eventos e observadores (hoje: 3), 61 testes originais | Concluída |
| 3 | Persistência JSON/Gson, autosave, restauração segura, preferências dos observadores | Concluída |
| 4A | Interface: layout, posicionamento dos PNGs, campo de texto, botões e componentes visuais (sem lógica de negócio) | **Concluída** |
| 4B | Integração com `AppCore`: criar/concluir/excluir tarefas, toggles dos observadores, CLEAR, navegação, rodapé, avisos | **Concluída** |
| 4C.1 | Animações (tarefas, estrelas, medalhas, conquista máxima), hover/pressionado, rolagem do histórico, teclado e foco visível | **Concluída** |
| 4C.2 | Transições entre painéis, fontes originais, polimento tipográfico/visual | Pendente (aguarda autorização) |
| 5 | Player musical funcional (javax.sound + MP3SPI), playlist, persistência, painel da playlist | **Concluída** |
| 6 | Acabamento visual: fontes originais, bases de tarefa uniformes, hover/foco delicados, botão de play, margens | **Concluída** |
| 7 | README final, diagramas UML, `observer-notes-final.zip` | Pendente |

## 3. Funcionalidades implementadas

**Domínio (Etapa 2)**
- `TaskBoard`: até 6 tarefas (concluídas ou não), UUID imutável, título 1 a 60 caracteres, nunca reemite um ID já usado.
- `AchievementLedger`: única autoridade de pontuação. Guarda os IDs que pontuaram, em ordem. Marco = posição. Máximo 16.
- 16 marcos: 3 estrelas, bronze, 3 estrelas, prata, 3 estrelas, ouro, 3 estrelas, conquista máxima. Depois: nada.
- Só a primeira conclusão de uma tarefa pontua. Desmarcar e remarcar, excluir, cliques repetidos ou várias threads nunca pontuam de novo.
- Pontuação é decidida no domínio **antes** de publicar eventos; falha de observador não perde ponto.
- Observer: `SynchronousEventBus` (Subject + EventPublisher), falhas isoladas por observador. Observadores: Histórico, Contador e Conquistas.
- Contador (tarefas concluídas agora) é separado do total histórico de conquistas.

**Persistência (Etapa 3)**
- `state.json` (tarefas, ledger, histórico), `settings.json` (preferências dos observadores) e `notes.json`
  (até três bilhetes do Little Notes), na pasta de dados do usuário
  (`UserDataDirectory`: Windows `%APPDATA%\ObserverNotes`, macOS `~/Library/Application Support/ObserverNotes`,
  Linux `$XDG_DATA_HOME/observer-notes` ou `~/.local/share/observer-notes`; sobrescrevível por `-Dobservernotes.data.dir=...`).
- **Fonte única de verdade das conquistas:** `achievements.scoredTaskIds` (ordenada). Marcos, estrelas e medalhas são derivados e **não** são gravados.
  Nunca se reconstrói o total contando tarefas existentes.
- Escrita atômica (`AtomicFileWriter`: temporário, `force`, `ATOMIC_MOVE`), backup da última versão boa (`.bak`).
- Leitura estrita (UTF-8, JSON estrito, tipos e campos obrigatórios, UUID canônico), validação pelas regras do domínio
  (`DomainRestorer`) **antes** de aplicar, versionamento (`formatVersion`) e migrações em cadeia (`Migration`; ainda não há nenhuma, o formato atual é 1).
- Arquivo ausente: começa do zero. Arquivo inválido, inconsistente ou de versão mais nova: **nunca é apagado**, é movido para `nome.corrupt-<data>`;
  tenta o backup; senão começa do zero. Se não der para ler nem preservar, **as gravações são bloqueadas** (não sobrescreve).
- `AutoSavePublisher` (Decorator do publicador): grava depois que todos os observadores rodaram; `MilestoneUnlocked` não regrava (o ponto já estava no ledger).
  Falha de gravação nunca chega ao domínio; a próxima alteração tenta de novo.
- Restaurar: domínio primeiro, depois projeções dos observadores; **nenhum evento é emitido e nenhum ponto concedido**; não grava na partida (exceto para curar após recuperar do backup).
- `ObserverRegistry`: liga/desliga os três observadores (cancela/refaz inscrição, resync a partir do domínio, sem eventos). Desligar nunca altera conquistas. A antiga chave `CONSTELLATION` em `settings.json` é ignorada ao abrir.
- `AppCore`: raiz de composição do núcleo (ainda não usada pela interface).

## 3b. Etapa 4A: interface visual (14/11/2026)

**O que foi feito** (tudo em `com.observernotes.ui`, sem tocar em domínio, observadores ou persistência):
- `Layout`: área lógica 1397x742 e todos os retângulos dos elementos, medidos no mockup e convertidos por **uma única função** (`Layout.mock`); corrigir um desvio é mudar um número.
- `NotebookCanvas`: mantém a escala proporcional com letterbox (a moldura nunca deforma) e passou a ter foco, cursor piscando, hover/pressionado e colar (Ctrl/Cmd+V) no campo de texto.
  Um gancho `setButtonListener` recebe cliques completos; **nenhum ouvinte é registrado na 4A**.
- `assets/SliceSpec`, `SliceRenderer`, `SpriteCache`: 3-slice (pontas escaladas, miolo esticado ou repetido para preservar o tracejado), sprites já no tamanho exato em pixels, cache e estados NORMAL/HOVER/PRESSED.
- `render/Surface`: desenho em coordenadas lógicas (bitmaps nítidos em pixels reais; formas e textos vetoriais).
- `scene/MainScene` + `ProceduralArt`: logo, botões LIST/ALERTS/RECORD, campo de texto, ADD, painel central nos três modos (Tarefas, Observadores, Histórico), painel Resumo, rodapé.
  Desenhados em Java2D (não havia PNG): painéis, barra de progresso listrada, lápis, pílulas do player e do horário, brilhos.
- `model/*`: `ScreenModel` (dados puros que a cena desenha), `PanelMode`, `TaskRowModel`, `ObserverCardModel`, `HistoryRowModel`, `AchievementTier`. Sem dependência do domínio: a 4B cria um adaptador.
- `widget/*`: `TextInput` (texto, cursor, limite de 80, sem tratar Enter), `InteractionState`, `ButtonId`.
- `Fonts`/`TextPainter`: fonte provisória (sans-serif do Java); se `assets/fonts/Cakerolli.(ttf|otf)` e `CloudSoft.(ttf|otf)` existirem, são carregadas automaticamente. Texto com contorno por `GlyphVector`.
- Assets limpos (originais preservados): `campo_texto_limpo.png` (sem o coração) e `card_observador_limpo.png` (sem o cursor).
- **Conquistas:** `AchievementTier` escolhe o PNG do ícone: `NONE` = roseta vazia (só quando não há conquistas), `BRONZE`/`SILVER`/`GOLD` = medalhas, `MAX` = conquista máxima.
  A 4B deve mapear o `AchievementProgress` para o tier. As estrelas 3x entre medalhas ficam para a 4C.
- **Player:** `PLAYER_PILL` reserva o espaço dos controles (anterior/tocar-pausar/próximo) e `FOOTER_MESSAGE` o da mensagem. Os controles são **só visuais** (sem hover, sem clique, sem som); `base-rodape.png` (trilho) ainda não é usado.
- Tela real: `NotebookCanvas` começa com `ScreenModel.empty()` (6 espaços livres, 0/0, 3 observadores ligados, Little Notes vazio, "Nenhum evento ainda"). Os dados do mockup existem **só** em `test/.../tools/MockupSample`.

**Prévias finais** (geradas headless por `PreviewRenderer` com os dados do mockup): `docs/previews/etapa4A-tasks.png`, `etapa4A-observers.png`, `etapa4A-history.png`, e `etapa4A-tasks-vazia-700x400.png` (estado real, janela pequena).

**Fidelidade:** posições medidas no mockup; as prévias foram comparadas lado a lado e ajustadas (botões da esquerda deslocados para não cobrir as argolas, CLEAR dentro do painel, sparkle após o texto etc.).
Diferenças conhecidas daquela etapa: fonte provisória (largura e arredondamento diferentes das originais); painéis desenhados em Java2D sem o recorte ondulado do topo; o antigo ícone da constelação e o coração do rodapé eram ampliados de PNGs pequenos (≈1,6x e ≈1,7x; levemente suaves); Abajur substituído por Conquistas (decisão anterior); rodapé mostrava o botão de pausa (PNG fornecido) onde o mockup tinha outro ícone. A Constelação foi removida posteriormente; veja 3f.

**Não implementado na 4A (de propósito):** criar/concluir/excluir tarefas, Enter no campo, ADD/LIST/ALERTS/RECORD/CLEAR com ação, toggles, X de excluir, navegação entre painéis, animações, som.
Botões LIST/ALERTS/RECORD/ADD têm apenas feedback de hover/pressionado. CLEAR, toggles, X e controles do player são desenhados sem reação ao mouse (não fingem funcionar). "NOVA!" só aparece com `newAchievement`.

## 3c. Etapa 4B: integração funcional (14/11/2026)

O app agora é uma to-do list real. Layout, assets e proporções da 4A **não mudaram**: as prévias de dados de exemplo da 4B são pixel a pixel idênticas às aprovadas na 4A (verificado por comparação de imagens).

**Como está ligado** (nenhuma regra de negócio foi duplicada; a UI só chama `AppCore`/`TaskService`/`ObserverRegistry`):
- `app/Bootstrap` monta o `AppCore` com `JsonStateRepository`/`JsonSettingsRepository` na pasta de `UserDataDirectory.resolve()`; `Main` usa isso, mostra erro de partida compreensível (e sai) se o núcleo não subir.
- `MainWindow` cria `NotebookCanvas` + `AppController`; mostra `startupMessages()` (arquivo recuperado do backup, inválido preservado como `.corrupt-...`) em uma caixa de diálogo.
  Ao fechar, grava de novo; se não conseguir, pergunta antes de fechar para não perder dados sem avisar.
- `ui/controller/AppController`: traduz `UiAction` (clique/Enter) em chamadas ao núcleo e monta o `ScreenModel` (via `ScreenAssembler`). Só tem navegação, texto do campo e avisos.
- `ScreenAssembler`: lista de tarefas vem do `TaskService`; contador do `CounterObserver`; histórico do `HistoryObserver`; conquistas do `AchievementObserver`; bilhetes chegam prontos do `LittleNotesService`.
- `FooterMessages`: observador próprio da UI para a mensagem do rodapé (continua funcionando com o Histórico desligado). Na partida usa a entrada mais recente do histórico restaurado.
- `SaveFailureTracker`: recebe os avisos de falha de gravação (estado e preferências) vindos do `AppCore`.
- Cliques usam a **chave da tarefa (UUID) do modelo**, nunca o índice da linha: um clique antigo numa tarefa já excluída não afeta nenhuma outra. Itens agem só ao soltar sobre o mesmo item pressionado.

**Funcionalidades**
- Campo de texto: foco, digitação, cursor, colar; limite de 60 caracteres (o mesmo do domínio). ADD ou Enter cria; título vazio é recusado com aviso; ao atingir 6 tarefas o campo mostra "Limite de 6 tarefas..." e o texto digitado não se perde.
- Checkbox marca/desmarca, X exclui; lista e espaços livres atualizam na hora.
- Contador (nuvem, coração e barra) vem do `CounterObserver`.
- Conquistas: ícone por `AchievementTier` (`NONE` roseta vazia só com 0; `STARS` estrela dourada enquanto só há estrelas; bronze/prata/ouro; máxima) e texto "n de 16". Separado do contador atual.
- ALERTS: três interruptores (Histórico, Contador e Conquistas) ligados ao `ObserverRegistry`; salvam `settings.json` automaticamente. Desligado, o painel mostra "desligado" em vez de dados velhos; religar ressincroniza com o estado atual. O domínio e o ledger nunca são afetados.
- RECORD: eventos reais (mais recente primeiro; as 6 mais recentes cabem na tela); CLEAR limpa só o histórico e grava.
- LIST/ALERTS/RECORD alternam os painéis sem perder dados. Hover/pressionado em LIST, ALERTS, RECORD, ADD e CLEAR.
- Rodapé: última mensagem real ("21:40 · ✓ Concluída: ...", criada, desmarcada, excluída, nova conquista). Avisos de validação duram 4 s.
- Falha de gravação: nunca é mostrada como salva. O rodapé exibe "Falha ao salvar: as alterações ainda não estão no disco" (estado) ou "...as preferências..." até uma gravação seguinte dar certo; a tela mantém os dados da sessão.
- **Player e NOVA!:** continuam só visuais (sem clique, sem som). "NOVA!" não aparece na tela real (nenhum uso fictício).

**Prévias** (`docs/previews/`, sessão real executada por `tools/RealSessionPreview`): `etapa4B-tasks.png`, `etapa4B-observers.png`, `etapa4B-observers-desligados.png`, `etapa4B-history.png`, `etapa4B-limite.png`.

## 3d. Etapa 4C.1: interações e conquistas (15/11/2026)

**Celebrações (só para fatos reais).** O controlador assina os eventos do domínio (`CelebrationCollector`). Só um `MilestoneUnlocked` publicado pelo `TaskService` (conclusão **inédita**) dispara celebração, e apenas se o observador de Conquistas estiver ligado.
Estrela 1/2/3: estrela dourada que "salta", brilhos orbitando e lampejos; a quarta conclusão vira medalha de **bronze**, depois **prata**, **ouro** e **conquista máxima** (raios, anel, confete de corações/estrelinhas, PNGs `ACH_*` reais, título "Medalha de ouro!" etc.).
Desmarcar/remarcar, excluir, reiniciar o app e abrir telas **não** repetem celebrações (a restauração não publica eventos). A pontuação continua decidida e gravada pelo domínio antes de qualquer notificação; as regras de pontuação não foram alteradas.

**Feedback visual leve (não bloqueia a interface).** Criar: linha surge com fade/deslize. Concluir: check "pula" com brilhos. Excluir: linha-fantasma esvanece. Duração entre 0,3 e 3,6 s; no máximo 24 efeitos simultâneos; o timer de 16 ms só roda enquanto há efeito ativo.
Hover: halo + leve aumento em checkbox, X e interruptor; pressionado: encolhe. Ações de itens continuam acontecendo ao **soltar** sobre o mesmo item.
Isolamento: o controlador chama os efeitos dentro de `try/catch`; um efeito com defeito não altera estado, pontuação nem gravação (testado com um sink que lança exceção).

**Histórico.** O observador mantém até 200 eventos; a tela mostra 6 por vez e rola com a roda do mouse, ↑/↓, PgUp/PgDn, Home/End. Barra de rolagem só aparece com mais de 6 eventos. Rolar não grava nada; CLEAR zera a rolagem e continua funcionando.

**Teclado e foco.** Tab/Shift+Tab: LIST, ALERTS, RECORD, campo, ADD, depois os itens do painel atual (tarefas: checkbox e X de cada uma; observadores: 3 interruptores; histórico: CLEAR) e os controles do Little Notes. Anel de foco visível em cada um.
Regras de segurança: **Enter nunca marca nem exclui tarefa**; Espaço marca/desmarca; excluir (X) e CLEAR por teclado exigem **Espaço duas vezes em até 4 s** (a primeira mostra uma dica no rodapé); com o foco no campo, as teclas só editam e Enter adiciona; Esc limpa o foco; clicar com o mouse esconde o foco de teclado.

**Previews (`docs/previews/etapa4C1-*.png`):** tarefas criar/concluir/excluir, estrela 2 e 3, bronze, prata, ouro e conquista máxima (cada folha mostra vários instantes). Foram geradas por `tools/AnimationPreview` com relógio manual (sem janela).
**Layout:** a renderização sem hover/foco/efeitos é pixel a pixel idêntica às prévias aprovadas (história, observadores e tarefas); a única mudança estática é a barra de rolagem com mais de 6 eventos.

## 3e. Etapa 5: player musical funcional (15/11/2026)

**Decisões.** O botão **NOVA!** agora é "adicionar músicas" (abre o seletor de arquivos do sistema, vários de uma vez). O título da faixa fica num trecho à direita do rodapé; um **aviso ativo ocupa a largura toda e esconde o título** (nunca se sobrepõem). Clicar no título abre/fecha um **painel pequeno da playlist** sobre o painel central. Sem player (testes antigos), o rodapé é idêntico ao da 4C.1 (verificado pixel a pixel).

**Pacote `com.observernotes.music` (sem dependência do domínio das tarefas nem da pontuação):**
`AudioBackend`/`JavaSoundBackend` (uma thread de áudio daemon por faixa; WAV/AIFF/AU nativos; MP3 via MP3SPI registrado no `javax.sound`), `PcmSource` (converte para PCM 16 bits), `VolumeScaler` (volume por software, ganho = volume²), `AudioSink`/`LineSink` (placa de som, substituível em testes), `Playlist` (pura), `PlayerEngine` (estados, avanço automático, repetição, faixas ausentes puladas, callbacks atrasados ignorados), `JsonPlaylistStore` (`music.json`, gravação atômica, arquivo corrompido vira `.invalido`), `MusicPlayer` (fachada: engine + persistência). Interface: `PlayerUi` (ações, painel, efeitos), `PlayerModel`.

**Funciona:** tocar/pausar, anterior/próxima (com volta), avanço automático ao terminar, repetir playlist, volume (−/+, barra clicável, roda do mouse sobre o player), nome da faixa, adicionar várias, escolher qual tocar (clique na linha), remover, subir/descer na ordem, rolagem da playlist (roda, setas, PgUp/PgDn, Home/End), Esc fecha. A música continua ao navegar entre LIST/ALERTS/RECORD (que apenas fecham a playlist). Não toca sozinho ao abrir.
**Persistência:** só referências (caminhos) aos arquivos, posição, volume e repetição, em `music.json` ao lado de `state.json`; nada é copiado. Arquivos movidos/apagados aparecem como "(indisponível)", são pulados e avisados no rodapé; arquivo de playlist ilegível não trava o app (aviso na abertura).
**Feedback visual:** pulso no botão ao tocar/pausar, balãozinho "♪ título" ao trocar de faixa, notinha balançando enquanto toca, hover/pressionado/foco nos controles. O botão "tocar" não existe como PNG: é desenhado no mesmo estilo (o PNG de pausa é usado enquanto toca).
**Teclado:** Tab inclui anterior, tocar/pausar, próxima, NOVA! e título (por último); com a playlist aberta, os itens dela substituem os das tarefas. Enter nunca remove uma faixa (só Espaço).
**Recursos:** `JavaSoundBackend.close()` para a thread, esvazia e fecha a linha e o arquivo; trocar de faixa fecha a anterior; ao sair do app `MusicPlayer.close()` é chamado antes de `System.exit`.

**Executável.** `pom.xml` inclui `mp3spi` (runtime) e `maven-shade-plugin` (com `ServicesResourceTransformer`, essencial ao MP3). `mvn package -DskipTests` gera `target/observer-notes-0.1.0.jar` (`java -jar`); `executar.bat`/`executar.sh` fazem isso e abrem o app. `dist/observer-notes-sem-mp3.jar` foi montado aqui (sem Maven) com Gson incluído: **só WAV/AIFF/AU**; verificado apenas que o JAR inicia (até criar a janela, que o ambiente sem tela não permite).

**Previews:** `docs/previews/etapa5-*.png` (rodapé parado, tocando com balão, playlist, rolada/pausada, vazia, foco no histórico), gerados por `tools/PlayerPreview`.

## 3f. Etapa 6: acabamento visual (15/11/2026)

Somente aparência; domínio, Observer, persistência, player e regras ficaram intactos (os 393 testes anteriores continuam passando, mais 3 novos de fontes/assets: 396).

**Fontes originais (`src/main/resources/assets/fonts/`, carregadas de dentro do app, sem depender do Windows):**
- **Cloud Soft Bold** (700): títulos, faixas, botões (LIST/ALERTS/RECORD/ADD/NOVA!), contadores e indicadores.
- **Cloud Soft Light** (300): textos escritos: título das tarefas, descrições, avisos do rodapé, histórico, título da faixa.
- **Cakerolli: não pôde ser usada, e isso precisa ficar claro.** O ZIP contém a versão **DEMO** (18 estilos, todos os pesos e itálicos). Conferi o arquivo: nessa DEMO os glifos de **todas as vogais (a e i o u, maiúsculas e minúsculas), dos números 0-9 e das letras acentuadas estão em branco** (o README do fabricante diz "demo, sem suporte completo de caracteres"). Textos como "Estudar o Observer" saem como "stdr bsrvr". Por isso ela não serve para nenhum texto em português e foi **deixada fora do JAR/ZIP**. O app já está preparado: se você tiver a versão completa, coloque-a como `src/main/resources/assets/fonts/Cakerolli-SemiBold.otf`; `Fonts` verifica se vogais, números e acentos são desenhados e, se forem, passa a usá-la nos textos escritos no lugar da Cloud Soft Light (se não forem, ignora o arquivo sozinho).
- Nenhuma das duas fontes tem ✓ nem ♪: `TextPainter` desenha esses símbolos com uma fonte do sistema do mesmo tamanho (antes sairiam como quadradinhos).
- Formato: OTF (CFF) via `Font.createFont`, suportado pelo Java 17.

**Licenças e restrições (`docs/fonts-licencas/`):** Cloud Soft: "Free for personal use" (versão de teste; versão completa à venda no link do README). Cakerolli DEMO: demonstração; uso comercial exige licença do fabricante (jipatype.com/cakerolli). **Estas fontes NÃO estão liberadas para uso comercial nem para redistribuição.** O projeto é pessoal/educacional; antes de qualquer distribuição pública, compre as licenças ou troque as fontes. O JAR e o ZIP contêm Cloud Soft: não os publique.

**Ajustes visuais:**
1. **Bordas:** botão X, checkbox e interruptores ganharam margem interna maior (X e interruptor a 0,45 da altura da borda; interruptor 11% menor); cliques seguem as mesmas áreas desenhadas.
2. **Linhas de tarefa:** as bases "pendente" e "concluída" tinham margens transparentes diferentes (a concluída parecia deslocada e maior). Ambas foram recortadas e padronizadas em 504x96 px; a linha vazia ("espaço livre") agora usa **a mesma base**, só esmaecida, em vez de outro componente. Mesma largura, altura e estrutura nos três estados (originais guardados em `assets/ui-originais/`).
3. **Hover/foco:** o círculo rosa preenchido foi removido. Hover = leve aumento (6%) + contorno fininho translúcido; foco de teclado = contorno lavanda com halo branco fino.
4. **Botões:** ícones LIST/ALERTS/RECORD com caixa de mesmo tamanho e posição, texto centrado no espaço restante.
5. **Play:** novo `tocar.png`, feito com o círculo e o triângulo do botão "próximo" (mesmo roxo, brilho e contorno), no lugar do botão rosa desenhado por código. Pausa/anterior/próximo inalterados; a linha da faixa em reprodução na playlist usa o mesmo ícone.
6. **Previews antes/depois:** `docs/previews/etapa6-comparativo-*.png` (em cima: 4A; embaixo: agora), `etapa6-*.png` e `etapa6-player-*.png`.

**Limitações visuais:** Cloud Soft Light é mais fina que o ideal para textos pequenos (legível nos tamanhos usados; a Cakerolli completa resolveria); o balãozinho de troca de faixa continua cobrindo brevemente o painel; não houve teste em janela real (previews gerados sem tela).

## 3g. Ajustes finais: playlist padrão, rodapé e tipografia (15/11/2026)

Entrega única, sem etapa nova. Domínio, Observer, conquistas e persistência de tarefas **não foram tocados**.

**Estado verificado ao retomar:** nenhum arquivo havia sido alterado desde `observer-notes-etapa-6.zip`; tudo abaixo foi feito do zero a partir da Etapa 6.

1. **Playlist padrão** (`music/DefaultPlaylist`, `assets/music/`): 7 MP3 + `playlist.txt` (`arquivo|título`). Na 1ª execução são extraídos para `<dados>/musicas-padrao/` e adicionados **depois** das músicas do usuário, sem duplicar, sem autoplay; o marcador `musicas-padrao.ok` evita que voltem após serem removidas. `JsonPlaylistStore` agora preserva o título gravado (antes recalculava pelo nome do arquivo). Faixa duplicada (`Bossa_Nova_Menu_1`) não incluída. MP3 recodificados em 96 kbps (limite de 30 MiB), tags originais mantidas. **Direitos de redistribuição NÃO verificados** (faixas do Suno): ver `docs/musicas-padrao-creditos.md`.
2. **Rodapé** (`ui/FooterRotation`, `MainScene.footer`): controles roxos | área única de informação | botão amarelo **♪ Playlist** (mesmo PNG amarelo do antigo NOVA!). Alternância a cada 6 s só enquanto toca; pausado/parado = só notificação; notificação nova entra na hora e fica ≥ 6 s (um aviso passageiro do app também fica retido 6 s); começar a tocar mostra o nome na hora se a notificação já passou de 6 s; fade de 450 ms; "…" em textos longos sem mudar o tamanho do rodapé. Relógio injetável; timer de quadros só roda durante fade/reprodução. Sem eventos no histórico. O trecho clicável do título foi removido; o foco por Tab acompanha a nova ordem.
3. **+ Música** agora fica no painel da playlist (pílula amarela acima da primeira linha, `Layout.ADD_MUSIC`); só existe com o painel aberto.
4. **Tipografia (revisada no mesmo dia):** somente **Cloud Soft Bold** (`Fonts.display/body/secondary`; body a 92% e secundário a 90% do tamanho pedido). Contorno colorido só em títulos e botões; textos dinâmicos sem contorno; concluídas com tachado e cor suave; placeholders discretos. Nunito e Cloud Soft Light descartadas (arquivos e README do Nunito removidos).
5. **Títulos longos de tarefa:** reduz a fonte no máximo a 86% (24 px) e depois corta com "…"; a linha mantém o mesmo tamanho; tachado preservado; tooltip com o título completo só quando cortado.

**Testes (somente os afetados + novos; a bateria completa não foi repetida):** 84 testes das classes tocadas passaram (novos: `FooterRotationTest` 5, `TextEllipsisTest` 3, `DefaultPlaylistTest` 4; ajustados: `FontsTest`, `PlayerCanvasTest`). Previews: `docs/previews/etapa7-*.png` (geradas com a fonte provisória, pois o Nunito não estava disponível; para economizar espaço, previews antigas foram removidas deste ZIP).

**Limitações:** sem janela nem áudio reais neste ambiente (MP3 não testado aqui; tooltip testado só pela lógica); Maven/JUnit oficiais não executados (executor mínimo).

## 3f. Little Notes e simplificação dos observadores (25/11/2026)

- A antiga Constelação foi removida do domínio de observação e da interface. Antes da remoção foi
  confirmado que pontuação e conquistas dependem exclusivamente de `AchievementLedger`,
  `TaskService` e `AchievementObserver`; nenhuma regra de pontos foi alterada.
- O painel ALERTS agora tem três cartões do mesmo tamanho (Histórico, Contador e Conquistas),
  centralizados com espaçamento vertical maior. Preferências antigas que ainda contenham
  `CONSTELLATION` são abertas normalmente: a chave desconhecida é ignorada.
- O espaço da antiga Constelação no Resumo virou **Little Notes**: até três bilhetes rosa, amarelo e
  lilás, desenhados em Java2D com leve rotação, sombra e fita. O estado vazio é um bilhete tracejado
  sem cobrança. Texto dinâmico usa Cloud Soft Bold sem contorno.
- Clique edita no próprio bilhete usando o `TextInput` existente; Enter confirma e Esc cancela.
  Cada bilhete aceita até 60 caracteres. A exibição usa reticências quando necessário e oferece o
  texto completo em tooltip. `×` aparece em edição, hover ou foco; `+` some ao chegar a três.
- O mural é independente: não publica `DomainEvent`, não toca a pontuação, não cria histórico,
  conquistas ou mensagens no rodapé. O rascunho do campo de tarefa é preservado durante a edição.
- `notes.json` usa `FORMAT_VERSION = 1`, `JsonFileStore` e `AtomicFileWriter`, separado de
  `state.json`. Arquivo inválido é preservado como `.corrupt-*`; backup válido é recuperado seguindo
  a mesma estratégia do estado principal.
- Verificação localizada: compilação Java 17 e 29 testes selecionados (repositório/backup,
  limites 3/60, compatibilidade de settings, controlador sem eventos/pontos, foco, hit-test e
  renderização 0/1/3) passaram. A bateria completa não foi executada.
- Prévias localizadas são geradas por `com.observernotes.tools.LittleNotesPreview` em
  `target/little-notes-preview/`.

## 4. Estrutura do projeto

```
observer-notes/
├─ pom.xml                         Java 17, JUnit 5.10.2 (test), Gson 2.10.1, surefire 3.2.5
├─ docs/  PROGRESSO.md, identidade-visual.md
└─ src/
   ├─ main/java/com/observernotes/
   │  ├─ app/          Main, Bootstrap (monta o AppCore com os repositórios JSON)
   │  ├─ domain/       TaskId, TaskView, Task, TaskBoard, CompletionResult, BoardFullException, InvalidStateException,
   │  │                Milestone, RewardType, AchievementLedger, AchievementProgress, DomainState, DomainRestorer
   │  ├─ domain/event/ DomainEvent (sealed), TaskCreated, TaskCompleted, TaskUncompleted, TaskDeleted, MilestoneUnlocked
   │  ├─ observer/     Observer, Subject, EventPublisher, Subscription, SynchronousEventBus, ObserverFailureHandler
   │  ├─ observer/impl HistoryObserver(+HistoryEntry), CounterObserver, AchievementObserver
   │  ├─ notes/        LittleNote, NotesRepository
   │  ├─ settings/     ObserverKind, ObserverPreferences
   │  ├─ persistence/  StateRepository, SettingsRepository, AppSnapshot, LoadResult, PersistenceException,
   │  │                AtomicWriter, AtomicFileWriter, JsonFileStore, JsonFields, Migration, InvalidDataException,
   │  │                StateJsonMapper, SettingsJsonMapper, JsonStateRepository, JsonSettingsRepository,
   │  │                JsonNotesRepository, UserDataDirectory
   │  ├─ application/  TaskService, LittleNotesService, AutoSavePublisher, ObserverRegistry, AppCore
   │  └─ ui/           Layout, Palette, Fonts, TextPainter, MainWindow, NotebookCanvas
   │     ├─ assets/    Asset, AssetCatalog, ImageScaler, SliceSpec, SliceRenderer, SpriteCache
   │     ├─ render/    Surface
   │     ├─ model/     ScreenModel, LittleNotesModel, PanelMode, TaskRowModel, ObserverCardModel, HistoryRowModel, AchievementTier
   │     ├─ widget/    ButtonId, UiAction, InteractionState, TextInput
   │     ├─ controller/ AppController, ScreenAssembler, FooterMessages, SaveFailureTracker
   │     └─ scene/     MainScene, ProceduralArt
   ├─ main/resources/assets/{ui,achievements}/   PNGs do usuário (nomes ASCII) + ui/caderno-moldura.png (moldura 2:1)
   └─ test/java/com/observernotes/   testes + support/ (Fixture, Snapshots, TempFiles, SpyStateRepository) + tools/ (ObserverDemo, PreviewRenderer, MockupSample, RealSessionPreview)
```

Fluxo de um comando: `UI → TaskService → TaskBoard (muda estado) → AchievementLedger (pontua) → AutoSavePublisher → SynchronousEventBus → observadores → grava state.json`.

## 5. Testes e resultados (Etapa 6: 396; Etapa 5: 393)

A Etapa 6 só acrescentou `FontsTest` (fontes originais carregadas, rejeição de fonte com glifos em branco, fallback de ✓/♪, bases de tarefa de mesmo tamanho). Os demais testes foram reexecutados uma vez e passaram.

### Etapa 5

**393 testes, 393 passaram** (302 da 4C.1 preservados + 91 novos), no executor mínimo descrito abaixo.
Novos: `PlaylistTest`, `VolumeScalerTest`, `PlayerEngineTest` (play/pausa/troca, avanço automático, repetição, volume, playlist vazia, faixa ausente, callback atrasado, encerramento), `JavaSoundBackendTest` (WAV gerado na hora decodificado e entregue a uma saída falsa: 16/8 bits, volume, pausa, fechar libera tudo, arquivo ausente/inválido/MP3 falso, sem dispositivo), `JsonPlaylistStoreTest`, `MusicPlayerTest` (persistência, restauração sem tocar, arquivos movidos, falha ao salvar), `PlayerUiTest` (ações, painel, efeitos só em mudanças reais, avisos), `PlayerIntegrationTest` (tarefas, conquistas, observadores e gravação funcionando junto com a música; sem tocar sozinho ao reiniciar) e `PlayerCanvasTest` (cliques, Tab, teclado, roda, desenho, aviso x título).
Os testes de áudio usam WAVs senoidais **gerados nos próprios testes** (sem áudio de terceiros).

## 5a. Testes e resultados (Etapa 4C.1, referência)

**302 testes, 302 passaram** (243 da 4B preservados + 59 novos da 4C.1).
Novos na 4C.1: `FocusRingTest` 12 (ordem do Tab por painel, regras de ativação e confirmação), `EffectLayerTest` (duração, progresso, limite de 24, easing), `AppControllerEffectsTest` 11 (efeito só para fatos reais, estrelas 1-3 e medalhas em ordem, nunca repete, reinício sem celebração, observador de Conquistas desligado, efeito com defeito não afeta nada),
`HistoryScrollTest` 8 (offset, limites, 200 eventos, CLEAR, persistência), `NotebookCanvasKeyboardTest` 11 (Tab, Espaço/Enter, exclusão em dois passos, campo, Esc, roda, hover/pressionado) e `AnimationRenderTest` 7 (todo efeito altera a tela e some sem deixar rastro; foco/hover/barra de rolagem visíveis; layout intacto).
Nenhum teste anterior foi removido ou enfraquecido.

## 5b. Testes e resultados (Etapa 4B, referência)

**243 testes, 243 passaram** (197 das Etapas 1 a 4A preservados + 46 novos da 4B), no executor mínimo descrito abaixo.
Os testes antigos da 4A que usavam a API de botões foram apenas adaptados (`setButtonListener` virou `setActionListener(UiAction)`, `hitTest` recebe o modelo); nenhum foi removido ou enfraquecido.

Novos na 4B: `AppControllerIntegrationTest` 34 (núcleo real + arquivos reais em pasta temporária: criar por ADD/Enter, título vazio, limite de 6 e liberação de espaço, concluir/desmarcar/remarcar sem duplicar recompensa,
excluir preservando conquistas, tiers bronze/prata, chave antiga de tarefa excluída, reiniciar e continuar a progressão, toggles dos observadores com ressincronização e preferências persistidas,
histórico e CLEAR, navegação, rodapé, falha de gravação do estado e das preferências), `MainSceneActionTest` 7 (cada clique devolve a chave da linha/observador certo; CLEAR só no histórico; player e NOVA! não clicáveis),
`EndToEndTest` 5 (canvas real + controlador + núcleo: sessão completa com mouse e teclado em janelas de tamanhos diferentes, reinício, pressionar numa linha e soltar em outra não faz nada, Enter vazio e sétima tarefa).
Verificação por mutação: 7 defeitos injetados em controlador/montagem/hit-test (toggle vira complete, toggle de observador sempre desliga, clique sempre na 1ª linha, CLEAR apagando tarefas, flag de falha de gravação removida, contador fora do observador, tier) — 6 detectados; 1 era equivalente (ramo `default` inalcançável).
Layout: as prévias com dados de exemplo são pixel a pixel idênticas às aprovadas na 4A.

### Etapa 4A (referência)

**197 testes, 197 passaram** (141 das Etapas 1 a 3 preservados + 56 novos da 4A).

Novos na 4A: AssetLoadingTest 7 (todos os PNGs carregam, caminhos únicos, ativos limpos sem coração/cursor), SliceRendererTest 6, TextInputTest 8, InteractionStateTest 3,
LayoutTest 8 (áreas dentro do canvas, 6 linhas sem sobreposição, CLEAR dentro do painel, espaço do player), ScreenModelTest 6, FontsTest 1,
MainSceneRenderTest 10 (renderização headless dos 3 modos, vários tamanhos de janela, letterbox, texto longo não vaza, cada tier de conquista desenha um PNG diferente),
NotebookCanvasInteractionTest 7 (eventos sintéticos de mouse e teclado).

**Não foi possível testar com janela real:** o ambiente não tem tela. Foco, digitação, Enter e cliques foram verificados com eventos sintéticos entregues ao canvas e ao controlador; `MainWindow`, as caixas de diálogo (avisos de partida e fechamento), o piscar do cursor, o cursor de mão, o hover visual e Ctrl+V **não** foram exercitados e precisam do roteiro manual abaixo.

### Etapa 3 (referência)

**141 testes, 141 passaram** (61 das Etapas 1 e 2 preservados + 80 novos).

| Classe | Testes |
|---|---|
| TaskBoardTest 12, MilestoneTest 3, AchievementProgressTest 5, AchievementLedgerTest 4, SynchronousEventBusTest 6, TaskServiceTest 18, ObserversTest 13 | 61 (Etapa 2) |
| DomainRestorerTest | 14 |
| JsonStateRepositoryTest | 26 |
| JsonSettingsRepositoryTest | 6 |
| AtomicFileWriterTest | 3 |
| UserDataDirectoryTest | 4 |
| ObserverRegistryTest | 7 |
| AppCorePersistenceTest | 20 |

**Limitação importante da validação:** o JUnit 5 oficial e o Maven não puderam ser executados no ambiente de desenvolvimento (sem acesso à rede).
Os testes são código JUnit 5 real; foram executados com um executor mínimo compatível, escrito só para esse ambiente e **não incluído no projeto**.
Gson 2.10 (da instalação local do Gradle) foi usado para compilar e testar a camada JSON. `mvn -o validate` aceitou o `pom.xml`.
**Ação recomendada:** rodar `mvn test` (ou *Run As → JUnit Test* no Eclipse) e confirmar **393** testes verdes.
Também foi feita verificação por mutação: 15 regras quebradas de propósito (6 na Etapa 2 e 9 na Etapa 3); todas foram detectadas pelos testes.

## 6. Pendências e limitações conhecidas

**Player (Etapa 5):**
- **Som não validado em dispositivo real.** O ambiente de desenvolvimento não tem placa de som nem janela: a decodificação, o fluxo de bytes, pausa, volume e encerramento foram testados com uma saída falsa; ouvir de verdade, a latência da pausa e o comportamento do seletor de arquivos do Windows precisam do seu teste manual.
- **MP3 não foi testado aqui:** sem rede não foi possível baixar o MP3SPI/JLayer. A integração é pelo SPI do `javax.sound` (sem código específico); só os caminhos de erro (MP3 inválido, biblioteca ausente) foram testados. Rode `mvn test` e abra um MP3 real no app.
- `mvn package` (JAR com MP3) não foi executado aqui (sem Maven/rede); a configuração segue o padrão do shade plugin.
- Volume é por software (16 bits); a pausa para a linha de áudio, então o som para depois de esvaziar o buffer da placa (fração de segundo).
- Não há barra de progresso/tempo da faixa nem embaralhar (fora do escopo pedido).
- O balãozinho de troca de faixa pode cobrir por instantes as últimas linhas do painel (transitório, não bloqueia cliques).
- Arrastar músicas para a janela e atalhos globais de teclado não foram implementados.

**Anteriores:**

- **Validação da 4C.1:** não foi possível abrir uma janela real (ambiente sem tela). Mouse, teclado, roda e animações foram validados com eventos sintéticos e imagens renderizadas com relógio manual. **Faça um teste manual** (roteiro abaixo) para confirmar a sensação das animações e do foco.
- Celebrações aparecem sobre o painel de resumo por até 3,6 s e podem cobrir brevemente o texto "tarefas concluídas" (é transitório e não bloqueia cliques).
- Rolagem do histórico: sem arrastar a barra (só roda, teclado); a barra é só indicador.
- Sem rolagem/edição de título de tarefa; títulos longos encolhem a fonte para caber.
- Observador de Histórico desligado não registra o que acontece enquanto está desligado (por projeto).
- Aviso de validação desaparece sozinho em 4 s (timer do Swing).
- Sem transições entre painéis (4C.2).
- Fechar a janela grava de novo; se falhar, pergunta antes de fechar. Matar o processo não passa por isso (mas cada alteração já foi gravada na hora).
- `Clock.systemUTC()` com horas exibidas no fuso do sistema.
- Sem checksum: edição manual consistente do `state.json` (por exemplo, apagar um ID do ledger *e* a marca da tarefa) não é detectável.
  O que é inconsistente é rejeitado. É uma limitação inerente a dados locais.
- Backup é de **uma gravação atrás**: recuperar do backup pode perder a última alteração.
- Duas instâncias do app ao mesmo tempo podem se sobrescrever (não há trava de arquivo).
- Gravação é síncrona na thread de eventos (arquivos pequenos; aceitável).
- Histórico: só os 200 eventos mais recentes são mantidos. Observador de Histórico desligado não registra o que acontece enquanto está desligado.
- Cada conclusão é registrada no histórico, inclusive as de tarefas remarcadas.
- Fontes **Cakerolli** (textos) e **Cloud Soft** (botões/títulos, negrito com contorno) ainda sem os arquivos TTF/OTF: usar fonte provisória até o usuário enviar (ver `docs/identidade-visual.md`).
- Resolvido na 4A: usamos as cópias limpas `card_observador_limpo.png` e `campo_texto_limpo.png`; `campo_nova_tarefa.png` (com coração) é o "espaço livre".
- Desenhados em Java2D na 4A (autorizado): lápis, barra de progresso listrada, painéis, pílulas do player e do horário, brilhos. O PNG de play não existe: só pausa/anterior/próximo.
- Decisões já tomadas: Abajur do mockup **substituído** pelo tile de Conquistas; o espaço da antiga Constelação agora é o mural Little Notes; janela em proporção ~2:1 (moldura 1397x742, 4 argolas);
  Conquistas pode ser ligado/desligado (só afeta a visão; o ledger não depende dele); "NOVA!" sugerido como "adicionar música".

## 7. Próxima etapa planejada (aguarda autorização)

**Etapa 4C.2 (opcional):** transições entre painéis, fontes originais Cakerolli/Cloud Soft (quando os arquivos chegarem), polimento final e ajuste fino de alinhamento com a fonte definitiva.
**Depois:** player de música real (os controles e o espaço no rodapé já estão reservados; `NOVA!` aguarda a definição de uso).

### Roteiro de teste manual (Etapa 5)
1. Clique em **NOVA!**, escolha vários MP3/WAV: o painel da playlist abre, nada toca sozinho.
2. Toque em ▶ (centro do player): vira pausa; pulso no botão e balão com o nome. Pause, retome, ◀ e ▶▶.
3. Deixe uma faixa curta terminar: a próxima começa. Ative "Repetir: sim" e ouça a playlist voltar ao início.
4. Volume: −/+, clique na barra, roda do mouse sobre o player.
5. Suba/desça/remova faixas; mova um arquivo de pasta e tente tocá-lo: aparece "(indisponível)" e é pulado.
6. Feche e reabra: a playlist, o volume e a repetição voltam, sem tocar. Complete tarefas durante a música: conquistas e gravação normais.
7. Tab/Espaço pelos controles; Esc fecha a playlist.

### Roteiro de teste manual (4C.1)
1. Concluir 3 tarefas inéditas: três estrelas com animações crescentes; a 4ª conclusão: medalha de bronze (confete). Continuar até prata, ouro e conquista máxima (16).
2. Desmarcar/remarcar e excluir tarefas: nenhuma celebração repetida. Fechar e reabrir: nada celebra.
3. Passar o mouse e pressionar checkbox, X e interruptores: halo e encolhimento.
4. Tab/Shift+Tab: anel de foco visível; Enter em checkbox não faz nada; Espaço marca; X exige Espaço duas vezes; Enter no campo adiciona.
5. RECORD com mais de 6 eventos: roda do mouse, setas, PgUp/PgDn, Home/End; CLEAR continua funcionando.

### Roteiro de teste manual no Eclipse (4B)
1. `File → Import → Maven → Existing Maven Projects`, escolha `observer-notes/`. Java 17+. Para não misturar com seus dados reais: *Run Configurations → Arguments → VM arguments* `-Dobservernotes.data.dir=C:\temp\on-teste`.
2. Rodar `com.observernotes.app.Main`. Esperado: 6 espaços livres, "0 de 0", roseta vazia, "Nenhum evento ainda".
3. Clicar no campo, digitar, Enter e depois o botão ADD: tarefas criadas. Campo vazio + Enter: aviso no rodapé. Criar a 7ª: aviso de limite e o texto continua no campo.
4. Marcar um checkbox: coração e barra sobem, aparece a estrela dourada e "1 de 16". Desmarcar/remarcar várias vezes: continua "1 de 16". Excluir com o X: conquista permanece.
5. ALERTS: desligar o Contador (resumo mostra "–"), mexer nas tarefas, religar: contador atualizado. RECORD: ver eventos; CLEAR limpa só a lista.
6. Fechar e abrir de novo: tudo igual (inclusive interruptores). Redimensionar a janela: proporção preservada.
7. Falha de gravação (opcional): tornar a pasta de dados somente leitura e criar uma tarefa: o rodapé avisa que não salvou.
8. Rodar os testes: *Run As → JUnit Test* na pasta `src/test/java` ou `mvn test` (esperado 393 verdes).

## 8. Como outro chat do Claude deve continuar

1. Extrair `observer-notes-etapa-N.zip`; trabalhar na pasta `observer-notes/`. Ler este arquivo e `docs/identidade-visual.md`.
2. Não reiniciar o projeto nem alterar o que está concluído sem justificativa técnica. Manter Observer/SOLID e os testes existentes.
3. Compilar sem rede (se o ambiente não tiver Maven Central): `javac --release 17 -cp <gson.jar> -d out $(find src/main -name '*.java')`.
   Gson pode estar em `/opt/gradle-*/lib/gson-*.jar`. JUnit Jupiter normalmente **não** está disponível: usar um executor mínimo ou pedir ao usuário para rodar `mvn test`.
4. Pré-visualizar a interface sem tela: `com.observernotes.tools.RealSessionPreview <pasta>` (sessão real, cinco telas) ou `com.observernotes.tools.PreviewRenderer <saida.png> <largura> <altura> [tasks|observers|history] [sample|empty] [texto] [none|bronze|silver|gold|max]` (headless; `src/main/resources` precisa estar no classpath).
5. Demonstração do Observer em console: `com.observernotes.tools.ObserverDemo`.
6. Ao concluir cada etapa: rodar todos os testes, atualizar este arquivo, gerar `observer-notes-etapa-N.zip` com o projeto completo e entregar ao usuário. Não avançar sem autorização.
7. Idioma da conversa e dos textos da interface: **português do Brasil**.
