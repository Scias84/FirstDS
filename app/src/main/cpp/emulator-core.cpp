#include <jni.h>
#include <cstdint>
#include <cstring>

// Dimensiones de pantalla de Nintendo DS
#define DS_WIDTH 256
#define DS_HEIGHT 192
#define BUFFER_SIZE (DS_WIDTH * DS_HEIGHT * 4) // Formato RGBA8888 (196,608 bytes)

// Búferes en memoria nativa
static uint32_t topScreenBuffer[DS_WIDTH * DS_HEIGHT];
static uint32_t bottomScreenBuffer[DS_WIDTH * DS_HEIGHT];
static bool isRomLoaded = false;
static uint32_t frameCounter = 0;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeInit(JNIEnv *env, jobject thiz, jstring system_path) {
    for (int i = 0; i < DS_WIDTH * DS_HEIGHT; i++) {
        topScreenBuffer[i] = 0xFF000000;
        bottomScreenBuffer[i] = 0xFF000000;
    }
    frameCounter = 0;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeLoadRom(JNIEnv *env, jobject thiz, jstring rom_path) {
    const char *path = env->GetStringUTFChars(rom_path, nullptr);
    if (path == nullptr) return JNI_FALSE;

    isRomLoaded = true;
    frameCounter = 0;

    env->ReleaseStringUTFChars(rom_path, path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeRunFrame(
    JNIEnv *env, jobject thiz,
    jint key_mask, jint touch_x, jint touch_y, jboolean is_touching
) {
    if (!isRomLoaded) return;

    frameCounter++;

    // Generador visual de cuadros sincronizados
    uint32_t barPos = (frameCounter % DS_HEIGHT);
    for (int y = 0; y < DS_HEIGHT; y++) {
        for (int x = 0; x < DS_WIDTH; x++) {
            int idx = y * DS_WIDTH + x;

            // Pantalla Superior: Barra de barrido dinámico
            if (y == (int)barPos) {
                topScreenBuffer[idx] = 0xFFFFFFFF;
            } else {
                topScreenBuffer[idx] = 0xFF102030 | ((frameCounter & 0x7F) << 16);
            }

            // Pantalla Inferior: Detección del punto táctil
            if (is_touching && x >= touch_x - 4 && x <= touch_x + 4 && y >= touch_y - 4 && y <= touch_y + 4) {
                bottomScreenBuffer[idx] = 0xFF00FF00;
            } else {
                bottomScreenBuffer[idx] = 0xFF181818;
            }
        }
    }
}

JNIEXPORT jobject JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeGetTopBuffer(JNIEnv *env, jobject thiz) {
    return env->NewDirectByteBuffer(topScreenBuffer, BUFFER_SIZE);
}

JNIEXPORT jobject JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeGetBottomBuffer(JNIEnv *env, jobject thiz) {
    return env->NewDirectByteBuffer(bottomScreenBuffer, BUFFER_SIZE);
}

}
