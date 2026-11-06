# Redesign responsivo JPG Diet

Branch: codex/jpg-diet-responsive-redesign
Base: 371300bce854b7e8187b3139289ab92033c420fd

## Implementado na iteração atual
- **Modo horizontal em tela única sem barra de rolagem**:
  - Ajuste de dimensões verticais e distribuição proporcional no layout tablet (`layout-w840dp/activity_main.xml`), garantindo que todos os elementos caibam em 100% da viewport sem necessidade de rolagem.
- **Menu na parte superior direita (EXIF, GPS, Subpastas)**:
  - Botão de opções no canto superior direito do cabeçalho (`btnOptions`).
  - `PopupMenu` com itens comutáveis nativos (checkmarks):
    - **Manter EXIF** (câmera, data, lente)
    - **Remover GPS** (localização geográfica)
    - **Incluir subpastas** (varredura recursiva de diretórios)
    - **Modo de destino** (outra pasta vs. substituição)
  - Persistência contínua em `SharedPreferences` e reflexo imediato no sumário inline (`txtOptionsSummary`).
- **Ações e Progresso elevados**:
  - Com a saída das caixas de seleção da coluna esquerda, o botão vermelho "Iniciar otimização" e "Analisar pasta" subiram, ficando posicionados logo abaixo dos 3 cartões de preset (92/85/75).
  - O painel de progresso (`panel_progress.xml`) também subiu para a coluna esquerda, diretamente abaixo dos botões de ação, exibindo barra de progresso horizontal, status, arquivo atual e resumo sem rolagem.
- **Prévia sincronizada à direita**:
  - A coluna da direita preenche a altura disponível dinamicamente (`match_parent`), nivelando a prévia com a coluna de controles e status.
- **Celular com organização vertical**:
  - Mantido fluxo linear coerente para telas `< 840dp`, com o menu de opções no cabeçalho superior e prévia na sequência.

## Verificação executada
- Validação XML: 100% dos 32 arquivos XML validados com sucesso (zero erros de sintaxe).
- Resolução de IDs: 25 IDs únicos do `MainActivity.kt` verificados e presentes tanto no layout mobile (`layout/activity_main.xml`) quanto tablet (`layout-w840dp/activity_main.xml`).
- Resolução de Recursos: Verificação de todas as referências `@color/`, `@string/`, `@dimen/`, `@drawable/` e `@style/` contra os recursos existentes, com 0 referências pendentes.

## Compilação e validação pendentes
- O workflow existente `Build APK` (`.github/workflows/build-apk.yml`) possui gatilho manual (`workflow_dispatch`).
- Para compilar o APK de depuração, acesse o GitHub Actions no repositório, selecione o workflow **Build APK**, escolha a branch `codex/jpg-diet-responsive-redesign` e clique em **Run workflow**.
