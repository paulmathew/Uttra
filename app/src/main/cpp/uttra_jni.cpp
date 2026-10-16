
#include <jni.h>
#include "whisper.h"
#include <cstdint>
#include <string>
#include <android/log.h>
#include <chrono>
#include <vector>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_uttra_app_transcription_UttraNativeBridge_nativeWhisperInfo(
        JNIEnv* env,
        jobject /* thiz */
) {
    const char* info = whisper_print_system_info();

    return env->NewStringUTF(
            info != nullptr ? info : "Unknown"
    );
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_uttra_app_transcription_UttraNativeBridge_nativeLoadModel(
        JNIEnv* env,
        jobject /* thiz */,
        jstring modelPath
) {
    const char* path = env->GetStringUTFChars(
            modelPath, nullptr
    );

    if (path == nullptr) {
        return JNI_FALSE;
    }

    whisper_context_params params =
            whisper_context_default_params();

    whisper_context* context =
            whisper_init_from_file_with_params(
                    path,
                    params
            );

    env->ReleaseStringUTFChars(modelPath, path);

    if (context == nullptr) {
        return JNI_FALSE;
    }

    whisper_free(context);

    return JNI_TRUE;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_uttra_app_transcription_UttraNativeBridge_nativeCreateContext(
        JNIEnv* env,
        jobject /* thiz */,
        jstring modelPath
) {
    if (modelPath == nullptr) {
        return 0;
    }

    const char* path = env->GetStringUTFChars(
            modelPath, nullptr
    );

    if (path == nullptr) {
        return 0;
    }

    whisper_context_params params =
            whisper_context_default_params();

    whisper_context* context =
            whisper_init_from_file_with_params(
                    path,
                    params
            );

    env->ReleaseStringUTFChars(modelPath, path);

    return reinterpret_cast<jlong>(context);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_uttra_app_transcription_UttraNativeBridge_nativeFreeContext(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong contextHandle
) {
    auto* context =
            reinterpret_cast<whisper_context*>(contextHandle);

    if (context != nullptr) {
        whisper_free(context);
    }
}



static void whisperProgressCallback(
        struct whisper_context* /* context */,
        struct whisper_state* /* state */,
        int progress,
        void* /* userData */
) {
    __android_log_print(
            ANDROID_LOG_INFO,
            "UttraNative",
            "WHISPER PROGRESS: %d%%",
            progress
    );
}
static bool whisperEncoderCallback(
        struct whisper_context* /* context */,
        struct whisper_state* /* state */,
        void* /* userData */
) {
    __android_log_print(
            ANDROID_LOG_INFO,
            "UttraNative",
            "WHISPER ENCODER: beginning"
    );

    return true;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_uttra_app_transcription_UttraNativeBridge_nativeTranscribe(
        JNIEnv* env,
        jobject /* thiz */,
        jlong contextHandle,
        jfloatArray audioSamples,
        jstring languageCode
) {
    auto* context =
            reinterpret_cast<whisper_context*>(contextHandle);

    if (context == nullptr || audioSamples == nullptr) {
        env->ThrowNew(
                env->FindClass("java/lang/IllegalArgumentException"),
                "Invalid Whisper context or audio samples"
        );
        return nullptr;
    }

    const jsize sampleCount =
            env->GetArrayLength(audioSamples);

    if (sampleCount == 0) {
        return env->NewStringUTF("");
    }

    // Access the audio samples supplied by Kotlin.
//    jfloat* samples = env->GetFloatArrayElements(
//            audioSamples, nullptr
//    );
//
//    if (samples == nullptr) {
//        return nullptr;
//    }

    std::vector<float> samples(sampleCount);

    env->GetFloatArrayRegion(
            audioSamples,
            0,
            sampleCount,
            samples.data()
    );

    if (env->ExceptionCheck()) {
        return nullptr;
    }

    whisper_full_params params =
            whisper_full_default_params(
                    WHISPER_SAMPLING_GREEDY
            );

    params.n_threads = 2;
    params.translate = false;
    params.no_context = true;


    params.print_progress = false;
    params.print_timestamps = false;
    params.print_realtime = false;

    params.progress_callback = whisperProgressCallback;
    params.progress_callback_user_data = nullptr;

    params.encoder_begin_callback = whisperEncoderCallback;
    params.encoder_begin_callback_user_data = nullptr;


    // Default to automatic language detection.
    std::string language = "auto";

    if (languageCode != nullptr) {
        const char* chars =
                env->GetStringUTFChars(languageCode, nullptr);
//
//        if (chars == nullptr) {
//            env->ReleaseFloatArrayElements(
//                    audioSamples,    samples.data(),
//                     JNI_ABORT
//            );
//            return nullptr;
//        }
        if (chars == nullptr) {
            return nullptr;
        }

        language = chars;

        env->ReleaseStringUTFChars(
                languageCode, chars
        );
    }

    params.language = language.c_str();

//    const int result = whisper_full(
//            context,
//            params,
//            samples,
//            sampleCount
//    );
    __android_log_print(
            ANDROID_LOG_INFO,
            "UttraNative",
            "WHISPER LANGUAGE: %s",
            params.language
    );
    __android_log_print(
            ANDROID_LOG_INFO,
            "UttraNative",
            "WHISPER NATIVE: inference started, samples=%d",
            sampleCount
    );

    const auto startTime =
            std::chrono::steady_clock::now();

    const int result = whisper_full(
            context,
            params,
            samples.data(),
            sampleCount
    );

    const auto endTime =
            std::chrono::steady_clock::now();
    whisper_print_timings(context);

    const auto elapsedMs =
            std::chrono::duration_cast<
                    std::chrono::milliseconds
            >(endTime - startTime).count();

    __android_log_print(
            ANDROID_LOG_INFO,
            "UttraNative",
            "WHISPER NATIVE: inference returned, result=%d, elapsedMs=%lld",
            result,
            static_cast<long long>(elapsedMs)
    );

//    env->ReleaseFloatArrayElements(
//            audioSamples,
//            samples.data(),
//            JNI_ABORT
//    );

    if (result != 0) {
        env->ThrowNew(
                env->FindClass("java/lang/IllegalStateException"),
                "Whisper inference failed"
        );
        return nullptr;
    }

    std::string transcription;

    const int segmentCount =
            whisper_full_n_segments(context);

    for (int i = 0; i < segmentCount; ++i) {
        const char* text =
                whisper_full_get_segment_text(context, i);

        if (text != nullptr) {
            transcription += text;
        }
    }

    return env->NewStringUTF(transcription.c_str());
}
