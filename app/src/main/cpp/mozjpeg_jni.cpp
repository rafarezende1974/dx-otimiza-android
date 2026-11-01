// JPG Otimiza v0.2 — JNI para o MozJPEG.
//
// Entrada: pixels ARGB vindos do Kotlin (Bitmap.getPixels), já com a
// orientação EXIF aplicada. Saída: bytes JPEG em memória, via
// jpeg_mem_dest, devolvidos como byte[] do Java. Nada é escrito em
// arquivo aqui: o Kotlin grava o temporário, copia o EXIF e só
// então publica no destino, igual à v0.1.
//
// Segurança: o handler de erro padrão do libjpeg chama exit() em
// erro, o que mataria o app. Aqui o handler usa setjmp/longjmp e
// devolve null ao Kotlin, que cai para o motor Android (fallback).
// Sem isso, um JPEG estranho derrubaria o lote inteiro.
#include <jni.h>
#include <setjmp.h>
#include <stdlib.h>
#include <string.h>
#include "jpeglib.h"

struct MozErrorMgr {
    struct jpeg_error_mgr pub;
    jmp_buf setjmp_buffer;
};

static void moz_error_exit(j_common_ptr cinfo) {
    MozErrorMgr* err = reinterpret_cast<MozErrorMgr*>(cinfo->err);
    // Mensagem fica no buffer interno; não imprimimos nada.
    (*cinfo->err->format_message)(cinfo, nullptr);
    longjmp(err->setjmp_buffer, 1);
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_br_com_dxlabs_dxotimiza_MozJpeg_nativeCompressArgb(
    JNIEnv* env,
    jclass /* clazz */,
    jintArray argb,
    jint width,
    jint height,
    jint quality,
    jboolean trellis) {
    if (argb == nullptr || width <= 0 || height <= 0) return nullptr;
    if (quality < 1) quality = 1;
    if (quality > 100) quality = 100;

    jint* pixels = env->GetIntArrayElements(argb, nullptr);
    if (pixels == nullptr) return nullptr;

    jpeg_compress_struct cinfo;
    MozErrorMgr jerr;
    unsigned char* outbuffer = nullptr;
    unsigned long outsize = 0;
    jbyteArray result = nullptr;
    JSAMPROW row = nullptr;

    cinfo.err = jpeg_std_error(&jerr.pub);
    jerr.pub.error_exit = moz_error_exit;

    if (setjmp(jerr.setjmp_buffer)) {
        // Erro dentro do libjpeg: limpa e devolve null (fallback).
        if (row != nullptr) free(row);
        if (outbuffer != nullptr) free(outbuffer);
        jpeg_destroy_compress(&cinfo);
        env->ReleaseIntArrayElements(argb, pixels, JNI_ABORT);
        return nullptr;
    }

    jpeg_create_compress(&cinfo);
    jpeg_mem_dest(&cinfo, &outbuffer, &outsize);

    cinfo.image_width = static_cast<JDIMENSION>(width);
    cinfo.image_height = static_cast<JDIMENSION>(height);
    cinfo.input_components = 3;
    cinfo.in_color_space = JCS_RGB;

    jpeg_set_defaults(&cinfo);
    jpeg_set_quality(&cinfo, quality, TRUE);
    cinfo.optimize_coding = TRUE;

    if (trellis) {
        // Perfil de máxima compressão do MozJPEG: progressivo +
        // trellis + otimização de scans. É o que dá o ganho extra
        // sobre o encoder padrão do Android, ao custo de ser mais
        // lento por foto. Se a versão do MozJPEG não suportar um
        // parâmetro, o setjmp acima protege o app.
        jpeg_c_set_int_param(&cinfo, JINT_COMPRESS_PROFILE, JCP_MAX_COMPRESSION);
        jpeg_c_set_bool_param(&cinfo, JBOOLEAN_USE_SCANS_IN_TRELLIS, TRUE);
        jpeg_c_set_bool_param(&cinfo, JBOOLEAN_TRELLIS_EOB_OPT, TRUE);
        jpeg_c_set_bool_param(&cinfo, JBOOLEAN_TRELLIS_Q_OPT, TRUE);
        jpeg_c_set_int_param(&cinfo, JINT_TRELLIS_NUM_LOOPS, 10);
    } else {
        jpeg_simple_progression(&cinfo);
    }

    jpeg_start_compress(&cinfo, TRUE);

    row = static_cast<JSAMPROW>(malloc(sizeof(JSAMPLE) * 3 * width));
    if (row == nullptr) {
        jpeg_destroy_compress(&cinfo);
        env->ReleaseIntArrayElements(argb, pixels, JNI_ABORT);
        return nullptr;
    }

    while (cinfo.next_scanline < cinfo.image_height) {
        JDIMENSION y = cinfo.next_scanline;
        const jint* src = pixels + static_cast<size_t>(y) * width;
        // ARGB int: 0xAARRGGBB. O MozJPEG quer RGB puro, sem alfa.
        for (JDIMENSION x = 0; x < static_cast<JDIMENSION>(width); x++) {
            jint px = src[x];
            row[x * 3 + 0] = static_cast<JSAMPLE>((px >> 16) & 0xFF);
            row[x * 3 + 1] = static_cast<JSAMPLE>((px >> 8) & 0xFF);
            row[x * 3 + 2] = static_cast<JSAMPLE>(px & 0xFF);
        }
        jpeg_write_scanlines(&cinfo, &row, 1);
    }

    jpeg_finish_compress(&cinfo);

    if (outbuffer != nullptr && outsize > 0) {
        result = env->NewByteArray(static_cast<jsize>(outsize));
        if (result != nullptr) {
            env->SetByteArrayRegion(result, 0, static_cast<jsize>(outsize),
                                    reinterpret_cast<const jbyte*>(outbuffer));
        }
    }

    free(row);
    free(outbuffer);
    jpeg_destroy_compress(&cinfo);
    env->ReleaseIntArrayElements(argb, pixels, JNI_ABORT);
    return result;
}
