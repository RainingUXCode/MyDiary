# Identidade visual: fontes (registro; vale a partir da Etapa 4)

Fontes originais usadas no Canva para criar os elementos. **Os arquivos TTF/OTF ainda não foram enviados**:
até lá a interface usa uma fonte provisória do Java.

| Fonte | Uso | Aparência |
|---|---|---|
| **Cakerolli** | Textos das tarefas, descrições e demais textos escritos | Arredondada, delicada, informal. Tarefas concluídas aparecem com tachado. |
| **Cloud Soft** | Textos dos botões, títulos e o nome "Observer Notes" | Geralmente em negrito, preenchimento claro e contorno colorido (rosa, roxo e outros conforme o elemento). |

Diretrizes:
- Não alterar os PNGs que já têm texto incorporado.
- Texto dinâmico renderizado em Java2D; contorno desenhado com Java2D (`GlyphVector` + `Stroke`), ajustado visualmente.
- Os valores de contorno do Canva (80 e 125) **não** são convertidos em pixels; ajustar por comparação com o mockup.
- Texto nítido, centralizado e legível. Sem fontes substitutas além da provisória.
- Quando os arquivos chegarem: colocar em `src/main/resources/assets/fonts/` e carregar com `Font.createFont`.

Estado na Etapa 4A: `Fonts` já procura `assets/fonts/Cakerolli.(ttf|otf)` e `assets/fonts/CloudSoft.(ttf|otf)`; sem eles usa a fonte provisória. Ao enviar os arquivos basta colocá-los nessa pasta e reajustar contornos/tamanhos comparando com o mockup.

## Atualização (ajustes finais, 15/11/2026)
Hierarquia vigente: somente **Cloud Soft Bold**. Contorno colorido apenas em títulos e botões; textos dinâmicos sem contorno. Nunito, Cloud Soft Light e Cakerolli não são usadas.
