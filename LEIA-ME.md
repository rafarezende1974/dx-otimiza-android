# JPG Otimiza Android v0.3.0

## O que a v0.3 muda (visual)

- Visual novo: fundo escuro, contador grande "TOTAL ECONOMIZADO"
  no topo e prévia da primeira foto da pasta de origem. Só a casca:
  motor, EXIF, origem intacta e relatórios não mudam.
- Ícone novo: foto da lata "JPG Diet" enviada por ele
  (`ic_launcher_photo.png`, 864x864), em vez do vetor da v0.2
  (que fica sem uso no repo).

Otimizador de JPGs para a Galaxy Tab S9+ (e outros tablets Android
arm64). Equivalente próprio, em espírito, ao JPEGmini de Windows:
reduz o peso dos JPGs sem mudar a resolução, para entrega mais leve
ao cliente, WhatsApp, Drive ou portal. Não é o JPEGmini, não usa o
algoritmo patenteado dele e não copia nome, marca nem textos dele.

Nome novo na v0.2: **JPG Otimiza** (antes DX Otimiza). O pacote
continua `br.com.dxlabs.dxotimiza` de propósito: o push via API não
apaga arquivos, então trocar o pacote deixaria as classes antigas
órfãs no repo. O pacote só mudaria numa v1 para a Play.

Kotlin + Views/XML + C++ (JNI), AGP 8.5.2, Kotlin 2.0.20,
minSdk 29, targetSdk 34, NDK 27.2.12479018, CMake 3.22.1.
Sem Gradle wrapper (o CI usa o Gradle do sistema, mesmo ritual do
DX Studio e do Editor Assistente Android). Ícone próprio da v0.2:
lata de refrigerante diet genérica, sem marca — arquivo mais leve
é a versão diet — sem logo da DX Labs.

## O que a v0.2 faz (além da v0.1)

- Motor **MozJPEG nativo** (progressivo + trellis) como padrão:
  na mesma qualidade, o arquivo sai menor que no encoder Android.
  O MozJPEG é baixado pelo CMake na hora do build (tag v4.1.1) e
  compilado só como biblioteca, sem ferramentas. Não vai commitado.
- **Fallback automático:** se a biblioteca nativa não carregar ou
  falhar numa foto, o app usa o encoder Android da v0.1 e o lote
  segue. Na tela dá para escolher o motor (MozJPEG ou Android) e
  o relatório diz qual motor rodou.
- Escolha de pastas pelo Storage Access Framework (SAF), com
  permissão persistente: origem e destino, em pastas diferentes.
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
  e `resumo-otimizacao.txt` (totais, economia e motor).
- Um arquivo por vez, com a tela acesa durante o lote (sem serviço
  em primeiro plano nesta versão — mantenha o app aberto).

## Limites honestos da v0.2

- **Só arm64-v8a.** É a ABI da Tab S9+ e da maioria dos celulares
  atuais; emulador x86 e aparelhos 32 bits antigos não rodam esta
  versão.
- **Qualidade ainda fixa por modo, não perceptual por foto.** O
  MozJPEG dá arquivo menor na mesma qualidade, mas ainda não escolhe
  a qualidade por foto como o JPEGmini. O ganho real depende da foto:
  JPGs que saíram da câmera ou do editor em qualidade 95-100
  costumam encolher bem em 85; JPGs já comprimidos, muito detalhados
  ou ruidosos encolhem pouco — e aí o app marca como mantido.
- **Primeiro build com NDK pode falhar.** O FetchContent do MozJPEG,
  o nome do alvo da biblioteca e o NDK no runner são os pontos de
  risco conhecidos. Se o log mostrar erro nesses pontos, o ajuste é
  no CMakeLists ou na tag, sem mexer no Kotlin — o usuário avisou
  que aceita demorar mais aqui.
- **Só JPG.** Sem HEIC, sem PNG, sem vídeo.
- **Sem estimativa antes de rodar.** A análise conta arquivos e soma
  o peso original; quanto vai economizar só aparece no relatório.
- **Sem serviço em primeiro plano.** Minimizar ou bloquear a tela
  pode interromper o lote.
- **Fotos muito grandes** são decodificadas inteiras, uma por vez.
  Na Tab S9+ isso é confortável para os JPGs usuais de evento; se
  alguma foto gigante falhar, ela aparece como falha no relatório e
  o lote segue para a próxima.

## O que fica para depois

- Qualidade automática por foto (SSIM ou métrica perceptual) por
  cima do MozJPEG, para chegar mais perto do JPEGmini foto a foto.
- Serviço em primeiro plano, para rodar com a tela bloqueada.
- Estimativa por amostragem (otimizar 3-5 fotos e projetar o lote).

## Build

A integração do Muse não cria arquivos de workflow:
1. O código é subido via push (só arquivos de texto, sem wrapper,
   sem binários; a prévia PNG do ícone NÃO vai ao repo).
2. Edite pela web `.github/workflows/build-apk.yml` colando
   `BUILD-WORKFLOW.yml.txt` da v0.2 (agora com NDK + CMake).
3. O artefato passa a ser `jpg-otimiza-apk`, com `app-debug.apk`
   dentro. APK de debug, sem assinatura de release. Cada build de
   debug tem chave descartável: desinstale a versão anterior
   (DX Otimiza v0.1) antes de instalar a v0.2.

## Teste recomendado na Tab S9+

1. Desinstale a v0.1 e instale a v0.2.
2. Confira na tela inicial: "Motor: MozJPEG nativo disponível".
   Se aparecer "indisponível", o lote vai rodar no encoder Android
   — avise, porque aí o NDK não entrou no APK.
3. Teste primeiro com uma pasta-cópia pequena (10-20 JPGs de câmera,
   de preferência em qualidade alta), não direto na pasta do evento.
4. Rode em Equilíbrio (85) com motor MozJPEG, confira o relatório
   e abra 3-4 fotos otimizadas em 100% comparando com as originais.
5. Rode o mesmo lote com motor Android e compare o total: o
   MozJPEG deve sair menor, na mesma qualidade.
6. Só então use num lote real — e confira também o EXIF de uma foto
   (data/câmera) e o comportamento de uma foto já pequena, que deve
   sair como mantida.
