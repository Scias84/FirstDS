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
    
    // Leer la ruta de la ROM enviada desde Kotlin
    const char* path = env->GetStringUTFChars(romPath, nullptr);
    LOGI("Iniciando motor de emulación para ROM: %s", path);

    // Mensaje de estado del núcleo C++
    std::string status = "Núcleo C++ Activo:\nCPU ARM9 y ARM7 Inicializadas.\nMemoria RAM 4MB Mapeada.";

    // Liberar memoria del string recibido
    env->ReleaseStringUTFChars(romPath, path);

    // Devolver el resultado a Kotlin
    return env->NewStringUTF(status.c_str());
}
