# Redesign responsivo JPG Diet

Branch: codex/jpg-diet-responsive-redesign
Base: 371300bce854b7e8187b3139289ab92033c420fd

## Implementado na iteração atual
- **Métricas compactas no alto à esquerda**: Cartão horizontal compacto com colunas para JPGs na origem, Tamanho total e Economia obtida (em destaque vermelho), acompanhado do detalhamento da economia.
- **Seleção de origem/destino em duas linhas compactas**:
  - Linha 1: Caminho da origem + botão compacto "Escolher".
  - Linha 2: Caminho do destino + botão compacto "Escolher".
  - Linha 3 (auxiliar): Aviso de proteção dos originais + botão "Modo" para alternar modo de destino.
- **Presets 92/85/75 em três cartões lado a lado**: Cartões seletores em `RadioGroup` horizontal com destaque visual em vermelho escuro com borda carmesim ao selecionar, mantendo integridade com as opções de EXIF, remoção de GPS e subpastas.
- **Botão vermelho "Iniciar otimização"**: Botão primário com rótulo "Iniciar otimização" e estilo de destaque.
- **Correção da aparência de botão desabilitado**: Configuração de `ColorStateList` dedicados para background, stroke e texto (`res/color/btn_primary_*` e `res/color/btn_secondary_*`). Quando desabilitado, o botão exibe superfície neutra escura, borda sutil e texto legível fosco, sem artefatos ou cores lavadas.
- **Prévia grande à direita no tablet**: Na variante de layout `>= 840dp`, a prévia ocupa a coluna direita completa com altura ampliada (460dp) e moldura escura dedicada, alinhando-se aos controles à esquerda.
- **Progresso na parte inferior**: Painel de progresso posicionado na base inferior com barra de progresso horizontal, status, arquivo corrente e resumo final.
- **Celular com organização vertical**: Layout para `< 840dp` empilhado ordenadamente: Métricas -> Pastas -> Presets/Opções -> Iniciar Otimização -> Prévia -> Progresso.
- **Preservação de regras e dados**: Otimizador intacto, métricas estritamente reais, proteção dos originais inalterada e compatibilidade com salvamento/restauração de ciclo de vida.

## Verificação executada
- Validação XML: 100% dos 32 arquivos XML validados com sucesso (zero erros de sintaxe).
- Resolução de IDs: 27 IDs únicos do `MainActivity.kt` verificados e presentes tanto no layout mobile (`layout/activity_main.xml`) quanto tablet (`layout-w840dp/activity_main.xml`).
- Resolução de Recursos: Verificação de todas as referências `@color/`, `@string/`, `@dimen/`, `@drawable/` e `@style/` contra os recursos existentes, com 0 referências pendentes.

## Compilação e validação pendentes
- O workflow existente `Build APK` (`.github/workflows/build-apk.yml`) possui gatilho manual (`workflow_dispatch`).
- Para compilar o APK de depuração, acesse o GitHub Actions no repositório, selecione o workflow **Build APK**, escolha a branch `codex/jpg-diet-responsive-redesign` e clique em **Run workflow**.
