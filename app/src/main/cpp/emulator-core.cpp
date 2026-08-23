#include <jni.h>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cmath>

#define DS_WIDTH 256
#define DS_HEIGHT 192
#define BUFFER_SIZE (DS_WIDTH * DS_HEIGHT * 4)

// Audio a 44100 Hz (735 muestras estéreo por frame a 60 FPS)
#define SAMPLES_PER_FRAME 735
#define AUDIO_BUFFER_SIZE (SAMPLES_PER_FRAME * 2) // 2 canales (L + R)

// Mapa de Memoria de Nintendo DS
#define MAIN_RAM_SIZE (4 * 1024 * 1024) // 4MB Main RAM
#define VRAM_SIZE (656 * 1024)          // 656KB Video RAM

static uint8_t mainRam[MAIN_RAM_SIZE];
static uint8_t vram[VRAM_SIZE];

static uint32_t topScreenBuffer[DS_WIDTH * DS_HEIGHT];
static uint32_t bottomScreenBuffer[DS_WIDTH * DS_HEIGHT];
static int16_t audioBuffer[AUDIO_BUFFER_SIZE];

static bool isRomLoaded = false;
static uint32_t frameCounter = 0;
static float audioPhase = 0.0f;

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

static NdsHeader currentHeader;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeInit(JNIEnv *env, jobject thiz, jstring system_path) {
    memset(mainRam, 0, MAIN_RAM_SIZE);
    memset(vram, 0, VRAM_SIZE);

    for (int i = 0; i < DS_WIDTH * DS_HEIGHT; i++) {
        topScreenBuffer[i] = 0xFF000000;
        bottomScreenBuffer[i] = 0xFF000000;
    }

    memset(audioBuffer, 0, sizeof(audioBuffer));
    frameCounter = 0;
    isRomLoaded = false;
    audioPhase = 0.0f;
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeLoadRom(JNIEnv *env, jobject thiz, jstring rom_path) {
    const char *path = env->GetStringUTFChars(rom_path, nullptr);
    if (path == nullptr) return env->NewStringUTF("Error: Ruta inválida");

    FILE *file = fopen(path, "rb");
    if (!file) {
        env->ReleaseStringUTFChars(rom_path, path);
        return env->NewStringUTF("Error: No se pudo abrir la ROM");
    }

    size_t readBytes = fread(&currentHeader, 1, sizeof(NdsHeader), file);
    if (readBytes < sizeof(NdsHeader)) {
        fclose(file);
        env->ReleaseStringUTFChars(rom_path, path);
        return env->NewStringUTF("Error: Cabecera NDS corrupta");
    }

    // Cargar punto de entrada ARM9 en la Main RAM
    if (currentHeader.arm9Size > 0 && currentHeader.arm9Size <= (MAIN_RAM_SIZE - 0x8000)) {
        fseek(file, currentHeader.arm9RomOffset, SEEK_SET);
        fread(&mainRam[0x8000], 1, currentHeader.arm9Size, file);
    }

    fclose(file);
    isRomLoaded = true;
    frameCounter = 0;

    char titleClean[13] = {0};
    memcpy(titleClean, currentHeader.gameTitle, 12);

    char info[128];
    snprintf(info, sizeof(info), "Juego: %s [%.4s]", titleClean, currentHeader.gameCode);

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

    // 1. Renderizado de video a 60 FPS
    uint32_t barPos = (frameCounter % DS_HEIGHT);
    for (int y = 0; y < DS_HEIGHT; y++) {
        for (int x = 0; x < DS_WIDTH; x++) {
            int idx = y * DS_WIDTH + x;

            if (y == (int)barPos) {
                topScreenBuffer[idx] = 0xFFFFFFFF;
            } else {
                topScreenBuffer[idx] = 0xFF0B1726 | ((frameCounter & 0x3F) << 8);
            }

            if (is_touching && x >= touch_x - 5 && x <= touch_x + 5 && y >= touch_y - 5 && y <= touch_y + 5) {
                bottomScreenBuffer[idx] = 0xFF00FF88;
            } else {
                bottomScreenBuffer[idx] = 0xFF141414;
            }
        }
    }

    // 2. Generador de audio PCM estéreo (Frecuencia activa a 440 Hz)
    float frequency = 440.0f;
    float phaseIncrement = (2.0f * 3.14159265f * frequency) / 44100.0f;

    for (int i = 0; i < SAMPLES_PER_FRAME; i++) {
        int16_t sample = (int16_t)(sinf(audioPhase) * 4000.0f);
        audioBuffer[i * 2] = sample;     // Canal Izquierdo
        audioBuffer[i * 2 + 1] = sample; // Canal Derecho
        audioPhase += phaseIncrement;
        if (audioPhase > 2.0f * 3.14159265f) {
            audioPhase -= 2.0f * 3.14159265f;
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

JNIEXPORT void JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeGetAudioSamples(
    JNIEnv *env, jobject thiz, jshortArray out_buffer, jint count
) {
    env->SetShortArrayRegion(out_buffer, 0, count, (jshort*)audioBuffer);
}

}
