#include <jni.h>
#include <cstdio>
#include <cstring>
#include <string>
#include <utility>
#include <vector>
#include "needle.h"

namespace {
std::vector<unsigned char> g_model;

std::vector<unsigned char> readFile(const char* path) {
    std::vector<unsigned char> data;
    FILE* f = std::fopen(path, "rb");
    if (!f) return data;
    std::fseek(f, 0, SEEK_END);
    long size = std::ftell(f);
    std::fseek(f, 0, SEEK_SET);
    if (size > 0) {
        data.resize((size_t) size);
        if (std::fread(data.data(), 1, data.size(), f) != data.size()) data.clear();
    }
    std::fclose(f);
    return data;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wahtuh_pho_asr_WhistleEngine_nativeLoad(JNIEnv* env, jclass, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    std::vector<unsigned char> data = readFile(p);
    env->ReleaseStringUTFChars(path, p);
    if (data.empty()) return JNI_FALSE;
    g_model = std::move(data);
    int rc = needle_load(g_model.data(), (unsigned long long) g_model.size());
    if (rc < 0) return JNI_FALSE;
    return (needle_models() & NEEDLE_SPEECH) != 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wahtuh_pho_asr_WhistleEngine_nativeTranscribe(JNIEnv* env, jclass,
        jfloatArray pcm, jstring language, jstring keywords) {
    jsize n = env->GetArrayLength(pcm);
    jfloat* samples = env->GetFloatArrayElements(pcm, nullptr);
    const char* lang = language == nullptr ? nullptr : env->GetStringUTFChars(language, nullptr);
    const char* keys = keywords == nullptr ? nullptr : env->GetStringUTFChars(keywords, nullptr);
    std::vector<char> out(1 << 20);
    int rc = needle_transcribe(samples, (int) n, lang, keys, 0, out.data(), (int) out.size());
    if (lang) env->ReleaseStringUTFChars(language, lang);
    if (keys) env->ReleaseStringUTFChars(keywords, keys);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (rc < 0) {
        const char* error = needle_last_error();
        std::string message = error == nullptr ? "Whistle failed" : error;
        jclass ex = env->FindClass("java/lang/IllegalStateException");
        if (ex) env->ThrowNew(ex, message.c_str());
        return nullptr;
    }
    return env->NewStringUTF(out.data());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wahtuh_pho_asr_WhistleEngine_nativeError(JNIEnv* env, jclass) {
    const char* error = needle_last_error();
    return env->NewStringUTF(error == nullptr ? "" : error);
}
