# Redesign responsivo JPG Diet

Branch: codex/jpg-diet-responsive-redesign
Base: 371300bce854b7e8187b3139289ab92033c420fd

## Implementado na iteração atual
- **Barra de progresso de ponta a ponta na base ("Dock")**:
  - O painel de progresso (`panel_progress.xml`) agora ocupa a largura total inferior (100%), formando uma base sólida de controle.
  - Linha superior com rótulo `PROGRESSO` à esquerda, nome do arquivo atual centralizado (`txtCurrent`) e percentual em destaque à direita (`txtPercent` de 0% a 100% em vermelho carmesim).
  - Barra de progresso horizontal contínua e longa, proporcionando leitura visual nítida à distância no tablet.
  - Linha inferior com status e relatório da economia obtida.
- **Duas colunas superiores perfeitamente niveladas**:
  - **Coluna Esquerda**: Métricas compactas $\to$ Pastas (com seletor Outra pasta / Substituir) $\to$ Presets (cartões de 88dp com números grandes) $\to$ Botões "Iniciar otimização" e "Analisar pasta". Altura total de ~405dp.
  - **Coluna Direita**: Prévia da foto calibrada para 340dp de imagem (~404dp de painel), alinhando-se exatamente na mesma linha inferior dos botões da esquerda.
- **Espaçamento e ausência de rolagem**:
  - Margem de 16dp abaixo do logotipo evita que os painéis fiquem grudados no cabeçalho.
  - Altura total da tela ~555dp, garantindo exibição completa em qualquer tablet em orientação horizontal (600dp a 800dp) sem acionar barra de rolagem.
- **Celular mantido consistente**:
  - O celular vertical preserva o fluxo: Cabeçalho $\to$ Métricas $\to$ Pastas $\to$ Presets $\to$ Ações $\to$ Prévia $\to$ Progresso na base.

## Verificação executada
- Validação XML: 100% dos 32 arquivos XML validados (0 erros).
- Resolução de IDs: 29 IDs únicos do `MainActivity.kt` verificados e presentes tanto no layout mobile (`layout/activity_main.xml`) quanto tablet (`layout-w840dp/activity_main.xml`).
- Resolução de Recursos: Todas as referências `@color/`, `@string/`, `@dimen/`, `@drawable/` e `@style/` resolvidas.

## Compilação e validação pendentes
- O workflow existente `Build APK` (`.github/workflows/build-apk.yml`) possui gatilho manual (`workflow_dispatch`).
- Para compilar o APK de depuração, acesse o GitHub Actions no repositório, selecione o workflow **Build APK**, escolha a branch `codex/jpg-diet-responsive-redesign` e clique em **Run workflow**.
