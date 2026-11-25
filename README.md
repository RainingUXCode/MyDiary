# Observer Notes

To-do list em Java (Swing), inspirada em jogos Flash dos anos 2000. Projeto pessoal: padrão Observer, SOLID e persistência local.

## Importar no Eclipse
1. `File → Import → Maven → Existing Maven Projects` e escolha esta pasta.
2. Java 17 ou superior. As dependências (Gson, JUnit 5) são baixadas pelo Maven.
3. Executar: classe `com.observernotes.app.Main`. Testes: `mvn test` ou *Run As → JUnit Test*.

## Rodar no VS Code
1. Instale o *Extension Pack for Java* e abra a pasta `observer-notes/` (File → Open Folder). Java 17+ e Maven instalados.
2. Terminal: `mvn test` (esperado: todos verdes) e `mvn compile exec:java -Dexec.mainClass=com.observernotes.app.Main`, ou abra `Main.java` e clique em *Run*.
3. Dados de teste separados: `-Dobservernotes.data.dir=C:\temp\on-teste` (VM args no `launch.json`).

## Fontes e licenças
- **Cloud Soft Bold**: títulos, botões e destaques. **Uso pessoal apenas: não há licença para uso comercial nem para redistribuir.** A Cloud Soft Light deixou de ser usada (arquivo removido do pacote).
- **Tipografia única: Cloud Soft Bold.** Com contorno colorido só nos títulos e botões; sem contorno nos textos dinâmicos (tarefas, histórico, notificações, músicas, descrições), com tachado e cor suave nas tarefas concluídas e cor discreta nos placeholders. Nunito e Cloud Soft Light não são mais usadas.
- Cakerolli DEMO (glifos em branco) não é usada. Detalhes: `docs/PROGRESSO.md` e `docs/fonts-licencas/`.

## Player musical e playlist padrão
- **Rodapé:** à esquerda ◀ ⏸/▶ ▶▶ (roxo); no centro uma única área de informação que alterna a cada 6 s entre a última notificação e `♪ Now Playing: Nome` (só alterna enquanto toca; pausado/parado mostra só a notificação; notificação nova aparece na hora e fica ao menos 6 s; textos longos terminam em "…"); à direita o botão amarelo **♪ Playlist**, que abre/fecha o painel. A alternância é só visual: não gera eventos no histórico.
- **Dentro da playlist:** botão **+ Música** (MP3, WAV, AIFF, AU; vários arquivos), escolher, subir, descer, remover, repetir e volume. A roda do mouse sobre o player muda o volume.
- **Playlist padrão:** 7 músicas (Sweet Morning, Starry Notes, Cotton Candy, Pink Café, Soft Breeze, Sunny Day, Dreamy Days) entram na primeira execução, sem tocar sozinhas, depois das músicas que você já tinha. Se você as remover, não voltam. **Créditos e a ressalva sobre direitos de redistribuição: `docs/musicas-padrao-creditos.md`.**
- A playlist (caminhos + títulos), o volume e a repetição ficam em `music.json`, ao lado de `state.json`. Músicas suas continuam só como referências (nada é copiado).
- JAR executável: `mvn package -DskipTests` e `java -jar target/observer-notes-0.1.0.jar`, ou `executar.bat` (Windows) / `./executar.sh`. O JAR inclui MP3SPI para MP3.
- Títulos de tarefa longos terminam em "…" e mostram o texto completo num tooltip ao passar o mouse.

## Testar as animações (Etapa 4C.1)
- Conclua tarefas inéditas: 3 estrelas, depois bronze (4ª), prata, ouro e conquista máxima (16ª). Desmarcar/excluir/reabrir não repete celebração.
- Mouse sobre/pressionando checkbox, X e interruptores: hover e pressionado. RECORD com mais de 6 eventos: roda do mouse, ↑/↓, PgUp/PgDn, Home/End.
- Teclado: Tab/Shift+Tab (anel de foco), Espaço marca, Enter nunca marca/exclui, X e CLEAR pedem Espaço duas vezes.
- Prévias estáticas das animações: `docs/previews/etapa4C1-*.png`. Gerar de novo: `com.observernotes.tools.AnimationPreview <pasta>` (em `src/test/java`).

## Usar
Crie tarefas no campo (Enter ou ADD), marque no checkbox, exclua no X. LIST / ALERTS / RECORD alternam tarefas, observadores e histórico. Tudo é salvo automaticamente.
No **Resumo**, o mural **Little Notes** guarda até três bilhetinhos independentes das tarefas: `+` cria,
clique no bilhete edita no próprio lugar, Enter confirma, Esc cancela e `×` remove. Os bilhetes não
geram pontos, conquistas, histórico nem notificações.
Roteiro completo de teste no Eclipse: `docs/PROGRESSO.md`, seção 7 (4C.1 e 4B).

## Dados do usuário
Ficam fora do projeto (veja `docs/PROGRESSO.md`, seção 3). Para testar com outra pasta: `-Dobservernotes.data.dir=/caminho`.
Tarefas e progresso ficam em `state.json`, preferências em `settings.json` e os bilhetes do Little Notes
em `notes.json`. O mural usa arquivo próprio, versionado, com escrita atômica, backup e recuperação de
arquivo corrompido; o formato de `state.json` não foi alterado.

Estado do desenvolvimento, estrutura e instruções de continuação: **`docs/PROGRESSO.md`**.
