#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "NDS_Kernel"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_ejemplo_emulador_MainActivity_initEmulatorCore(
        JNIEnv* env,
        jobject /* this */,
        jstring romPath) {
    
    // Convertir la ruta de la ROM de Java a C++
    const char* path = env->GetStringUTFChars(romPath, nullptr);
    
    LOGI("Iniciando motor de emulación para ROM: %s", path);

    // Simulación de inicialización de la arquitectura ARM9 / ARM7
    std::string status = "Núcleo C++ Activo: CPU ARM9 y ARM7 Listas. Memoria RAM 4MB Mapeada.";

    env->ReleaseStringUTFChars(romPath, path);
    return env->NewStringUTF.c_str() ? env->NewStringUTF(status.c_str()) : nullptr;
}
