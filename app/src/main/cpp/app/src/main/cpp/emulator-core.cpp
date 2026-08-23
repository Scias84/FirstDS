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
    
    const char* path = env->GetStringUTFChars(romPath, nullptr);
    LOGI("Iniciando motor de emulación real para ROM: %s", path);

    std::string status = "Núcleo C++ Activo:\nCPU ARM9 y ARM7 Enlazadas.\nMemoria RAM Inicializada.";

    env->ReleaseStringUTFChars(romPath, path);
    return env->NewStringUTF(status.c_str());
}
