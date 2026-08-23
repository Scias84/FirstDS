
#include <jni.h>
#include <cstdint>
#include <cstring>

// Resoluciones nativas de Nintendo DS
#define DS_WIDTH 256
#define DS_HEIGHT 192
#define BUFFER_SIZE (DS_WIDTH * DS_HEIGHT * 4) // RGBA8888 (196,608 bytes por pantalla)

// Búferes en memoria nativa
static uint32_t topScreenBuffer[DS_WIDTH * DS_HEIGHT];
static uint32_t bottomScreenBuffer[DS_WIDTH * DS_HEIGHT];
static bool isRomLoaded = false;
static uint32_t frameCounter = 0;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeInit(JNIEnv *env, jobject thiz, jstring system_path) {
    // Inicializar búferes en negro sólido
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

    // Marcamos la ROM como activa en memoria
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

    // Generador de prueba de señal activa de GPU (Patrón de sincronización de cuadros)
    uint32_t barPos = (frameCounter % DS_HEIGHT);
    for (int y = 0; y < DS_HEIGHT; y++) {
        for (int x = 0; x < DS_WIDTH; x++) {
            int idx = y * DS_WIDTH + x;

            // Pantalla Superior: Barra de barrido y fondo activo
            if (y == (int)barPos) {
                topScreenBuffer[idx] = 0xFFFFFFFF; // Línea blanca de refresco
            } else {
                topScreenBuffer[idx] = 0xFF102030 | ((frameCounter & 0x7F) << 16);
            }

            // Pantalla Inferior: Detección táctil en tiempo real
            if (is_touching && x >= touch_x - 4 && x <= touch_x + 4 && y >= touch_y - 4 && y <= touch_y + 4) {
                bottomScreenBuffer[idx] = 0xFF00FF00; // Puntero verde en posición táctil
            } else {
                bottomScreenBuffer[idx] = 0xFF181818;
            }
        }
    }
}

JNIEXPORT jobhtar JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeGetTopBuffer(JNIEnv *env, jobject thiz) {
    return env->NewDirectByteBuffer(topScreenBuffer, BUFFER_SIZE);
}

JNIEXPORT jobject JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeGetBottomBuffer(JNIEnv *env, jobject thiz) {
    return env->NewDirectByteBuffer(bottomScreenBuffer, BUFFER_SIZE);
}

}
