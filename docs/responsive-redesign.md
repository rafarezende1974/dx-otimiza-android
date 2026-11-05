# Redesign responsivo JPG Diet

Branch: codex/jpg-diet-responsive-redesign
Base: 371300bce854b7e8187b3139289ab92033c420fd

## Implementado
- Tema preto/grafite/vermelho, logo existente e cartões com tipografia e alvos de toque maiores.
- Layout vertical abaixo de 840 dp de largura disponível; duas colunas a partir de 840 dp.
- Insets de barras do sistema e recortes; reinflação ao redimensionar, preservando estado do lote em memória.
- Quantidade/tamanho reais da análise e economia real do último lote da sessão.
- Presets 92/85/75, EXIF/GPS e subpastas na tela principal. Preferências salvas.
- Subpastas conectadas à listagem usada tanto na análise quanto no processamento.
- Controles bloqueados durante análise/lote e liberados no encerramento ou erro.
- Cópia em outra pasta continua padrão. Substituição exige escolha e confirmação explícitas, não é persistida.
- Comparação de identidade de documentos além da URI para bloquear origem/destino iguais.
- Falha ao criar subpasta interrompe o lote, em vez de publicar silenciosamente na raiz.
- Resumo de substituição corrigido: não afirma preservar originais nesse modo.

## Verificação executada
PASS: 19 XMLs parseados; 27 IDs únicos por variante; mesmos IDs em celular/tablet; 27 referências Kotlin resolvidas.
Revisão das ligações com os presets, opções, varredura, análise, relatórios e proteção padrão.
O encoder de imagens e a rotina de publicação não foram reescritos.

## Compilação e validação pendentes
A tentativa de gradle assembleDebug --console=plain falhou: Gradle não instalado no ambiente.
Java 17 está disponível; Android SDK/adb não foram encontrados no local padrão/PATH.
Clone direto falhou por DNS. Arquivos foram inspecionados e alterações publicadas pela conexão GitHub.
O workflow existente Build APK só roda automaticamente em main e permite workflow_dispatch. Não foi alterado.
Executar Build APK escolhendo esta branch no GitHub Actions, ou compilar com SDK 36, Java 17 e Gradle 8.11.1.
Não foi gerado APK nem executado teste em dispositivo/emulador.

## Roteiro de aceite em Android
1. Telas 360/412 dp, fonte ampliada, tablet 840/1280 dp e janela dividida: rolagem, leitura, barras e botões.
2. Rotacionar/redimensionar durante análise e lote: manter progresso e opções, sem duplicar processamento.
3. Pasta vazia, JPEG corrompido, permissão revogada e destino sem escrita: mensagem coerente e controles recuperados.
4. Presets 92/85/75: comparar qualidade passada ao encoder e resultados reais.
5. Subpastas ligadas/desligadas: análise e lote devem selecionar o mesmo conjunto, preservando caminhos.
6. Copiar lote pequeno para outra pasta e comparar hashes dos originais antes/depois.
7. Origem/destino iguais (inclusive URIs diferentes do mesmo documento): bloquear.
8. EXIF/GPS, fotos orientadas por EXIF e JPEG sem ganho: conferir saída e relatórios.
9. Modo de substituição: testar somente com cópias descartáveis; cancelar confirmação não deve alterar arquivos.

## Limites preexistentes que exigem atenção
- Sem ganho, o motor copia o original inteiro, inclusive metadados. O texto da nova tela informa isso.
- A rotina existente de substituição apaga/recria o destino: não oferece recuperação transacional se a escrita falhar.
- O motor lê a orientação somente quando manter EXIF está ativo; espelhamento EXIF e privacidade no caso sem ganho exigem revisão própria.
- Processamento continua vinculado à Activity, sem serviço de segundo plano ou retomada após encerramento do processo.
- A escolha de pasta aninhada e colisões com arquivos já existentes continuam exigindo teste de proteção.
- Não foi possível recuperar o mockup da conversa referenciada; implementação baseada na especificação explícita.
