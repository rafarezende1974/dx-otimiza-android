# DX Otimiza Android v0.1.0

Otimizador de JPGs para a Galaxy Tab S9+ (e outros tablets Android).
Equivalente próprio, em espírito, ao JPEGmini de Windows: reduz o peso
dos JPGs sem mudar a resolução, para entrega mais leve ao cliente,
WhatsApp, Drive ou portal. Não é o JPEGmini, não usa o algoritmo
patenteado dele e não copia nome, marca nem textos dele.

Pacote: `br.com.dxlabs.dxotimiza`
Kotlin + Views/XML, AGP 8.5.2, Kotlin 2.0.20, minSdk 29, targetSdk 34.
Sem Gradle wrapper (o CI usa o Gradle do sistema, mesmo ritual do
DX Studio e do Editor Assistente Android). Ícone próprio (foto + seta
para baixo, ciano sobre escuro) — não é a logo da DX Labs.

## O que a v0.1 faz

- Escolha de pastas pelo Storage Access Framework (SAF), com permissão
  persistente: origem e destino, em pastas diferentes.
- Lote de JPG/JPEG, incluindo subpastas: a estrutura relativa da
  origem é recriada no destino, com o mesmo nome de arquivo.
- 3 modos de qualidade fixa: Qualidade (92), Equilíbrio (85,
  recomendado) e Leve (75). Sem redimensionar, sem mudar de formato.
- EXIF: mantém câmera, data e lente por padrão (a orientação é
  aplicada nos pixels e gravada como normal, para nenhum visualizador
  girar a foto duas vezes). Opção de remover só o GPS.
- Regra anti-piora: se a versão recomprimida ficar maior ou igual à
  original, a original é copiada sem alteração e marcada como
  "mantida (sem ganho)". O app nunca entrega um arquivo maior.
- **A origem nunca é alterada.** O app só lê dela. Se destino for
  igual à origem, ele recusa. Pastas `*_otimizado` dentro da origem
  são ignoradas na varredura, para uma segunda rodada não reprocessar
  a saída.
- Relatório no destino: `relatorio-otimizacao.csv` (arquivo a arquivo)
  e `resumo-otimizacao.txt` (totais e economia).
- Um arquivo por vez, com a tela acesa durante o lote (sem serviço
  em primeiro plano nesta versão — mantenha o app aberto).

## Limites honestos da v0.1

- **Qualidade fixa por modo, não perceptual por foto.** A v0.1 não
  analisa cada foto para achar o ponto antes do artefato, como o
  JPEGmini faz, e não usa MozJPEG via NDK. O ganho real depende da
  foto: JPGs que saíram da câmera ou do editor em qualidade 95-100
  costumam encolher bem em 85; JPGs já comprimidos, muito detalhados
  ou ruidosos encolhem pouco — e aí o app marca como mantido.
- **Só JPG.** Sem HEIC, sem PNG, sem vídeo. Vídeo (H.264/H.265) é
  outro motor e fica fora da v0.1.
- **Sem estimativa antes de rodar.** A análise conta arquivos e soma
  o peso original; quanto vai economizar só aparece no relatório.
- **Sem serviço em primeiro plano.** Minimizar ou bloquear a tela
  pode interromper o lote.
- **Fotos muito grandes** são decodificadas inteiras, uma por vez.
  Na Tab S9+ isso é confortável para os JPGs usuais de evento; se
  alguma foto gigante falhar, ela aparece como falha no relatório e
  o lote segue para a próxima.

## O que fica para a v0.2 (se valer a pena depois do teste)

- MozJPEG via NDK (trellis + Huffman otimizado) e/ou loop de
  qualidade com métrica perceptual simples, para chegar mais perto
  do comportamento do JPEGmini foto a foto.
- Serviço em primeiro plano, para rodar com a tela bloqueada.
- Estimativa por amostragem (otimizar 3-5 fotos e projetar o lote).

## Build

A integração do Muse não cria repositórios nem arquivos de workflow:
1. Crie o repositório vazio pela web: `rafarezende1974/dx-otimiza-android`.
2. O código é subido via push (só arquivos de texto, sem wrapper).
3. Crie pela web `.github/workflows/build-apk.yml` colando
   `BUILD-WORKFLOW.yml.txt`.
4. O artefato é `dx-otimiza-apk`, com `app-debug.apk` dentro.
   APK de debug, sem assinatura de release nesta versão. Cada build
   de debug tem chave descartável: desinstale a versão anterior
   antes de instalar a nova.

## Teste recomendado na Tab S9+

1. Instale o APK.
2. Teste primeiro com uma pasta-cópia pequena (10-20 JPGs de câmera,
   de preferência em qualidade alta), não direto na pasta do evento.
3. Escolha origem e destino em pastas diferentes.
4. Rode em Equilíbrio (85), confira o relatório e abra 3-4 fotos
   otimizadas em 100% comparando com as originais.
5. Só então use num lote real — e confira também o EXIF de uma foto
   (data/câmera) e o comportamento de uma foto já pequena, que deve
   sair como mantida.
