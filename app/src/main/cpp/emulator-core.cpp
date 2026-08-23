#include <jni.h>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cmath>

#define DS_WIDTH 256
#define DS_HEIGHT 192
#define BUFFER_SIZE (DS_WIDTH * DS_HEIGHT * 4)

#define SAMPLES_PER_FRAME 735
#define AUDIO_BUFFER_SIZE (SAMPLES_PER_FRAME * 2)

// Mapa de Memoria Oficial Nintendo DS
#define MAIN_RAM_SIZE (4 * 1024 * 1024) // 4MB
#define VRAM_SIZE     (656 * 1024)      // 656KB

static uint8_t mainRam[MAIN_RAM_SIZE];
static uint8_t vram[VRAM_SIZE];
static uint8_t romData[32 * 1024 * 1024]; // Búfer de ROM hasta 32MB
static size_t romSize = 0;

static uint32_t topScreenBuffer[DS_WIDTH * DS_HEIGHT];
static uint32_t bottomScreenBuffer[DS_WIDTH * DS_HEIGHT];
static int16_t audioBuffer[AUDIO_BUFFER_SIZE];

static bool isRomLoaded = false;
static uint32_t frameCounter = 0;
static float audioPhase = 0.0f;

// Estructura de Registros ARM946E-S
struct ARM9Registers {
    uint32_t r[16]; // R0-R12 (Generales), R13 (SP), R14 (LR), R15 (PC)
    uint32_t cpsr;  // Current Program Status Register
    bool halted;
    uint64_t cyclesExecuted;
};

static ARM9Registers arm9;

// Estructura de Cabecera NDS
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

// --- FUNCIONES DEL BUS DE MEMORIA ---
uint32_t memRead32(uint32_t address) {
    // 0x02000000 - 0x023FFFFF: Main RAM (4MB Espejada)
    if (address >= 0x02000000 && address < 0x03000000) {
        uint32_t offset = address & (MAIN_RAM_SIZE - 1);
        return *(uint32_t*)(&mainRam[offset]);
    }
    // 0x08000000+: Cartucho Game Card ROM
    if (address >= 0x08000000 && address < (0x08000000 + romSize)) {
        uint32_t offset = address - 0x08000000;
        return *(uint32_t*)(&romData[offset]);
    }
    return 0;
}

void memWrite32(uint32_t address, uint32_t val) {
    if (address >= 0x02000000 && address < 0x03000000) {
        uint32_t offset = address & (MAIN_RAM_SIZE - 1);
        *(uint32_t*)(&mainRam[offset]) = val;
    }
}

// --- INTÉRPRETE DE INSTRUCCIONES ARM9 (FETCH - DECODE - EXECUTE) ---
void executeArm9Step() {
    if (arm9.halted) return;

    uint32_t pc = arm9.r[15];
    uint32_t instr = memRead32(pc);
    arm9.r[15] += 4; // Avanzar PC

    uint32_t cond = (instr >> 28) & 0xF;
    if (cond == 0xE || cond == 0x0) { // Siempre o Condición Básica
        uint32_t type = (instr >> 24) & 0xF;

        // Salto Branch y Branch con Enlace (B / BL)
        if ((type & 0xE) == 0xA) {
            int32_t offset = (int32_t)((instr & 0x00FFFFFF) << 8) >> 6;
            if (type & 1) { // BL (Link)
                arm9.r[14] = arm9.r[15];
            }
            arm9.r[15] += offset + 4;
        }
        // Procesamiento de datos / Registro Movimiento (MOV, ADD, SUB)
        else if ((type & 0xC) == 0x0) {
            uint32_t opcode = (instr >> 21) & 0xF;
            uint32_t rd = (instr >> 12) & 0xF;
            uint32_t rn = (instr >> 16) & 0xF;
            uint32_t op2 = instr & 0xFF; // Operando inmediato simple

            if (opcode == 0xD) { // MOV Rd, Op2
                arm9.r[rd] = op2;
            } else if (opcode == 0x4) { // ADD Rd, Rn, Op2
                arm9.r[rd] = arm9.r[rn] + op2;
            } else if (opcode == 0x2) { // SUB Rd, Rn, Op2
                arm9.r[rd] = arm9.r[rn] - op2;
            }
        }
    }
    arm9.cyclesExecuted++;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeInit(JNIEnv *env, jobject thiz, jstring system_path) {
    memset(mainRam, 0, MAIN_RAM_SIZE);
    memset(vram, 0, VRAM_SIZE);
    memset(romData, 0, sizeof(romData));
    memset(&arm9, 0, sizeof(arm9));

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

    fseek(file, 0, SEEK_END);
    romSize = ftell(file);
    fseek(file, 0, SEEK_SET);

    if (romSize > sizeof(romData)) romSize = sizeof(romData);

    size_t readRom = fread(romData, 1, romSize, file);
    memcpy(&currentHeader, romData, sizeof(NdsHeader));
    fclose(file);

    if (readRom < sizeof(NdsHeader)) {
        env->ReleaseStringUTFChars(rom_path, path);
        return env->NewStringUTF("Error: ROM incompleta");
    }

    // 1. Cargar binario ARM9 en el espacio correspondiente de la RAM
    uint32_t ramOffset = currentHeader.arm9RamAddress & (MAIN_RAM_SIZE - 1);
    if (currentHeader.arm9RomOffset + currentHeader.arm9Size <= romSize && ramOffset + currentHeader.arm9Size <= MAIN_RAM_SIZE) {
        memcpy(&mainRam[ramOffset], &romData[currentHeader.arm9RomOffset], currentHeader.arm9Size);
    }

    // 2. Apuntar el Program Counter (PC / R15) al Entrypoint oficial
    arm9.r[15] = currentHeader.arm9EntryAddress;
    arm9.r[13] = 0x03002F7C; // Stack Pointer estándar ARM9
    arm9.cpsr = 0x1F;        // Modo System (32-bit ARM)
    arm9.halted = false;
    arm9.cyclesExecuted = 0;

    isRomLoaded = true;
    frameCounter = 0;

    char titleClean[13] = {0};
    memcpy(titleClean, currentHeader.gameTitle, 12);

    char info[128];
    snprintf(info, sizeof(info), "ARM9 Listo: %s [PC: 0x%08X]", titleClean, arm9.r[15]);

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

    // 1. Ejecutar ciclo de instrucciones ARM9 por cuadro (~5,000 ciclos por frame)
    for (int i = 0; i < 5000; i++) {
        executeArm9Step();
    }

    // 2. Renderizado de video a 60 FPS
    uint32_t barPos = (frameCounter % DS_HEIGHT);
    for (int y = 0; y < DS_HEIGHT; y++) {
        for (int x = 0; x < DS_WIDTH; x++) {
            int idx = y * DS_WIDTH + x;

            if (y == (int)barPos) {
                topScreenBuffer[idx] = 0xFFFFFFFF;
            } else {
                // Modulación dinámica de color basada en el estado del registro PC
                topScreenBuffer[idx] = 0xFF051221 | ((arm9.r[15] & 0xFF) << 8);
            }

            if (is_touching && x >= touch_x - 5 && x <= touch_x + 5 && y >= touch_y - 5 && y <= touch_y + 5) {
                bottomScreenBuffer[idx] = 0xFF00FF88;
            } else {
                bottomScreenBuffer[idx] = 0xFF141414;
            }
        }
    }

    // 3. Generador de Audio Estéreo
    float frequency = 440.0f;
    float phaseIncrement = (2.0f * 3.14159265f * frequency) / 44100.0f;

    for (int i = 0; i < SAMPLES_PER_FRAME; i++) {
        int16_t sample = (int16_t)(sinf(audioPhase) * 4000.0f);
        audioBuffer[i * 2] = sample;
        audioBuffer[i * 2 + 1] = sample;
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

JNIEXPORT jstring JNICALL
Java_com_ejemplo_emulador_NativeBridge_nativeGetCpuStatus(JNIEnv *env, jobject thiz) {
    char status[128];
    snprintf(status, sizeof(status), "PC: 0x%08X | Ciclos: %llu", arm9.r[15], (unsigned long long)arm9.cyclesExecuted);
    return env->NewStringUTF(status);
}

}
