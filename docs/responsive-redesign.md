# Redesign responsivo JPG Diet

Branch: codex/jpg-diet-responsive-redesign
Base: 371300bce854b7e8187b3139289ab92033c420fd

## Implementado na iteração atual
- **Equilíbrio e alinhamento perfeito das duas colunas (Tablet)**:
  - Eliminação do espaço residual na coluna esquerda através da elevação dos cartões de preset (altura aumentada para 88dp), seletor de modo em linha e indicador percentual de progresso.
  - Ajuste da altura da prévia da foto (410dp) e espaçamento superior ampliado abaixo do logotipo (16dp), evitando que as janelas fiquem coladas no cabeçalho. As duas colunas terminam rigorosamente na mesma altura, sem rolagem vertical.
- **Seletor de Modo integrado entre Origem e Destino**:
  - Dois botões estilizados lado a lado entre a linha de Origem e a linha de Destino: `Outra pasta (cópia)` e `Substituir originais`.
  - Ao selecionar `Substituir originais`, a linha de Destino é esmaecida (35% de opacidade) e desativada, com o texto indicativo "Destino: mesma pasta dos originais", mantendo o diálogo de confirmação de segurança antes da otimização.
  - Ao selecionar `Outra pasta (cópia)`, a linha de Destino volta à opacidade total e o botão "Escolher" é reativado.
- **Presets de Compressão com maior destaque visual**:
  - Espaçamento de 12dp adicionado entre o título "COMPRESSÃO" e os botões de preset, eliminando a sensação de elementos grudados.
  - Cartões ampliados (88dp de altura, formato quase quadrado) com números destacados em tamanho grande (1.75x, ~24sp bold) e legendas proporcionais abaixo ("Qualidade", "Equilíbrio", "Leve").
- **Indicador percentual em Progresso**:
  - Inclusão do indicador percentual em tempo real (`txtPercent`), exibindo `0%` -> `100%` com destaque em vermelho carmesim ao lado da barra de progresso horizontal e do status.
- **Menu superior direito e celular**:
  - Menu `Opções ▾` preservado no canto superior direito para controle de EXIF, GPS e subpastas.
  - Celular mantém fluxo vertical limpo com as mesmas melhorias visuais.

## Verificação executada
- Validação XML: 100% dos 32 arquivos XML validados (0 erros).
- Resolução de IDs: 29 IDs únicos do `MainActivity.kt` verificados e presentes tanto no layout mobile (`layout/activity_main.xml`) quanto tablet (`layout-w840dp/activity_main.xml`).
- Resolução de Recursos: Todas as referências `@color/`, `@string/`, `@dimen/`, `@drawable/` e `@style/` resolvidas.

## Compilação e validação pendentes
- O workflow existente `Build APK` (`.github/workflows/build-apk.yml`) possui gatilho manual (`workflow_dispatch`).
- Para compilar o APK de depuração, acesse o GitHub Actions no repositório, selecione o workflow **Build APK**, escolha a branch `codex/jpg-diet-responsive-redesign` e clique em **Run workflow**.
