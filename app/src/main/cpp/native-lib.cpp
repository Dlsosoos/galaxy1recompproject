#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <string>

#define TAG "G1Recomp"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

std::string g1_extract(int fd, const std::string& keyHex, const std::string& outDir);

static bool g_running = false;

static std::string J(JNIEnv* e, jstring s) {
    const char* c = e->GetStringUTFChars(s, nullptr);
    std::string r(c);
    e->ReleaseStringUTFChars(s, c);
    return r;
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_pedro_galaxy1recomp_NativeBridge_nativeVersion(JNIEnv* e, jobject) {
    return e->NewStringUTF("shell 0.1 (nucleo recompilado nao vinculado)");
}

// Retorna 0 = rodando, 1 = nucleo recompilado ainda nao vinculado
JNIEXPORT jint JNICALL
Java_com_pedro_galaxy1recomp_NativeBridge_nativeStart(JNIEnv* e, jobject, jint fd, jstring fmt,
                                                      jstring cfg, jstring mods, jstring cheats) {
    std::string format = J(e, fmt), config = J(e, cfg), modList = J(e, mods), cheatList = J(e, cheats);
    LOGI("start fd=%d fmt=%s", fd, format.c_str());
    LOGI("config=%s", config.c_str());
    LOGI("mods=%s", modList.c_str());
    LOGI("cheats=%s", cheatList.c_str());
    // TODO: ler o disco pelo fd (ISO direto / WBFS via tabela de setores),
    // extrair main.dol + arquivos, chamar o ponto de entrada recompilado,
    // aplicar mods (substituicao de arquivos) e cheats (patches de memoria / save).
    close(fd);
    g_running = false;
    return 1;
}

JNIEXPORT jstring JNICALL
Java_com_pedro_galaxy1recomp_NativeBridge_nativeExtract(JNIEnv* e, jobject, jint fd, jstring key, jstring out) {
    return e->NewStringUTF(g1_extract(fd, J(e, key), J(e, out)).c_str());
}

JNIEXPORT void JNICALL
Java_com_pedro_galaxy1recomp_NativeBridge_nativeStop(JNIEnv*, jobject) { g_running = false; }

}
