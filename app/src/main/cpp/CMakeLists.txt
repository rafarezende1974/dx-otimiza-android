# JPG Otimiza v0.2 — camada nativa MozJPEG.
#
# O MozJPEG NÃO vai commitado no repo: o push via API do Muse só leva
# arquivos de texto pequenos, e o MozJPEG é um projeto C inteiro. O
# CMake baixa o código na hora do build (o runner tem internet) e
# compila só a biblioteca, sem ferramentas nem testes.
#
# Tag: v4.1.1. Se o FetchContent falhar dizendo que a tag não existe,
# trocar GIT_TAG por "master" e rebuildar — o código JNI não muda,
# porque ele usa só a API libjpeg estável + os parâmetros MozJPEG
# documentados em jpeglib.h.
cmake_minimum_required(VERSION 3.22.1)
project(jpgotimiza)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)
FetchContent_Declare(
    mozjpeg
    GIT_REPOSITORY https://github.com/mozilla/mozjpeg.git
    GIT_TAG v4.1.1
    GIT_SHALLOW TRUE
)

# Só a biblioteca. Sem Java, sem biblioteca compartilhada separada:
# o .a estático entra no nosso .so.
#
# Correção do build nº 3 (run 41257061485): o CMake do MozJPEG
# exige libpng quando PNG_SUPPORTED está ligado, porque ele compila
# o cjpeg-static junto com a biblioteca — e o runner não tem
# libpng. Como o app não usa PNG nem o cjpeg, desligar PNG resolve
# sem instalar nada no runner.
# (WITH_TOOLS não existe no CMake do MozJPEG; a v0.2 anterior o
# definia à toa e o cjpeg-static continuava sendo compilado.)
set(WITH_JAVA OFF CACHE BOOL "" FORCE)
set(WITH_TURBOJPEG OFF CACHE BOOL "" FORCE)
set(ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(ENABLE_STATIC ON CACHE BOOL "" FORCE)
set(WITH_SIMD ON CACHE BOOL "" FORCE)
set(PNG_SUPPORTED OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(mozjpeg)

add_library(jpgotimiza SHARED mozjpeg_jni.cpp)

# jpeglib.h fica na raiz do fonte; jconfig.h é gerado no build.
target_include_directories(jpgotimiza PRIVATE
    ${mozjpeg_SOURCE_DIR}
    ${mozjpeg_BINARY_DIR}
)

# Alvo da biblioteca: com ENABLE_SHARED OFF, o CMake do MozJPEG
# cria só "jpeg-static" (não "jpeg" — esse só existe com a versão
# compartilhada). Ligar contra "jpeg" aqui foi o segundo erro
# embutido na v0.2, que apareceria logo depois do PNG.
target_link_libraries(jpgotimiza jpeg-static log)
