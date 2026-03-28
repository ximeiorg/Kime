// Kime Rime JNI 接口
// 基于 trime 的实现

#include <rime_api.h>
#include <jni.h>
#include <android/log.h>
#include <memory>
#include <string>
#include <vector>
#include <unistd.h>  // for usleep
#include <cstring>   // for strcmp
#include <utility>   // for std::pair

#define LOG_TAG "KimeRime"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Rime 单例类
class Rime {
public:
    Rime() : rime(rime_get_api()) {}
    Rime(Rime const&) = delete;
    void operator=(Rime const&) = delete;

    static Rime& Instance() {
        static Rime instance;
        return instance;
    }

    void startup(const char* user_data_dir, const char* shared_data_dir) {
        if (!rime) {
            LOGE("Rime API not available");
            return;
        }

        RIME_STRUCT(RimeTraits, traits);
        traits.shared_data_dir = shared_data_dir;
        traits.user_data_dir = user_data_dir;
        traits.log_dir = "";  // 禁用文件日志，只输出到 logcat
        traits.app_name = "rime.kime";
        traits.distribution_name = "Kime";
        traits.distribution_code_name = "kime";
        traits.distribution_version = "1.0.0";

        LOGI("Setting up Rime with shared_data_dir=%s, user_data_dir=%s", shared_data_dir, user_data_dir);
        
        // 第一步：setup
        rime->setup(&traits);
        LOGI("Rime setup completed");
        
        // 第二步：initialize
        rime->initialize(&traits);
        LOGI("Rime initialize completed");
        
        // 第三步：检查是否需要部署
        bool need_deploy = rime->is_maintenance_mode();
        LOGI("Need deploy: %s", need_deploy ? "true" : "false");
        
        if (need_deploy) {
            LOGI("Starting maintenance/deployment...");
            rime->start_maintenance(true);
            
            // 等待部署完成（最多等待30秒）
            int wait_count = 0;
            while (rime->is_maintenance_mode() && wait_count < 300) {
                usleep(100000);  // 100ms
                wait_count++;
                if (wait_count % 10 == 0) {
                    LOGI("Waiting for deployment... (%d seconds)", wait_count / 10);
                }
            }
            
            if (rime->is_maintenance_mode()) {
                LOGE("Deployment timeout!");
            } else {
                LOGI("Deployment completed successfully");
            }
        }
        
        // 第四步：创建会话
        session_id_ = rime->create_session();
        LOGI("Session created: %lu", (unsigned long)session_id_);
        
        // 检查是否成功加载了词典（尝试获取候选词）
        bool dict_loaded = false;
        if (session_id_) {
            // 尝试输入一个键来检查词典是否加载
            rime->process_key(session_id_, 'a', 0);
            RIME_STRUCT(RimeContext, context);
            if (rime->get_context(session_id_, &context)) {
                if (context.menu.num_candidates > 0) {
                    dict_loaded = true;
                    LOGI("Dictionary loaded successfully, candidates available");
                }
                rime->free_context(&context);
            }
            rime->clear_composition(session_id_);
        }
        
        // 如果词典没有加载，强制重新部署
        if (!dict_loaded) {
            LOGI("Dictionary not loaded, forcing redeployment...");
            
            // 销毁会话
            if (session_id_) {
                rime->destroy_session(session_id_);
                session_id_ = 0;
            }
            
            // 强制部署
            rime->start_maintenance(true);
            
            // 等待部署完成
            int wait_count = 0;
            while (rime->is_maintenance_mode() && wait_count < 300) {
                usleep(100000);
                wait_count++;
                if (wait_count % 10 == 0) {
                    LOGI("Forced deployment waiting... (%d seconds)", wait_count / 10);
                }
            }
            
            if (rime->is_maintenance_mode()) {
                LOGE("Forced deployment timeout!");
            } else {
                LOGI("Forced deployment completed");
            }
            
            // 重新创建会话
            session_id_ = rime->create_session();
            LOGI("New session created: %lu", (unsigned long)session_id_);
        }
        
        // 第五步：检查当前方案
        char schema[256];
        if (rime->get_current_schema(session_id_, schema, sizeof(schema))) {
            LOGI("Current schema: %s", schema);
        } else {
            LOGE("Failed to get current schema!");
        }
        
        // 第六步：检查方案列表
        RimeSchemaList schema_list = {0};
        if (rime->get_schema_list(&schema_list)) {
            LOGI("Available schemas: %zu", schema_list.size);
            for (size_t i = 0; i < schema_list.size; i++) {
                LOGI("  Schema %zu: %s (%s)", i, schema_list.list[i].schema_id, schema_list.list[i].name);
            }
            rime->free_schema_list(&schema_list);
        } else {
            LOGE("Failed to get schema list!");
        }
    }

    bool isMaintaining() {
        if (!rime) return false;
        // librime API 使用 is_maintenance_mode
        return rime->is_maintenance_mode();
    }

    std::string getCurrentSchema() {
        if (!rime || !session_id_) return "";
        
        // get_current_schema 需要 buffer 和 buffer_size 参数
        char buffer[256];
        if (rime->get_current_schema(session_id_, buffer, sizeof(buffer))) {
            return std::string(buffer);
        }
        return "";
    }

    bool processKey(int keycode, int mask) {
        if (!rime || !session_id_) {
            LOGE("processKey: rime or session not available");
            return false;
        }
        LOGD("processKey: keycode=%d, mask=%d", keycode, mask);
        bool result = rime->process_key(session_id_, keycode, mask);
        LOGD("processKey result: %d", result);
        return result;
    }

    const char* getInput() {
        if (!rime || !session_id_) return "";
        const char* input = rime->get_input(session_id_);
        LOGD("getInput: '%s'", input ? input : "(null)");
        return input ? input : "";
    }

    void getCandidates(std::vector<std::string>& candidates) {
        if (!rime || !session_id_) return;
        
        RIME_STRUCT(RimeContext, context);
        if (rime->get_context(session_id_, &context)) {
            LOGD("getCandidates: num_candidates=%d", context.menu.num_candidates);
            if (context.menu.num_candidates > 0) {
                for (int i = 0; i < context.menu.num_candidates; ++i) {
                    const char* text = context.menu.candidates[i].text;
                    LOGD("Candidate %d: '%s'", i, text ? text : "(null)");
                    candidates.push_back(text ? text : "");
                }
            }
            rime->free_context(&context);
        } else {
            LOGD("getCandidates: no context available");
        }
    }

    bool selectCandidate(int index) {
        if (!rime || !session_id_) return false;
        return rime->select_candidate_on_current_page(session_id_, index);
    }

    std::string commit() {
        std::string result;
        if (!rime || !session_id_) return result;
        
        RIME_STRUCT(RimeCommit, commit);
        if (rime->get_commit(session_id_, &commit)) {
            result = commit.text ? commit.text : "";
            LOGD("commit: '%s'", result.c_str());
            rime->free_commit(&commit);
        }
        return result;
    }

    void clearComposition() {
        if (!rime || !session_id_) return;
        rime->clear_composition(session_id_);
    }

    bool toggleAsciiMode() {
        if (!rime || !session_id_) {
            LOGE("toggleAsciiMode: rime or session not available");
            return false;
        }
        
        // 获取当前 ascii_mode 状态
        RIME_STRUCT(RimeStatus, status);
        if (!rime->get_status(session_id_, &status)) {
            LOGE("toggleAsciiMode: failed to get status");
            return false;
        }
        
        bool current_ascii_mode = status.is_ascii_mode;
        rime->free_status(&status);
        
        LOGI("toggleAsciiMode: current ascii_mode=%s", current_ascii_mode ? "true" : "false");
        
        // 切换状态
        bool new_ascii_mode = !current_ascii_mode;
        
        // 使用 set_option 来设置 ascii_mode
        rime->set_option(session_id_, "ascii_mode", new_ascii_mode);
        
        // 验证是否设置成功
        RIME_STRUCT(RimeStatus, new_status);
        if (rime->get_status(session_id_, &new_status)) {
            bool result = new_status.is_ascii_mode == new_ascii_mode;
            LOGI("toggleAsciiMode: new ascii_mode=%s, result=%s",
                 new_status.is_ascii_mode ? "true" : "false",
                 result ? "success" : "failed");
            rime->free_status(&new_status);
            return result;
        }
        
        return true;
    }

    bool isAsciiMode() {
        if (!rime || !session_id_) return false;
        
        RIME_STRUCT(RimeStatus, status);
        if (rime->get_status(session_id_, &status)) {
            bool result = status.is_ascii_mode;
            rime->free_status(&status);
            return result;
        }
        return false;
    }

    bool switchSchema(const char* schema_id) {
        if (!rime || !session_id_) {
            LOGE("switchSchema: rime or session not available");
            return false;
        }
        
        LOGI("switchSchema: switching to '%s'", schema_id);
        
        // 使用 set_schema_option 来切换方案
        // 首先获取当前方案列表确认方案存在
        RimeSchemaList schema_list = {0};
        bool schema_exists = false;
        if (rime->get_schema_list(&schema_list)) {
            for (size_t i = 0; i < schema_list.size; i++) {
                if (strcmp(schema_list.list[i].schema_id, schema_id) == 0) {
                    schema_exists = true;
                    LOGI("Found schema: %s (%s)", schema_list.list[i].schema_id, schema_list.list[i].name);
                    break;
                }
            }
            rime->free_schema_list(&schema_list);
        }
        
        if (!schema_exists) {
            LOGE("Schema '%s' not found in schema list", schema_id);
            return false;
        }
        
        // 使用 select_schema 来切换方案
        bool result = rime->select_schema(session_id_, schema_id);
        LOGI("select_schema result: %s", result ? "true" : "false");
        
        if (result) {
            // 验证切换是否成功
            char current_schema[256];
            if (rime->get_current_schema(session_id_, current_schema, sizeof(current_schema))) {
                LOGI("Current schema after switch: %s", current_schema);
                return strcmp(current_schema, schema_id) == 0;
            }
        }
        
        return result;
    }

    void getAvailableSchemas(std::vector<std::pair<std::string, std::string>>& schemas) {
        if (!rime) return;
        
        RimeSchemaList schema_list = {0};
        if (rime->get_schema_list(&schema_list)) {
            LOGI("Available schemas: %zu", schema_list.size);
            for (size_t i = 0; i < schema_list.size; i++) {
                std::string id = schema_list.list[i].schema_id ? schema_list.list[i].schema_id : "";
                std::string name = schema_list.list[i].name ? schema_list.list[i].name : "";
                schemas.push_back(std::make_pair(id, name));
                LOGI("  Schema %zu: %s (%s)", i, id.c_str(), name.c_str());
            }
            rime->free_schema_list(&schema_list);
        } else {
            LOGE("Failed to get schema list!");
        }
    }
    
    bool deploy() {
        if (!rime) {
            LOGE("deploy: rime not available");
            return false;
        }
        
        LOGI("Starting deployment...");
        
        // 先销毁旧session
        if (session_id_) {
            LOGI("Destroying old session before deployment");
            rime->destroy_session(session_id_);
            session_id_ = 0;
        }
        
        rime->start_maintenance(true);
        
        // 等待部署完成（最多等待30秒）
        int wait_count = 0;
        while (rime->is_maintenance_mode() && wait_count < 300) {
            usleep(100000);  // 100ms
            wait_count++;
            if (wait_count % 10 == 0) {
                LOGI("Waiting for deployment... (%d seconds)", wait_count / 10);
            }
        }
        
        if (rime->is_maintenance_mode()) {
            LOGE("Deployment timeout!");
            return false;
        }
        
        // 重新创建session
        LOGI("Creating new session after deployment");
        session_id_ = rime->create_session();
        if (!session_id_) {
            LOGE("Failed to create session after deployment");
            return false;
        }
        LOGI("New session created: %lu", (unsigned long)session_id_);
        
        LOGI("Deployment completed successfully");
        return true;
    }

    void destroy() {
        if (rime) {
            if (session_id_) {
                rime->destroy_session(session_id_);
                session_id_ = 0;
            }
            rime->finalize();
        }
    }

private:
    RimeApi* rime;
    RimeSessionId session_id_ = 0;
};

extern "C" {

// 初始化 Rime 引擎
JNIEXPORT void JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeInitialize(
    JNIEnv* env,
    jobject thiz,
    jstring user_data_dir,
    jstring shared_data_dir
) {
    const char* user_dir = env->GetStringUTFChars(user_data_dir, nullptr);
    const char* shared_dir = env->GetStringUTFChars(shared_data_dir, nullptr);
    
    LOGI("Initializing Rime engine with user_dir=%s, shared_dir=%s", user_dir, shared_dir);
    Rime::Instance().startup(user_dir, shared_dir);
    
    env->ReleaseStringUTFChars(user_data_dir, user_dir);
    env->ReleaseStringUTFChars(shared_data_dir, shared_dir);
}

// 检查是否正在维护
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeIsMaintaining(
    JNIEnv* env,
    jobject thiz
) {
    return Rime::Instance().isMaintaining() ? JNI_TRUE : JNI_FALSE;
}

// 获取当前方案
JNIEXPORT jstring JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeGetCurrentSchema(
    JNIEnv* env,
    jobject thiz
) {
    std::string schema = Rime::Instance().getCurrentSchema();
    return env->NewStringUTF(schema.c_str());
}

// 处理按键输入
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeProcessKey(
    JNIEnv* env,
    jobject thiz,
    jint keycode,
    jint mask
) {
    return Rime::Instance().processKey(keycode, mask) ? JNI_TRUE : JNI_FALSE;
}

// 获取候选词列表
JNIEXPORT jobjectArray JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeGetCandidates(
    JNIEnv* env,
    jobject thiz
) {
    std::vector<std::string> candidates;
    Rime::Instance().getCandidates(candidates);
    
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(candidates.size(), stringClass, nullptr);
    
    for (size_t i = 0; i < candidates.size(); ++i) {
        jstring str = env->NewStringUTF(candidates[i].c_str());
        env->SetObjectArrayElement(result, i, str);
        env->DeleteLocalRef(str);
    }
    
    return result;
}

// 获取输入文本
JNIEXPORT jstring JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeGetInput(
    JNIEnv* env,
    jobject thiz
) {
    return env->NewStringUTF(Rime::Instance().getInput());
}

// 选择候选词
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeSelectCandidate(
    JNIEnv* env,
    jobject thiz,
    jint index
) {
    return Rime::Instance().selectCandidate(index) ? JNI_TRUE : JNI_FALSE;
}

// 提交文本
JNIEXPORT jstring JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeCommit(
    JNIEnv* env,
    jobject thiz
) {
    std::string text = Rime::Instance().commit();
    return env->NewStringUTF(text.c_str());
}

// 清除组合
JNIEXPORT void JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeClearComposition(
    JNIEnv* env,
    jobject thiz
) {
    Rime::Instance().clearComposition();
}

// 切换中英文模式（ascii_mode）
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeToggleAsciiMode(
    JNIEnv* env,
    jobject thiz
) {
    return Rime::Instance().toggleAsciiMode() ? JNI_TRUE : JNI_FALSE;
}

// 获取当前是否为英文模式
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeIsAsciiMode(
    JNIEnv* env,
    jobject thiz
) {
    return Rime::Instance().isAsciiMode() ? JNI_TRUE : JNI_FALSE;
}

// 切换输入方案
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeSwitchSchema(
    JNIEnv* env,
    jobject thiz,
    jstring schema_id
) {
    const char* schema = env->GetStringUTFChars(schema_id, nullptr);
    bool result = Rime::Instance().switchSchema(schema);
    env->ReleaseStringUTFChars(schema_id, schema);
    return result ? JNI_TRUE : JNI_FALSE;
}

// 获取可用方案列表
JNIEXPORT jobjectArray JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeGetAvailableSchemas(
    JNIEnv* env,
    jobject thiz
) {
    std::vector<std::pair<std::string, std::string>> schemas;
    Rime::Instance().getAvailableSchemas(schemas);
    
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(schemas.size(), stringClass, nullptr);
    
    for (size_t i = 0; i < schemas.size(); ++i) {
        // 返回方案ID
        jstring str = env->NewStringUTF(schemas[i].first.c_str());
        env->SetObjectArrayElement(result, i, str);
        env->DeleteLocalRef(str);
    }
    
    return result;
}

// 销毁引擎
JNIEXPORT void JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeDestroy(
    JNIEnv* env,
    jobject thiz
) {
    LOGI("Destroying Rime engine");
    Rime::Instance().destroy();
}

// 部署
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_kime_rime_RimeEngine_nativeDeploy(
    JNIEnv* env,
    jobject thiz
) {
    LOGI("Deploying Rime engine");
    return Rime::Instance().deploy() ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"