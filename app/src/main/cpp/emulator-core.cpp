#include <jni.h>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <dlfcn.h>
#include <android/log.h>

#define LOG_TAG "MelonBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define DS_WIDTH 256
#define DS_HEIGHT 192
#define BUFFER_SIZE (DS_WIDTH * DS_HEIGHT * 4)
#define AUDIO_BUFFER_SIZE 4096

static uint32_t topScreenBuffer[DS_WIDTH * DS_HEIGHT];
static uint32_t bottomScreenBuffer[DS_WIDTH * DS_HEIGHT];

static int16_t audioRingBuffer[AUDIO_BUFFER_SIZE];
static size_t audioWritePos = 0;

static int currentKeyMask = 0x0FFF;
static int touchCoordX = 0;
static int touchCoordY = 0;
static bool touchIsActive = false;
static bool isCoreLoaded = false;
static bool isGameLoaded = false;

struct retro_game_info {
    const char *path;
    const void *data;
    size_t size;
    const char *meta;
};

static void (*core_init)(void) = nullptr;
static void (*core_deinit)(void) = nullptr;
static bool (*core_load_game)(const struct retro_game_info *game) = nullptr;
static void (*core_unload_game)(void) = nullptr;
static void (*core_run)(void) = nullptr;
static void (*core_set_environment)(bool (*)(unsigned, void*)) = nullptr;
static void (*core_set_video_refresh)(void (*)(const void*, unsigned, unsigned, size_t)) = nullptr;
static void (*core_set_audio_sample_batch)(size_t (*)(const int16_t*, size_t)) = nullptr;
static void (*core_set_input_poll)(void (*)(void)) = nullptr;
static void (*core_set_input_state)(int16_t (*)(unsigned, unsigned, unsigned, unsigned)) = nullptr;

static void *coreHandle = nullptr;

static bool cb_environment(unsigned cmd, void *data) {
    return true;
}

static void cb_video_refresh(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data) return;

    const uint32_t *src = (const uint32_t *)data;
    size_t stride = pitch / sizeof(uint32_t);

    for (unsigned y = 0; y < DS_HEIGHT && y < height; y++) {
        for (unsigned x = 0; x < DS_WIDTH && x < width; x++) {
            topScreenBuffer[y * DS_WIDTH + x] = src[y * stride + x] | 0xFF000000;
        }
    }

    for (unsigned y = 0; y < DS_HEIGHT && (y + DS_HEIGHT) < height; y++) {
        for (unsigned x = 0; x < DS_WIDTH && x < width; x++) {
            bottomScreenBuffer[y * DS_WIDTH + x] = src[(y + DS_HEIGHT) * stride + x] | 0xFF000000;
        }
    }
}

static size_t cb_audio_sample_batch(const int16_t *data, size_t frames) {
    size_t samples = frames * 2;
    for (size_t i = 0; i < samples; i++) {
        audioRingBuffer[audioWritePos] = data[i];
        audioWritePos = (audioWritePos + 1) % AUDIO_BUFFER_SIZE;
    }
    return frames;
}

static void cb_input_poll(void) {}

static int16_t cb_input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0) return 0;

    if (device == 1) { // RETRO_DEVICE_JOYPAD
        switch (id) {
            case 0: return !(currentKeyMask & (1 << 1)) ? 1 : 0;  // B
            case 1: return !(currentKeyMask & (1 << 11)) ? 1 : 0; // Y
            case 2: return !(currentKeyMask & (1 << 2)) ? 1 : 0;  // SELECT
            case 3: return !(currentKeyMask & (1 << 3)) ? 1 : 0;  // START
            case 4: return !(currentKeyMask & (1 << 6)) ? 1 : 0;  // UP
            case 5: return !(currentKeyMask & (1 << 7)) ? 1 : 0;  // DOWN
            case 6: return !(currentKeyMask & (1 << 5)) ? 1 : 0;  // LEFT
            case 7: return !(currentKeyMask & (1 << 4)) ? 1 : 0;  // RIGHT
            case 8: return !(currentKeyMask & (1 << 0)) ? 1 : 0;  // A
            case 9: return !(currentKeyMask & (1 << 10)) ? 1 : 0; // X
            case 10: return !(currentKeyMask & (1 << 9)) ? 1 : 0; // L
            case 11: return !(currentKeyMask & (1 << 8)) ? 1 : 0; // R
            default: return 0;
        }
    }

    if (device == 6) { // RETRO_DEVICE_POINTER
        if (id == 0) return (int16_t)(((float)touchCoordX / 255.0f) * 65534.0f - 32767.0f);
        if (id == 1) return (int16_t)(((float)touchCoordY / 191.0f) * 65534.0f - 32767.0f);
        if (id == 2) return touchIsActive ? 1 : 0;
    }

    return 0;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeInit(JNIEnv *env, jobject thiz, jstring system_path) {
    const char *nativePath = env->GetStringUTFChars(system_path, nullptr);

    char libPath[512];
    snprintf(libPath, sizeof(libPath), "%s/../lib/libmelonds.so", nativePath);

    coreHandle = dlopen("libmelonds.so", RTLD_NOW);
    if (!coreHandle) {
        coreHandle = dlopen(libPath, RTLD_NOW);
    }

    if (!coreHandle) {
        LOGE("No se pudo cargar libmelonds.so: %s", dlerror());
        env->ReleaseStringUTFChars(system_path, nativePath);
        return JNI_FALSE;
    }

    core_init = (void (*)(void))dlsym(coreHandle, "retro_init");
    core_deinit = (void (*)(void))dlsym(coreHandle, "retro_deinit");
    core_load_game = (bool (*)(const struct retro_game_info*))dlsym(coreHandle, "retro_load_game");
    core_unload_game = (void (*)(void))dlsym(coreHandle, "retro_unload_game");
    core_run = (void (*)(void))dlsym(coreHandle, "retro_run");
    core_set_environment = (void (*)(bool (*)(unsigned, void*)))dlsym(coreHandle, "retro_set_environment");
    core_set_video_refresh = (void (*)(void (*)(const void*, unsigned, unsigned, size_t)))dlsym(coreHandle, "retro_set_video_refresh");
    core_set_audio_sample_batch = (size_t (*)(size_t (*)(const int16_t*, size_t)))dlsym(coreHandle, "retro_set_audio_sample_batch");
    core_set_input_poll = (void (*)(void (*)(void)))dlsym(coreHandle, "retro_set_input_poll");
    core_set_input_state = (void (*)(int16_t (*)(unsigned, unsigned, unsigned, unsigned)))dlsym(coreHandle, "retro_set_input_state");

    if (core_set_environment) core_set_environment(cb_environment);
    if (core_set_video_refresh) core_set_video_refresh(cb_video_refresh);
    if (core_set_audio_sample_batch) core_set_audio_sample_batch(cb_audio_sample_batch);
    if (core_set_input_poll) core_set_input_poll(cb_input_poll);
    if (core_set_input_state) core_set_input_state(cb_input_state);

    if (core_init) core_init();

    isCoreLoaded = true;
    env->ReleaseStringUTFChars(system_path, nativePath);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeLoadRom(JNIEnv *env, jobject thiz, jstring rom_path) {
    if (!isCoreLoaded || !core_load_game) {
        return env->NewStringUTF("Error: melonDS no está inicializado");
    }

    const char *path = env->GetStringUTFChars(rom_path, nullptr);

    FILE *f = fopen(path, "rb");
    if (!f) {
        env->ReleaseStringUTFChars(rom_path, path);
        return env->NewStringUTF("Error: No se pudo abrir la ROM");
    }

    fseek(f, 0, SEEK_END);
    size_t size = ftell(f);
    fseek(f, 0, SEEK_SET);

    void *romData = malloc(size);
    fread(romData, 1, size, f);
    fclose(f);

    struct retro_game_info gameInfo;
    gameInfo.path = path;
    gameInfo.data = romData;
    gameInfo.size = size;
    gameInfo.meta = nullptr;

    bool success = core_load_game(&gameInfo);
    free(romData);
    env->ReleaseStringUTFChars(rom_path, path);

    if (success) {
        isGameLoaded = true;
        return env->NewStringUTF("melonDS: ¡Juego cargado y listo!");
    } else {
        return env->NewStringUTF("Error: melonDS no pudo montar la ROM");
    }
}

JNIEXPORT void JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeRunFrame(
    JNIEnv *env, jobject thiz,
    jint key_mask, jint touch_x, jint touch_y, jboolean is_touching
) {
    currentKeyMask = key_mask;
    touchCoordX = touch_x;
    touchCoordY = touch_y;
    touchIsActive = is_touching;

    if (isGameLoaded && core_run) {
        core_run();
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
    env->SetShortArrayRegion(out_buffer, 0, count, (jshort*)audioRingBuffer);
}

JNIEXPORT jstring JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeGetCpuStatus(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(isGameLoaded ? "melonDS: Activo a 60 FPS" : "En espera de ROM");
}

}
