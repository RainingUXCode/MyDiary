# Músicas padrão: créditos e situação de redistribuição

Sete músicas acompanham o aplicativo (`src/main/resources/assets/music/`, índice em `playlist.txt`). Na primeira
execução são copiadas para `<pasta de dados>/musicas-padrao/` e entram na playlist, sem tocar sozinhas.

| Título de exibição | Arquivo | Título original (metadado preservado) |
|---|---|---|
| Sweet Morning | sweet-morning.mp3 | ゲームメニューの朝 |
| Starry Notes | starry-notes.mp3 | 부드러운 MIDI 게임 음악 |
| Cotton Candy | cotton-candy.mp3 | 부드러운 MIDI 게임 음악 (segunda versão) |
| Pink Café | pink-cafe.mp3 | Bossa Nova Menu |
| Soft Breeze | soft-breeze.mp3 | Bossa Nova Menu (segunda versão) |
| Sunny Day | sunny-day.mp3 | Sunny Groove |
| Dreamy Days | dreamy-days.mp3 | Untitled |

- Os nomes curtos em inglês são **apenas títulos de exibição** (gravados na playlist). Os MP3 mantêm as tags originais:
  artista **koidriftlofi**, comentário **"made with suno"** (com data e id de criação de cada faixa).
- Um oitavo arquivo enviado (`Bossa_Nova_Menu_1.mp3`) era idêntico byte a byte a "Pink Café" e **não foi incluído**.
- Para caber no limite de 30 MiB de envio, os MP3 foram **recodificados em 96 kbps** (originais: ~190-200 kbps), com
  `ffmpeg -map_metadata 0` para manter as tags. As durações são as mesmas. Os arquivos originais continuam com você.

## Redistribuição: o que NÃO está verificado
As faixas foram geradas com o Suno. A inclusão no app foi feita porque você pediu, **"desde que haja autorização para
redistribuí-las"**; essa autorização **não pôde ser conferida aqui**. Os termos do Suno costumam permitir uso comercial
apenas para faixas criadas em plano pago, e a titularidade depende do seu plano na data da criação. Antes de publicar
ou distribuir o aplicativo para outras pessoas, confirme isso. Se não houver autorização, é só apagar os MP3 e o
`playlist.txt` de `assets/music/`: o app abre normalmente, com playlist vazia. As músicas que o usuário adicionar
continuam sendo apenas referências aos arquivos dele.
