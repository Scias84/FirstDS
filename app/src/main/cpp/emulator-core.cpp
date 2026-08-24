#include <jni.h>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <dlfcn.h>
#include <pthread.h>
#include <android/log.h>

#define LOG_TAG "MelonBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define DS_WIDTH 256
#define DS_HEIGHT 192
#define BUFFER_SIZE (DS_WIDTH * DS_HEIGHT * 4)

#define AUDIO_RING_SIZE 65536

static uint32_t topScreenBuffer[DS_WIDTH * DS_HEIGHT];
static uint32_t bottomScreenBuffer[DS_WIDTH * DS_HEIGHT];

static int16_t audioFifo[AUDIO_RING_SIZE];
static size_t audioReadIdx = 0;
static size_t audioWriteIdx = 0;
static pthread_mutex_t audioMutex = PTHREAD_MUTEX_INITIALIZER;

static int currentKeyMask = 0x0FFF;
static int touchCoordX = 0;
static int touchCoordY = 0;
static bool touchIsActive = false;
static bool isCoreLoaded = false;
static bool isGameLoaded = false;

static int currentPixelFormat = 1;
static char systemDirectory[512] = {0};
static void *persistedRomData = nullptr;
static pthread_mutex_t coreMutex = PTHREAD_MUTEX_INITIALIZER;

struct retro_game_info {
    const char *path;
    const void *data;
    size_t size;
    const char *meta;
};

struct retro_variable {
    const char *key;
    const char *value;
};

typedef bool (*retro_environment_t)(unsigned cmd, void *data);
typedef void (*retro_video_refresh_t)(const void *data, unsigned width, unsigned height, size_t pitch);
typedef void (*retro_audio_sample_t)(int16_t left, int16_t right);
typedef size_t (*retro_audio_sample_batch_t)(const int16_t *data, size_t frames);
typedef void (*retro_input_poll_t)(void);
typedef int16_t (*retro_input_state_t)(unsigned port, unsigned device, unsigned index, unsigned id);

typedef void (*fn_retro_init)(void);
typedef void (*fn_retro_deinit)(void);
typedef bool (*fn_retro_load_game)(const struct retro_game_info *game);
typedef void (*fn_retro_unload_game)(void);
typedef void (*fn_retro_run)(void);
typedef void (*fn_retro_set_environment)(retro_environment_t);
typedef void (*fn_retro_set_video_refresh)(retro_video_refresh_t);
typedef void (*fn_retro_set_audio_sample)(retro_audio_sample_t);
typedef void (*fn_retro_set_audio_sample_batch)(retro_audio_sample_batch_t);
typedef void (*fn_retro_set_input_poll)(retro_input_poll_t);
typedef void (*fn_retro_set_input_state)(retro_input_state_t);

static fn_retro_init core_init = nullptr;
static fn_retro_deinit core_deinit = nullptr;
static fn_retro_load_game core_load_game = nullptr;
static fn_retro_unload_game core_unload_game = nullptr;
static fn_retro_run core_run = nullptr;
static fn_retro_set_environment core_set_environment = nullptr;
static fn_retro_set_video_refresh core_set_video_refresh = nullptr;
static fn_retro_set_audio_sample core_set_audio_sample = nullptr;
static fn_retro_set_audio_sample_batch core_set_audio_sample_batch = nullptr;
static fn_retro_set_input_poll core_set_input_poll = nullptr;
static fn_retro_set_input_state core_set_input_state = nullptr;

static void *coreHandle = nullptr;

static bool cb_environment(unsigned cmd, void *data) {
    switch (cmd) {
        case 10:
            if (data) {
                currentPixelFormat = *(const int*)data;
                return true;
            }
            return false;

        case 15:
            if (data) {
                struct retro_variable *var = (struct retro_variable*)data;
                if (var && var->key) {
                    if (strcmp(var->key, "melonds_boot_directly") == 0 || strcmp(var->key, "melonds_boot_direct") == 0) {
                        var->value = "enabled";
                        return true;
                    }
                    if (strcmp(var->key, "melonds_use_bios") == 0) {
                        var->value = "disabled";
                        return true;
                    }
                    if (strcmp(var->key, "melonds_ds_bios") == 0) {
                        var->value = "FreeBIOS";
                        return true;
                    }
                    if (strcmp(var->key, "melonds_jit") == 0) {
                        var->value = "disabled";
                        return true;
                    }
                    if (strcmp(var->key, "melonds_threaded_renderer") == 0 || strcmp(var->key, "melonds_opengl") == 0) {
                        var->value = "disabled";
                        return true;
                    }
                    if (strcmp(var->key, "melonds_ds_mode") == 0) {
                        var->value = "DS";
                        return true;
                    }
                }
            }
            return false;

        case 9:
        case 31:
            if (data && systemDirectory[0] != '\0') {
                *(const char**)data = systemDirectory;
                return true;
            }
            return false;

        case 3:
            if (data) {
                *(bool*)data = true;
                return true;
            }
            return false;

        default:
            return false;
    }
}

static void cb_video_refresh(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data || width == 0 || height == 0) return;

    if (currentPixelFormat == 1) {
        const uint32_t *src = (const uint32_t *)data;
        size_t stride = pitch / sizeof(uint32_t);

        for (unsigned y = 0; y < DS_HEIGHT && y < height; y++) {
            for (unsigned x = 0; x < DS_WIDTH && x < width; x++) {
                uint32_t p = src[y * stride + x];
                uint8_t r = (p >> 16) & 0xFF;
                uint8_t g = (p >> 8) & 0xFF;
                uint8_t b = p & 0xFF;
                topScreenBuffer[y * DS_WIDTH + x] = 0xFF000000 | (b << 16) | (g << 8) | r;
            }
        }

        for (unsigned y = 0; y < DS_HEIGHT && (y + DS_HEIGHT) < height; y++) {
            for (unsigned x = 0; x < DS_WIDTH && x < width; x++) {
                uint32_t p = src[(y + DS_HEIGHT) * stride + x];
                uint8_t r = (p >> 16) & 0xFF;
                uint8_t g = (p >> 8) & 0xFF;
                uint8_t b = p & 0xFF;
                bottomScreenBuffer[y * DS_WIDTH + x] = 0xFF000000 | (b << 16) | (g << 8) | r;
            }
        }
    } else {
        const uint16_t *src = (const uint16_t *)data;
        size_t stride = pitch / sizeof(uint16_t);

        for (unsigned y = 0; y < DS_HEIGHT && y < height; y++) {
            for (unsigned x = 0; x < DS_WIDTH && x < width; x++) {
                uint16_t c = src[y * stride + x];
                uint8_t r = ((c >> 11) & 0x1F) << 3;
                uint8_t g = ((c >> 5) & 0x3F) << 2;
                uint8_t b = (c & 0x1F) << 3;
                topScreenBuffer[y * DS_WIDTH + x] = 0xFF000000 | (b << 16) | (g << 8) | r;
            }
        }

        for (unsigned y = 0; y < DS_HEIGHT && (y + DS_HEIGHT) < height; y++) {
            for (unsigned x = 0; x < DS_WIDTH && x < width; x++) {
                uint16_t c = src[(y + DS_HEIGHT) * stride + x];
                uint8_t r = ((c >> 11) & 0x1F) << 3;
                uint8_t g = ((c >> 5) & 0x3F) << 2;
                uint8_t b = (c & 0x1F) << 3;
                bottomScreenBuffer[y * DS_WIDTH + x] = 0xFF000000 | (b << 16) | (g << 8) | r;
            }
        }
    }
}

static void cb_audio_sample(int16_t left, int16_t right) {
    pthread_mutex_lock(&audioMutex);
    audioFifo[audioWriteIdx] = left;
    audioFifo[(audioWriteIdx + 1) % AUDIO_RING_SIZE] = right;
    audioWriteIdx = (audioWriteIdx + 2) % AUDIO_RING_SIZE;
    pthread_mutex_unlock(&audioMutex);
}

static size_t cb_audio_sample_batch(const int16_t *data, size_t frames) {
    if (!data || frames == 0) return 0;
    size_t samples = frames * 2;

    pthread_mutex_lock(&audioMutex);
    for (size_t i = 0; i < samples; i++) {
        audioFifo[audioWriteIdx] = data[i];
        audioWriteIdx = (audioWriteIdx + 1) % AUDIO_RING_SIZE;
    }
    pthread_mutex_unlock(&audioMutex);
    return frames;
}

static void cb_input_poll(void) {}

static int16_t cb_input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0) return 0;

    if (device == 1) {
        switch (id) {
            case 0: return !(currentKeyMask & (1 << 1)) ? 1 : 0;
            case 1: return !(currentKeyMask & (1 << 11)) ? 1 : 0;
            case 2: return !(currentKeyMask & (1 << 2)) ? 1 : 0;
            case 3: return !(currentKeyMask & (1 << 3)) ? 1 : 0;
            case 4: return !(currentKeyMask & (1 << 6)) ? 1 : 0;
            case 5: return !(currentKeyMask & (1 << 7)) ? 1 : 0;
            case 6: return !(currentKeyMask & (1 << 5)) ? 1 : 0;
            case 7: return !(currentKeyMask & (1 << 4)) ? 1 : 0;
            case 8: return !(currentKeyMask & (1 << 0)) ? 1 : 0;
            case 9: return !(currentKeyMask & (1 << 10)) ? 1 : 0;
            case 10: return !(currentKeyMask & (1 << 9)) ? 1 : 0;
            case 11: return !(currentKeyMask & (1 << 8)) ? 1 : 0;
            default: return 0;
        }
    }

    if (device == 6) {
        if (id == 0) return (int16_t)(((float)touchCoordX / 255.0f) * 65534.0f - 32767.0f);
        if (id == 1) return (int16_t)(((float)touchCoordY / 191.0f) * 65534.0f - 32767.0f);
        if (id == 2) return touchIsActive ? 1 : 0;
    }

    return 0;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeInit(JNIEnv *env, jobject thiz, jstring system_path, jstring lib_path) {
    const char *sysPath = env->GetStringUTFChars(system_path, nullptr);
    const char *libPath = env->GetStringUTFChars(lib_path, nullptr);

    snprintf(systemDirectory, sizeof(systemDirectory), "%s", sysPath);

    if (!isCoreLoaded || coreHandle == nullptr) {
        char fullLibPath[512];
        snprintf(fullLibPath, sizeof(fullLibPath), "%s/libmelonds.so", libPath);

        coreHandle = dlopen("libmelonds.so", RTLD_NOW | RTLD_GLOBAL);
        if (!coreHandle) {
            coreHandle = dlopen(fullLibPath, RTLD_NOW | RTLD_GLOBAL);
        }

        if (!coreHandle) {
            LOGE("Error abriendo libmelonds.so: %s", dlerror());
            env->ReleaseStringUTFChars(system_path, sysPath);
            env->ReleaseStringUTFChars(lib_path, libPath);
            return JNI_FALSE;
        }

        core_init = (fn_retro_init)dlsym(coreHandle, "retro_init");
        core_deinit = (fn_retro_deinit)dlsym(coreHandle, "retro_deinit");
        core_load_game = (fn_retro_load_game)dlsym(coreHandle, "retro_load_game");
        core_unload_game = (fn_retro_unload_game)dlsym(coreHandle, "retro_unload_game");
        core_run = (fn_retro_run)dlsym(coreHandle, "retro_run");
        core_set_environment = (fn_retro_set_environment)dlsym(coreHandle, "retro_set_environment");
        core_set_video_refresh = (fn_retro_set_video_refresh)dlsym(coreHandle, "retro_set_video_refresh");
        core_set_audio_sample = (fn_retro_set_audio_sample)dlsym(coreHandle, "retro_set_audio_sample");
        core_set_audio_sample_batch = (fn_retro_set_audio_sample_batch)dlsym(coreHandle, "retro_set_audio_sample_batch");
        core_set_input_poll = (fn_retro_set_input_poll)dlsym(coreHandle, "retro_set_input_poll");
        core_set_input_state = (fn_retro_set_input_state)dlsym(coreHandle, "retro_set_input_state");

        if (core_set_environment) core_set_environment(cb_environment);
        if (core_set_video_refresh) core_set_video_refresh(cb_video_refresh);
        if (core_set_audio_sample) core_set_audio_sample(cb_audio_sample);
        if (core_set_audio_sample_batch) core_set_audio_sample_batch(cb_audio_sample_batch);
        if (core_set_input_poll) core_set_input_poll(cb_input_poll);
        if (core_set_input_state) core_set_input_state(cb_input_state);

        if (core_init) core_init();

        isCoreLoaded = true;
    }

    env->ReleaseStringUTFChars(system_path, sysPath);
    env->ReleaseStringUTFChars(lib_path, libPath);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeLoadRom(JNIEnv *env, jobject thiz, jstring rom_path) {
    if (!isCoreLoaded || !core_load_game) {
        return env->NewStringUTF("Error: melonDS no inicializado");
    }

    const char *path = env->GetStringUTFChars(rom_path, nullptr);

    FILE *f = fopen(path, "rb");
    if (!f) {
        env->ReleaseStringUTFChars(rom_path, path);
        return env->NewStringUTF("Error: No se pudo leer la ROM");
    }

    fseek(f, 0, SEEK_END);
    size_t size = ftell(f);
    fseek(f, 0, SEEK_SET);

    if (size == 0) {
        fclose(f);
        env->ReleaseStringUTFChars(rom_path, path);
        return env->NewStringUTF("Error: ROM vacía");
    }

    pthread_mutex_lock(&coreMutex);
    isGameLoaded = false;

    if (persistedRomData) {
        free(persistedRomData);
        persistedRomData = nullptr;
    }

    persistedRomData = malloc(size);
    if (!persistedRomData) {
        fclose(f);
        pthread_mutex_unlock(&coreMutex);
        env->ReleaseStringUTFChars(rom_path, path);
        return env->NewStringUTF("Error: RAM insuficiente");
    }

    fread(persistedRomData, 1, size, f);
    fclose(f);

    struct retro_game_info gameInfo;
    gameInfo.path = path;
    gameInfo.data = persistedRomData;
    gameInfo.size = size;
    gameInfo.meta = nullptr;

    bool success = core_load_game(&gameInfo);
    if (success) {
        isGameLoaded = true;
    }
    pthread_mutex_unlock(&coreMutex);

    env->ReleaseStringUTFChars(rom_path, path);

    if (success) {
        return env->NewStringUTF("¡Juego cargado correctamente!");
    } else {
        return env->NewStringUTF("Error: melonDS rechazó la ROM");
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

    if (!isGameLoaded || !core_run) return;

    if (pthread_mutex_trylock(&coreMutex) == 0) {
        core_run();
        pthread_mutex_unlock(&coreMutex);
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

JNIEXPORT jint JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeGetAudioSamples(
    JNIEnv *env, jobject thiz, jshortArray out_buffer, jint max_count
) {
    pthread_mutex_lock(&audioMutex);

    size_t available = (audioWriteIdx >= audioReadIdx)
                       ? (audioWriteIdx - audioReadIdx)
                       : (AUDIO_RING_SIZE - audioReadIdx + audioWriteIdx);

    size_t toRead = (available < (size_t)max_count) ? available : (size_t)max_count;

    if (toRead > 0) {
        jshort temp[toRead];
        for (size_t i = 0; i < toRead; i++) {
            temp[i] = audioFifo[audioReadIdx];
            audioReadIdx = (audioReadIdx + 1) % AUDIO_RING_SIZE;
        }
        env->SetShortArrayRegion(out_buffer, 0, toRead, temp);
    }

    pthread_mutex_unlock(&audioMutex);
    return (jint)toRead;
}

JNIEXPORT jstring JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeGetCpuStatus(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(isGameLoaded ? "Activo" : "Espera");
}

}
