#include <jni.h>
#include <android/log.h>
#include <onnxruntime_c_api.h>
#include <string>
#include <vector>
#include <cstring>
#include <algorithm>

#define LOG_TAG "OnnxJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static OrtEnv* g_env = nullptr;
static OrtSession* g_session = nullptr;
static OrtAllocator* g_allocator = nullptr;

static const OrtApi* GetApi() {
    static const OrtApi* api = nullptr;
    if (!api) {
        api = OrtGetApiBase()->GetApi(ORT_API_VERSION);
        if (!api) {
            LOGE("Failed to get ONNX Runtime API");
        }
    }
    return api;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_kime_plugin_prediction_association_association_NativeOnnxEngine_nativeInitialize(
    JNIEnv* env, jobject thiz, jstring model_path) {

    const OrtApi* api = GetApi();
    if (!api) {
        LOGE("ONNX Runtime API not available");
        return JNI_FALSE;
    }

    if (g_session) {
        LOGD("Session already initialized");
        return JNI_TRUE;
    }

    const char* modelPathStr = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Initializing ONNX Runtime with model: %s", modelPathStr);

    OrtStatus* status = nullptr;

    status = api->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "onnx_prediction", &g_env);
    if (status) {
        LOGE("Failed to create OrtEnv: %s", api->GetErrorMessage(status));
        api->ReleaseStatus(status);
        env->ReleaseStringUTFChars(model_path, modelPathStr);
        return JNI_FALSE;
    }

    status = api->GetAllocatorWithDefaultOptions(&g_allocator);
    if (status) {
        LOGE("Failed to get allocator: %s", api->GetErrorMessage(status));
        api->ReleaseStatus(status);
        env->ReleaseStringUTFChars(model_path, modelPathStr);
        return JNI_FALSE;
    }

    OrtSessionOptions* session_options = nullptr;
    status = api->CreateSessionOptions(&session_options);
    if (status) {
        LOGE("Failed to create session options: %s", api->GetErrorMessage(status));
        api->ReleaseStatus(status);
        env->ReleaseStringUTFChars(model_path, modelPathStr);
        return JNI_FALSE;
    }

    status = api->SetIntraOpNumThreads(session_options, 4);
    if (status) {
        LOGE("Failed to set intra op num threads: %s", api->GetErrorMessage(status));
        api->ReleaseStatus(status);
        api->ReleaseSessionOptions(session_options);
        env->ReleaseStringUTFChars(model_path, modelPathStr);
        return JNI_FALSE;
    }

    status = api->CreateSession(g_env, modelPathStr, session_options, &g_session);
    if (status) {
        LOGE("Failed to create session: %s", api->GetErrorMessage(status));
        api->ReleaseStatus(status);
        api->ReleaseSessionOptions(session_options);
        env->ReleaseStringUTFChars(model_path, modelPathStr);
        return JNI_FALSE;
    }

    api->ReleaseSessionOptions(session_options);
    env->ReleaseStringUTFChars(model_path, modelPathStr);

    LOGI("ONNX Runtime initialized successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_kingzcheung_kime_plugin_prediction_association_association_NativeOnnxEngine_nativePredict(
    JNIEnv* env, jobject thiz, jlongArray input_ids, jint top_k) {

    const OrtApi* api = GetApi();
    if (!api || !g_session) {
        LOGE("ONNX Runtime not initialized");
        return nullptr;
    }

    jsize input_len = env->GetArrayLength(input_ids);
    jlong* input_data = env->GetLongArrayElements(input_ids, nullptr);

    std::vector<int64_t> input_shape = {1, static_cast<int64_t>(input_len)};
    size_t input_shape_len = input_shape.size();

    OrtMemoryInfo* memory_info = nullptr;
    OrtStatus* status = api->CreateMemoryInfo("Cpu", OrtArenaAllocator, 0, OrtMemTypeDefault, &memory_info);
    if (status) {
        LOGE("Failed to create memory info: %s", api->GetErrorMessage(status));
        api->ReleaseStatus(status);
        env->ReleaseLongArrayElements(input_ids, input_data, JNI_ABORT);
        return nullptr;
    }

    OrtValue* input_tensor = nullptr;
    status = api->CreateTensorWithDataAsOrtValue(
        memory_info,
        input_data,
        input_len * sizeof(int64_t),
        input_shape.data(),
        input_shape_len,
        ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64,
        &input_tensor
    );
    api->ReleaseMemoryInfo(memory_info);

    if (status) {
        LOGE("Failed to create input tensor: %s", api->GetErrorMessage(status));
        api->ReleaseStatus(status);
        env->ReleaseLongArrayElements(input_ids, input_data, JNI_ABORT);
        return nullptr;
    }

    const char* input_names[] = {"input_ids"};
    const char* output_names[] = {"logits"};

    OrtValue* output_tensor = nullptr;
    status = api->Run(g_session, nullptr, input_names, (const OrtValue* const*)&input_tensor, 1,
                      output_names, 1, &output_tensor);

    api->ReleaseValue(input_tensor);
    env->ReleaseLongArrayElements(input_ids, input_data, JNI_ABORT);

    if (status) {
        LOGE("Failed to run session: %s", api->GetErrorMessage(status));
        api->ReleaseStatus(status);
        return nullptr;
    }

    OrtTensorTypeAndShapeInfo* output_info = nullptr;
    status = api->GetTensorTypeAndShape(output_tensor, &output_info);
    if (status) {
        LOGE("Failed to get output info: %s", api->GetErrorMessage(status));
        api->ReleaseStatus(status);
        api->ReleaseValue(output_tensor);
        return nullptr;
    }

    size_t dims_count = 0;
    status = api->GetDimensionsCount(output_info, &dims_count);
    if (status) {
        LOGE("Failed to get dimensions count: %s", api->GetErrorMessage(status));
        api->ReleaseStatus(status);
        api->ReleaseTensorTypeAndShapeInfo(output_info);
        api->ReleaseValue(output_tensor);
        return nullptr;
    }

    std::vector<int64_t> output_dims(dims_count);
    status = api->GetDimensions(output_info, output_dims.data(), dims_count);
    api->ReleaseTensorTypeAndShapeInfo(output_info);

    if (status) {
        LOGE("Failed to get dimensions: %s", api->GetErrorMessage(status));
        api->ReleaseStatus(status);
        api->ReleaseValue(output_tensor);
        return nullptr;
    }

    LOGD("Output shape: [%ld, %ld, %ld]", (long)output_dims[0], (long)output_dims[1], (long)output_dims[2]);

    float* output_data = nullptr;
    status = api->GetTensorMutableData(output_tensor, (void**)&output_data);
    if (status) {
        LOGE("Failed to get output data: %s", api->GetErrorMessage(status));
        api->ReleaseStatus(status);
        api->ReleaseValue(output_tensor);
        return nullptr;
    }

    int64_t vocab_size = output_dims[2];
    int64_t last_pos = output_dims[1] - 1;

    float* logits = output_data + last_pos * vocab_size;

    std::vector<std::pair<int, float>> scores;
    for (int64_t i = 4; i < vocab_size; i++) {
        scores.emplace_back(static_cast<int>(i), logits[i]);
    }

    std::sort(scores.begin(), scores.end(),
              [](const auto& a, const auto& b) { return a.second > b.second; });

    scores.resize(std::min(static_cast<size_t>(top_k), scores.size()));

    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(scores.size() * 2, string_class, nullptr);

    for (size_t i = 0; i < scores.size(); i++) {
        char idx_str[32];
        snprintf(idx_str, sizeof(idx_str), "%d", scores[i].first);
        jstring j_idx = env->NewStringUTF(idx_str);
        env->SetObjectArrayElement(result, i * 2, j_idx);

        char score_str[32];
        snprintf(score_str, sizeof(score_str), "%f", scores[i].second);
        jstring j_score = env->NewStringUTF(score_str);
        env->SetObjectArrayElement(result, i * 2 + 1, j_score);
    }

    api->ReleaseValue(output_tensor);

    LOGD("Predicted %zu candidates", scores.size());
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_kingzcheung_kime_plugin_prediction_association_association_NativeOnnxEngine_nativeRelease(
    JNIEnv* env, jobject thiz) {

    const OrtApi* api = GetApi();

    if (g_session) {
        api->ReleaseSession(g_session);
        g_session = nullptr;
        LOGD("Session released");
    }

    if (g_env) {
        api->ReleaseEnv(g_env);
        g_env = nullptr;
        LOGD("Env released");
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_kime_plugin_prediction_association_association_NativeOnnxEngine_nativeIsInitialized(
    JNIEnv* env, jobject thiz) {
    return g_session ? JNI_TRUE : JNI_FALSE;
}