
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



extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_uttra_app_transcription_UttraNativeBridge_nativeDetectLanguage(
        JNIEnv* env,
        jobject /* thiz */,
        jlong contextHandle,
        jfloatArray audioSamples
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

    if (sampleCount <= 0) {
        env->ThrowNew(
                env->FindClass("java/lang/IllegalArgumentException"),
                "Empty audio samples"
        );
        return nullptr;
    }

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

    whisper_reset_timings(context);
    const auto startTime =
            std::chrono::steady_clock::now();

    __android_log_print(
            ANDROID_LOG_INFO,
            "UttraNative",
            "UTTRA LID CONTEXT: audioCtx=%d melWindowFrames=%d samples=%d",
            whisper_n_audio_ctx(context),
            2 * whisper_n_audio_ctx(context),
            sampleCount
    );

    // Prepare mel features from 16 kHz mono PCM samples.
    const int melResult = whisper_pcm_to_mel(
            context,
            samples.data(),
            sampleCount,
            2
    );
    const auto melEndTime =
            std::chrono::steady_clock::now();

    if (melResult != 0) {
        env->ThrowNew(
                env->FindClass("java/lang/IllegalStateException"),
                "Whisper mel preparation failed"
        );
        return nullptr;
    }

    const int languageCount = whisper_lang_max_id() + 1;

    std::vector<float> probabilities(
            languageCount,
            0.0f
    );

    const int detectedId = whisper_lang_auto_detect(
            context,
            0,
            4,
            probabilities.data()
    );


    const auto detectionEndTime =
            std::chrono::steady_clock::now();


    if (auto* timings = whisper_get_timings(context)) {
        __android_log_print(
                ANDROID_LOG_INFO,
                "UttraNative",
                "UTTRA LID STAGES: "
                "encoderMs=%.2f decoderMs=%.2f "
                "batchDecoderMs=%.2f promptMs=%.2f",
                static_cast<double>(timings->encode_ms),
                static_cast<double>(timings->decode_ms),
                static_cast<double>(timings->batchd_ms),
                static_cast<double>(timings->prompt_ms)
        );

        delete timings;
    }


    const auto melMs =
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    melEndTime - startTime
            ).count();

    const auto detectionMs =
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    detectionEndTime - melEndTime
            ).count();

    __android_log_print(
            ANDROID_LOG_INFO,
            "UttraNative",
            "UTTRA LANGUAGE TIMING: melMs=%lld, detectMs=%lld",
            static_cast<long long>(melMs),
            static_cast<long long>(detectionMs)
    );


    if (detectedId < 0) {
        env->ThrowNew(
                env->FindClass("java/lang/IllegalStateException"),
                "Whisper language detection failed"
        );
        return nullptr;
    }

    const int englishId = whisper_lang_id("en");
    const int malayalamId = whisper_lang_id("ml");

    const float englishProbability =
            englishId >= 0 ? probabilities[englishId] : 0.0f;

    const float malayalamProbability =
            malayalamId >= 0 ? probabilities[malayalamId] : 0.0f;

    const auto elapsedMs =
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now() - startTime
            ).count();

    const char* detectedLanguage =
            whisper_lang_str(detectedId);

    std::string diagnostic =
            std::string("detected=") + detectedLanguage +
            ", en=" + std::to_string(englishProbability) +
            ", ml=" + std::to_string(malayalamProbability) +
            ", elapsedMs=" + std::to_string(elapsedMs);

    __android_log_print(
            ANDROID_LOG_INFO,
            "UttraNative",
            "UTTRA LANGUAGE DETECTION: %s",
            diagnostic.c_str()
    );


    // Structured language scores:
    // index 0 = English, index 1 = Malayalam.
    const jfloat scores[] = {
            englishProbability,
            malayalamProbability
    };

    jfloatArray result = env->NewFloatArray(2);

    if (result == nullptr) {
        return nullptr;
    }

    env->SetFloatArrayRegion(
            result,
            0,
            2,
            scores
    );

    return result;

}

