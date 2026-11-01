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

# Só a biblioteca. Sem ferramentas (cjpeg/djpeg), sem Java, sem
# biblioteca compartilhada separada: o .a estático entra no nosso .so.
set(WITH_JAVA OFF CACHE BOOL "" FORCE)
set(WITH_TOOLS OFF CACHE BOOL "" FORCE)
set(WITH_TURBOJPEG OFF CACHE BOOL "" FORCE)
set(ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(ENABLE_STATIC ON CACHE BOOL "" FORCE)
set(WITH_SIMD ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(mozjpeg)

add_library(jpgotimiza SHARED mozjpeg_jni.cpp)

# jpeglib.h fica na raiz do fonte; jconfig.h é gerado no build.
target_include_directories(jpgotimiza PRIVATE
    ${mozjpeg_SOURCE_DIR}
    ${mozjpeg_BINARY_DIR}
)

# Nome do alvo da biblioteca no CMake do MozJPEG/libjpeg-turbo.
# Se o build falhar com "target jpeg not found", o nome correto
# está no CMakeLists.txt do MozJPEG baixado (alvos jpeg/jpeg-static).
target_link_libraries(jpgotimiza jpeg log)
