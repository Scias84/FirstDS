#include <jni.h>
#include <cstdint>
#include <cstring>
#include <cstdio>

#define DS_WIDTH 256
#define DS_HEIGHT 192
#define BUFFER_SIZE (DS_WIDTH * DS_HEIGHT * 4)

// Estructura de cabecera estándar Nintendo DS (512 bytes)
struct NdsHeader {
    char gameTitle[12];
    char gameCode[4];
    char makerCode[2];
    uint8_t unitCode;
    uint8_t encryptionSeed;
    uint8_t deviceCapacity;
    uint8_t reserved1[7];
    uint16_t ndsRegion;
    uint8_t romVersion;
    uint8_t flags;
    uint32_t arm9RomOffset;
    uint32_t arm9EntryAddress;
    uint32_t arm9RamAddress;
    uint32_t arm9Size;
    uint32_t arm7RomOffset;
    uint32_t arm7EntryAddress;
    uint32_t arm7RamAddress;
    uint32_t arm7Size;
};

static uint32_t topScreenBuffer[DS_WIDTH * DS_HEIGHT];
static uint32_t bottomScreenBuffer[DS_WIDTH * DS_HEIGHT];
static bool isRomLoaded = false;
static uint32_t frameCounter = 0;
static NdsHeader currentHeader;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeInit(JNIEnv *env, jobject thiz, jstring system_path) {
    for (int i = 0; i < DS_WIDTH * DS_HEIGHT; i++) {
        topScreenBuffer[i] = 0xFF000000;
        bottomScreenBuffer[i] = 0xFF000000;
    }
    frameCounter = 0;
    isRomLoaded = false;
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeLoadRom(JNIEnv *env, jobject thiz, jstring rom_path) {
    const char *path = env->GetStringUTFChars(rom_path, nullptr);
    if (path == nullptr) return env->NewStringUTF("Error: Ruta inválida");

    FILE *file = fopen(path, "rb");
    if (!file) {
        env->ReleaseStringUTFChars(rom_path, path);
        return env->NewStringUTF("Error: No se pudo abrir el archivo ROM");
    }

    // Leer cabecera binaria de 512 bytes
    size_t readBytes = fread(&currentHeader, 1, sizeof(NdsHeader), file);
    fclose(file);

    if (readBytes < sizeof(NdsHeader)) {
        env->ReleaseStringUTFChars(rom_path, path);
        return env->NewStringUTF("Error: Archivo ROM incompleto o corrupto");
    }

    isRomLoaded = true;
    frameCounter = 0;

    char titleClean[13] = {0};
    memcpy(titleClean, currentHeader.gameTitle, 12);

    char info[128];
    snprintf(info, sizeof(info), "Título: %s | ID: %.4s", titleClean, currentHeader.gameCode);

    env->ReleaseStringUTFChars(rom_path, path);
    return env->NewStringUTF(info);
}

JNIEXPORT void JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeRunFrame(
    JNIEnv *env, jobject thiz,
    jint key_mask, jint touch_x, jint touch_y, jboolean is_touching
) {
    if (!isRomLoaded) return;

    frameCounter++;

    uint32_t barPos = (frameCounter % DS_HEIGHT);
    for (int y = 0; y < DS_HEIGHT; y++) {
        for (int x = 0; x < DS_WIDTH; x++) {
            int idx = y * DS_WIDTH + x;

            if (y == (int)barPos) {
                topScreenBuffer[idx] = 0xFFFFFFFF;
            } else {
                topScreenBuffer[idx] = 0xFF0A1E3F | ((frameCounter & 0x3F) << 8);
            }

            if (is_touching && x >= touch_x - 5 && x <= touch_x + 5 && y >= touch_y - 5 && y <= touch_y + 5) {
                bottomScreenBuffer[idx] = 0xFF00FF00;
            } else {
                bottomScreenBuffer[idx] = 0xFF121212;
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
