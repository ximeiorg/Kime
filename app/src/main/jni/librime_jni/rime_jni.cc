// Xime Rime JNI 接口
// 基于 trime 的实现

#include <rime_api.h>
#include <rime/setup.h>
#include <rime/dict/reverse_lookup_dictionary.h>
#include "t9_processor.h"
#include "t9_patch_utils.h"
#include "t9_digit_userdict.h"
#include <jni.h>
#include <android/log.h>
#include <memory>
#include <string>
#include <vector>
#include <unistd.h>  // for usleep
#include <cstring>   // for strcmp
#include <utility>   // for std::pair
#include <ctime>     // for time

#define LOG_TAG "XimeRime"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 打字/输入高频路径的 LOGI/LOGD 写 logcat 会产生大量格式化+IPC 开销，
// 是前台耗电偏高的主因。
// 两级控制：
//   1) 编译期：RIME_JNI_VERBOSE_LOGGING 由 CMake 按构建类型定义
//      （Debug=1, Release=0）。Release 下宏展开为空语句，零开销。
//   2) 运行时：Debug 构建保留运行时开关 g_rime_jni_verbose_logging，
//      Kotlin 可通过 nativeSetVerboseLogging 手动切换，开发时不用重编。
#ifndef RIME_JNI_VERBOSE_LOGGING
#define RIME_JNI_VERBOSE_LOGGING 0
#endif
#if RIME_JNI_VERBOSE_LOGGING == 1
static volatile bool g_rime_jni_verbose_logging = true;
#define LOGI(...) do { if (g_rime_jni_verbose_logging) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__); } while (0)
#define LOGD(...) do { if (g_rime_jni_verbose_logging) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__); } while (0)
#else
#define LOGI(...) ((void)0)
#define LOGD(...) ((void)0)
#endif

extern void rime_require_module_lua();
extern void rime_require_module_octagram();
extern void rime_require_module_predict();
extern void rime_require_module_t9();

static void declare_librime_module_dependencies() {
  rime_require_module_lua();
  rime_require_module_octagram();
  rime_require_module_predict();
  rime_require_module_t9();
}

struct ProcessResult {
    bool processed = false;
    std::string committedText;
    std::string inputText;
    std::string preeditText;
    std::vector<std::pair<std::string, std::string>> candidates;
    bool isAsciiMode = false;
    bool hasNextPage = false;
    bool hasPrevPage = false;
    // T9 快照（仅 T9 会话活跃时填充）：左侧面板状态 + 首音节候选（"pinyin|len,..."）。
    // 合并进 getProcessResult 一次返回，避免 Kotlin 侧重复 JNI 取数（每键 5→3 次）。
    std::string t9PanelState;
    std::string t9SyllableOptions;
};

struct CompositionResult {
    std::string input;
    std::string preedit;
    std::string committedText;
    std::vector<std::pair<std::string, std::string>> candidates;
    bool isAsciiMode = false;
    bool hasNextPage = false;
    bool hasPrevPage = false;
};

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

        declare_librime_module_dependencies();

        user_data_dir_ = user_data_dir;
        shared_data_dir_ = shared_data_dir;

        std::string log_dir = std::string(user_data_dir) + "/logs";
        
        RIME_STRUCT(RimeTraits, traits);
        traits.shared_data_dir = shared_data_dir;
        traits.user_data_dir = user_data_dir;
        traits.log_dir = log_dir.c_str();
        traits.min_log_level = 1;
        traits.app_name = "rime.kime";
        traits.distribution_name = "Xime";
        traits.distribution_code_name = "kime";
        traits.distribution_version = "1.0.0";

        LOGI("Setting up Rime with shared_data_dir=%s, user_data_dir=%s, log_dir=%s", 
             shared_data_dir, user_data_dir, log_dir.c_str());
        
        rime->setup(&traits);
        LOGI("Rime setup completed");
        
        rime->initialize(&traits);
        LOGI("Rime initialize completed");
        initialized_ = true;
        
        // NOTE: start_maintenance 不再在 startup 中调用。
        // 从 Kotlin 侧根据词库文件是否已存在，按需调用 startMaintenance()。
        // 避免每次切换输入法时都触发 librime 的部署流程。
    }

    Bool startMaintenance(bool full) {
        if (!rime) {
            LOGE("startMaintenance: rime not available");
            return false;
        }
        LOGI("Starting maintenance (full=%s)...", full ? "true" : "false");
        Bool result = rime->start_maintenance(full);
        if (!result) {
            LOGE("startMaintenance FAILED: rime->start_maintenance() returned false");
        }
        return result;
    }

    bool createSession() {
        if (!rime) return false;
        session_id_ = rime->create_session();
        if (session_id_ != 0) {
            LOGI("Session created: %lu", (unsigned long)session_id_);
        } else {
            LOGD("Session creation failed (engine may be maintaining)");
        }
        return session_id_ != 0;
    }

    bool hasSession() {
        return session_id_ != 0;
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

    ProcessResult processKeyAndGetResult(int keycode, int mask) {
        ProcessResult result;
        result.processed = false;
        result.isAsciiMode = false;
        result.hasNextPage = false;
        result.hasPrevPage = false;

        if (!rime || !session_id_) {
            LOGE("processKeyAndGetResult: rime or session not available");
            return result;
        }

        result.processed = rime->process_key(session_id_, keycode, mask);
        readCurrentState(result);
        return result;
    }

    ProcessResult readResult(bool processed) {
        ProcessResult result;
        result.processed = processed;
        result.isAsciiMode = false;
        result.hasNextPage = false;
        result.hasPrevPage = false;
        if (!rime || !session_id_) {
            LOGE("readResult: rime or session not available");
            return result;
        }
        readCurrentState(result);
        return result;
    }

    void readCurrentState(ProcessResult& result) {
        RIME_STRUCT(RimeCommit, commit);
        if (rime->get_commit(session_id_, &commit)) {
            result.committedText = commit.text ? commit.text : "";
            rime->free_commit(&commit);
        }

    RIME_STRUCT(RimeContext, context);
    if (rime->get_context(session_id_, &context)) {
        const char* input = rime->get_input(session_id_);
        result.inputText = input ? input : "";
        result.preeditText = context.composition.preedit ?
            context.composition.preedit : "";
        // 汇总一条（逐候选日志已删：每键 20 条 logcat 写入与字符串创建
        // 在快速输入热路径上非必要）
        LOGI("readCurrentState: input='%s' num_candidates=%d",
             result.inputText.c_str(), context.menu.num_candidates);
        if (context.menu.num_candidates > 0) {
            for (int i = 0; i < context.menu.num_candidates; ++i) {
                const char* text = context.menu.candidates[i].text;
                const char* comment = context.menu.candidates[i].comment;
                result.candidates.push_back(std::make_pair(
                    text ? text : "",
                    comment ? comment : ""
                ));
            }
        }
        result.hasNextPage = !context.menu.is_last_page;
        result.hasPrevPage = context.menu.page_no > 0;
        rime->free_context(&context);
    } else {
        const char* input = rime->get_input(session_id_);
        result.inputText = input ? input : "";
        result.preeditText = "";
    }

        RIME_STRUCT(RimeStatus, status);
        if (rime->get_status(session_id_, &status)) {
            result.isAsciiMode = status.is_ascii_mode;
            rime->free_status(&status);
        }

        // ── T9 快照（一次 JNI 返回左栏状态 + 首音节候选）──
        // 仅在 T9 处理器活跃时填充；非 T9 场景保持空字符串，Kotlin 侧用默认值兜底。
        rime::T9Processor* t9proc = rime::T9ProcessorRequire();
        if (t9proc) {
            result.t9PanelState = t9proc->GetLeftPanelState();
            // 从面板状态解析当前数字段（第 5 段，已内含分词键锁定/unassigned/选择回退），
            // 为空时回退到 input 中的纯数字（无分隔符的常规输入）。
            std::string panelDigits;
            {
                const std::string& s = result.t9PanelState;
                size_t p = 0;
                for (int i = 0; i < 4 && p != std::string::npos; ++i) {
                    p = s.find(';', p + 1);
                }
                if (p != std::string::npos) {
                    size_t end = s.find(';', p + 1);
                    panelDigits = s.substr(p + 1, end - p - 1);
                }
            }
            if (panelDigits.empty()) {
                for (char c : result.inputText) {
                    if (c >= '0' && c <= '9') panelDigits += c;
                }
            }
            std::vector<std::string> options;
            t9proc->GetFirstSyllableOptions(panelDigits, 20, options);
            std::string joined;
            for (size_t i = 0; i < options.size(); ++i) {
                if (i > 0) joined += ",";
                joined += options[i];
            }
            result.t9SyllableOptions = joined;
        }
    }

    bool setInput(const char* input) {
        if (!rime || !session_id_) {
            LOGE("setInput: rime or session not available");
            return false;
        }
        if (!input || strlen(input) == 0) {
            LOGD("setInput: empty input, clearing composition");
            rime->clear_composition(session_id_);
            return true;
        }
        LOGD("setInput: '%s'", input);
        return rime->set_input(session_id_, input);
    }

    CompositionResult getComposition() {
        CompositionResult result;
        if (!rime || !session_id_) {
            LOGE("getComposition: rime or session not available");
            return result;
        }

        // 1. raw input
        const char* input = rime->get_input(session_id_);
        result.input = input ? input : "";

        // 2. context: preedit + candidates + pagination
        RIME_STRUCT(RimeContext, context);
        if (rime->get_context(session_id_, &context)) {
            if (context.composition.preedit) {
                result.preedit = context.composition.preedit;
            }
            LOGI("getComposition: input='%s' num_candidates=%d", result.input.c_str(), context.menu.num_candidates);
            for (int i = 0; i < context.menu.num_candidates; ++i) {
                const char* text = context.menu.candidates[i].text;
                const char* comment = context.menu.candidates[i].comment;
                result.candidates.push_back(std::make_pair(
                    text ? text : "",
                    comment ? comment : ""
                ));
            }
            result.hasNextPage = !context.menu.is_last_page;
            result.hasPrevPage = context.menu.page_no > 0;
            rime->free_context(&context);
        }

        // 3. commit text（统一返回，避免调用方额外查询）
        RIME_STRUCT(RimeCommit, commit);
        if (rime->get_commit(session_id_, &commit)) {
            result.committedText = commit.text ? commit.text : "";
            rime->free_commit(&commit);
        }

        // 4. status: ascii mode
        RIME_STRUCT(RimeStatus, status);
        if (rime->get_status(session_id_, &status)) {
            result.isAsciiMode = status.is_ascii_mode;
            rime->free_status(&status);
        }

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

    void getCandidatesWithComments(std::vector<std::pair<std::string, std::string>>& candidates) {
        if (!rime || !session_id_) return;
        
        RIME_STRUCT(RimeContext, context);
        if (rime->get_context(session_id_, &context)) {
            LOGD("getCandidatesWithComments: num_candidates=%d", context.menu.num_candidates);
            if (context.menu.num_candidates > 0) {
                for (int i = 0; i < context.menu.num_candidates; ++i) {
                    const char* text = context.menu.candidates[i].text;
                    const char* comment = context.menu.candidates[i].comment;
                    candidates.push_back(std::make_pair(
                        text ? text : "",
                        comment ? comment : ""
                    ));
                }
            }
            rime->free_context(&context);
        }
    }

    bool selectCandidate(int index) {
        if (!rime || !session_id_) return false;
        return rime->select_candidate_on_current_page(session_id_, index);
    }
    
    bool pageDown() {
        if (!rime || !session_id_) return false;
        return rime->process_key(session_id_, 0xFF56, 0);
    }
    
    bool pageUp() {
        if (!rime || !session_id_) return false;
        return rime->process_key(session_id_, 0xFF55, 0);
    }
    
    bool hasNextPage() {
        if (!rime || !session_id_) return false;
        RIME_STRUCT(RimeContext, context);
        if (rime->get_context(session_id_, &context)) {
            bool result = context.menu.page_no < context.menu.page_no + 1;
            rime->free_context(&context);
            return result;
        }
        return false;
    }
    
    bool hasPrevPage() {
        if (!rime || !session_id_) return false;
        RIME_STRUCT(RimeContext, context);
        if (rime->get_context(session_id_, &context)) {
            bool result = context.menu.page_no > 0;
            rime->free_context(&context);
            return result;
        }
        return false;
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
        
        // 直接切换方案，不验证方案是否存在（get_schema_list 不读 default.custom.yaml 的 patch）
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
            LOGD("No schemas available yet (deployment may still be running)");
        }
    }
    
    // 查找词汇的编码
    // 使用 reverse_lookup_dictionary API 反查字符编码
    bool lookupText(const char* text, std::string& outCode) {
        if (!rime || !text) return false;
        LOGD("lookupText: word='%s'", text);
        
        auto* component = rime::ReverseLookupDictionary::Require("reverse_lookup_dictionary");
        if (!component) { LOGD("lookupText: component not available"); return false; }
        auto* rldc = dynamic_cast<rime::ReverseLookupDictionaryComponent*>(component);
        if (!rldc) { LOGD("lookupText: not ReverseLookupDictionaryComponent"); return false; }
        
        // 先查当前 schema 使用的字典
        if (session_id_) {
            char schema_id[256] = {0};
            if (rime->get_current_schema(session_id_, schema_id, sizeof(schema_id))) {
                RimeConfig config = {0};
                if (rime->schema_open(schema_id, &config)) {
                    const char* dict = rime->config_get_cstring(&config, "translator/dictionary");
                    if (dict) {
                        LOGD("lookupText: schema '%s' uses dict '%s'", schema_id, dict);
                        auto d = rldc->Create(dict);
                        if (d && d->Load()) {
                            std::string r;
                            if (d->ReverseLookup(text, &r)) { outCode = r; delete d; rime->config_close(&config); return true; }
                        }
                        delete d;
                    }
                    // 也查 reverse_lookup 字典
                    const char* rev = rime->config_get_cstring(&config, "reverse_lookup/dictionary");
                    if (rev) {
                        LOGD("lookupText: schema '%s' reverse_lookup dict '%s'", schema_id, rev);
                        auto d = rldc->Create(rev);
                        if (d && d->Load()) {
                            std::string r;
                            if (d->ReverseLookup(text, &r)) { outCode = r; delete d; rime->config_close(&config); return true; }
                        }
                        delete d;
                    }
                    rime->config_close(&config);
                }
            }
        }
        
        // fallback: 依次尝试已知编码字典
        const char* fallbacks[] = {"wubi86", "pinyin_simp", nullptr};
        for (int i = 0; fallbacks[i]; i++) {
            auto d = rldc->Create(fallbacks[i]);
            if (!d) continue;
            if (d->Load()) {
                std::string r;
                if (d->ReverseLookup(text, &r)) { outCode = r; delete d; return true; }
            }
            delete d;
        }
        
        return false;
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
        
        // 删除 installation.yaml 以强制触发完整编译
        // librime 的 RimeStartMaintenance 中 installation_update 任务在
        // 检测到 installation.yaml 已存在且版本匹配时会返回 false，
        // 导致不调度任何编译任务直接返回
        std::string install_yaml(user_data_dir_ + "/installation.yaml");
        if (access(install_yaml.c_str(), F_OK) == 0) {
            LOGI("Removing existing installation.yaml to force full deployment");
            remove(install_yaml.c_str());
        }
        
        rime->start_maintenance(true);
        
        // 等待部署完成（不设超时，大词库编译可能很久）
        int wait_count = 0;
        while (rime->is_maintenance_mode()) {
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
    
    bool deploySchema(const char* schemaId) {
        if (!rime) {
            LOGE("deploySchema: rime not available");
            return false;
        }
        
        // 确保部署模块已加载（schema_update 等任务注册在 levers 模块中）
        rime::LoadModules(rime::kDeployerModules);
        
        // 构造 .schema.yaml 文件名
        std::string schemaFile(schemaId);
        if (schemaFile.find(".schema.yaml") == std::string::npos) {
            schemaFile += ".schema.yaml";
        }
        
        // 在 user_data_dir 和 shared_data_dir 中查找 schema 文件
        std::string schemaPath;
        std::string userPath = user_data_dir_ + "/" + schemaFile;
        std::string sharedPath = shared_data_dir_ + "/" + schemaFile;
        if (access(userPath.c_str(), F_OK) == 0) {
            schemaPath = userPath;
        } else if (access(sharedPath.c_str(), F_OK) == 0) {
            schemaPath = sharedPath;
        } else {
            LOGE("deploySchema: schema file not found at %s or %s",
                 userPath.c_str(), sharedPath.c_str());
            return false;
        }
        
        LOGI("Deploying single schema: %s", schemaPath.c_str());
        
        // 先销毁旧session
        if (session_id_) {
            rime->destroy_session(session_id_);
            session_id_ = 0;
        }
        
        Bool result = rime->deploy_schema(schemaPath.c_str());
        if (!result) {
            LOGE("deploy_schema failed for: %s", schemaPath.c_str());
            // 回退：启动完整维护等待完成
            rime->start_maintenance(true);
            while (rime->is_maintenance_mode()) {
                usleep(100000);
            }
        }
        
        // 重新创建session
        session_id_ = rime->create_session();
        LOGI("Deploy schema completed: %s", schemaId);
        return true;
    }

    void updateLastBuildTime() {
        if (!rime) return;
        RimeConfig config;
        if (rime->config_open("user", &config)) {
            int now = (int)(time(nullptr));
            rime->config_set_int(&config, "var/last_build_time", now);
            LOGI("Updated last_build_time to %d", now);
            rime->config_close(&config);
        }
    }

    void setPageSize(const char* schema_id, int page_size) {
        if (!rime) {
            LOGE("setPageSize: rime not available");
            return;
        }
        // schema_open 直接打开方案的配置对象，修改内存中的 menu/page_size
        RimeConfig config;
        if (rime->schema_open(schema_id, &config)) {
            rime->config_set_int(&config, "menu/page_size", page_size);
            rime->config_close(&config);
            LOGI("Set schema '%s' menu/page_size=%d via schema_open", schema_id, page_size);
        } else {
            LOGE("setPageSize: schema_open failed for '%s'", schema_id);
        }
    }

    void setOption(const char* option, Bool value) {
        if (!rime || !session_id_) {
            LOGE("setOption: rime or session not available");
            return;
        }
        rime->set_option(session_id_, option, value);
        LOGI("setOption: %s = %s", option, value ? "true" : "false");
    }

    Bool getOption(const char* option) {
        if (!rime || !session_id_) {
            LOGE("getOption: rime or session not available");
            return false;
        }
        Bool result = rime->get_option(session_id_, option);
        LOGD("getOption: %s = %s", option, result ? "true" : "false");
        return result;
    }

    void destroy() {
        if (rime) {
            if (session_id_) {
                rime->destroy_session(session_id_);
                session_id_ = 0;
            }
            rime->finalize();
        }
        initialized_ = false;
    }

    // 读取方案配置中的字符串项（schema + custom.yaml patch 合并后的最终值）
    std::string getSchemaString(const char* schema_id, const char* key) {
        if (!rime || !initialized_) {
            LOGE("getSchemaString: rime not initialized");
            return "";
        }
        RimeConfig config;
        if (!rime->schema_open(schema_id, &config)) {
            return "";
        }
        std::string value;
        const char* str = rime->config_get_cstring(&config, key);
        if (str) {
            value = str;
        }
        rime->config_close(&config);
        return value;
    }

    // 读取方案配置中的列表项（schema + custom.yaml patch 合并后的最终值）
    std::vector<std::string> getSchemaList(const char* schema_id, const char* key) {
        std::vector<std::string> items;
        if (!rime || !initialized_) {
            LOGE("getSchemaList: rime not initialized");
            return items;
        }
        RimeConfig config;
        if (!rime->schema_open(schema_id, &config)) {
            return items;
        }
        RimeConfigIterator iter;
        if (rime->config_begin_list(&iter, &config, key)) {
            while (rime->config_next(&iter)) {
                const char* value = rime->config_get_cstring(&config, iter.path);
                if (value) {
                    items.emplace_back(value);
                }
            }
            rime->config_end(&iter);
        }
        rime->config_close(&config);
        return items;
    }

    // 读取 user.yaml 用户状态字符串（user_config 组件 auto_save=true）
    std::string getUserConfigString(const char* key) {
        if (!rime || !initialized_) {
            LOGE("getUserConfigString: rime not initialized");
            return "";
        }
        RimeConfig config;
        if (!rime->user_config_open("user", &config)) {
            return "";
        }
        std::string value;
        const char* str = rime->config_get_cstring(&config, key);
        if (str) {
            value = str;
        }
        rime->config_close(&config);
        return value;
    }

    // 读取 user.yaml 用户状态布尔值
    bool getUserConfigBool(const char* key) {
        if (!rime || !initialized_) return false;
        RimeConfig config;
        if (!rime->user_config_open("user", &config)) return false;
        Bool value = False;
        rime->config_get_bool(&config, key, &value);
        rime->config_close(&config);
        return value == True;
    }

    // 写 user.yaml 用户状态字符串（user_config 组件 auto_save=true）
    bool setUserConfigString(const char* key, const char* value) {
        if (!rime || !initialized_) {
            LOGE("setUserConfigString: rime not initialized");
            return false;
        }
        RimeConfig config;
        if (!rime->user_config_open("user", &config)) return false;
        bool result = rime->config_set_string(&config, key, value);
        rime->config_close(&config);
        return result;
    }

    // 写 user.yaml 用户状态布尔值
    bool setUserConfigBool(const char* key, bool value) {
        if (!rime || !initialized_) return false;
        RimeConfig config;
        if (!rime->user_config_open("user", &config)) return false;
        bool result = rime->config_set_bool(&config, key, value ? True : False);
        rime->config_close(&config);
        return result;
    }

    // 数据目录访问器（供 T9 schema 补丁注入等 JNI 逻辑读取 schema/custom.yaml）
    const std::string& get_user_data_dir() const { return user_data_dir_; }
    const std::string& get_shared_data_dir() const { return shared_data_dir_; }

private:
    RimeApi* rime;
    RimeSessionId session_id_ = 0;
    std::string user_data_dir_;
    std::string shared_data_dir_;
    bool initialized_ = false;
};

extern "C" {

static jclass gRimeProcessResultClass = nullptr;
static jmethodID gRimeProcessResultCtor = nullptr;
static jclass gRimeCompositionClass = nullptr;
static jmethodID gRimeCompositionCtor = nullptr;
static jclass gRimeCandidateClass = nullptr;
static jmethodID gRimeCandidateCtor = nullptr;

static void ensureJniCache(JNIEnv* env) {
    if (!gRimeCandidateClass) {
        jclass cls = env->FindClass("com/kingzcheung/xime/rime/RimeCandidate");
        gRimeCandidateClass = (jclass)env->NewGlobalRef(cls);
        gRimeCandidateCtor = env->GetMethodID(gRimeCandidateClass, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;)V");
        env->DeleteLocalRef(cls);
    }
    if (!gRimeProcessResultClass) {
        jclass cls = env->FindClass("com/kingzcheung/xime/rime/RimeProcessResult");
        gRimeProcessResultClass = (jclass)env->NewGlobalRef(cls);
        gRimeProcessResultCtor = env->GetMethodID(gRimeProcessResultClass, "<init>",
            "(ZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;[Lcom/kingzcheung/xime/rime/RimeCandidate;ZZZLjava/lang/String;Ljava/lang/String;)V");
        env->DeleteLocalRef(cls);
    }
    if (!gRimeCompositionClass) {
        jclass cls = env->FindClass("com/kingzcheung/xime/rime/RimeComposition");
        gRimeCompositionClass = (jclass)env->NewGlobalRef(cls);
        gRimeCompositionCtor = env->GetMethodID(gRimeCompositionClass, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Lcom/kingzcheung/xime/rime/RimeCandidate;ZZZ)V");
        env->DeleteLocalRef(cls);
    }
}

// 运行时切换 verbose 日志（仅 Debug 构建生效，Release 下为空操作）
JNIEXPORT void JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeSetVerboseLogging(
    JNIEnv* env,
    jobject thiz,
    jboolean enabled
) {
#if RIME_JNI_VERBOSE_LOGGING == 1
    g_rime_jni_verbose_logging = enabled;
#else
    (void)env;
    (void)thiz;
    (void)enabled;
#endif
}

// 初始化 Rime 引擎
JNIEXPORT void JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeInitialize(
    JNIEnv* env,
    jobject thiz,
    jstring user_data_dir,
    jstring shared_data_dir
) {
    const char* user_dir = env->GetStringUTFChars(user_data_dir, nullptr);
    const char* shared_dir = env->GetStringUTFChars(shared_data_dir, nullptr);
    
    LOGI("Initializing Rime engine with user_dir=%s, shared_dir=%s", user_dir, shared_dir);
    Rime::Instance().startup(user_dir, shared_dir);

    // 初始化 T9 数字序列用户词典持久化路径（全局唯一，不依赖 schema 部署）。
    // SetFilePath 内部 loaded_ 标志防重复 Open。
    {
        static bool t9_db_initialized = false;
        if (!t9_db_initialized) {
            rime::T9DigitUserDict::SetFilePath(
                std::string(user_dir) + "/t9_digit.userdb");
            t9_db_initialized = true;
        }
    }

    env->ReleaseStringUTFChars(user_data_dir, user_dir);
    env->ReleaseStringUTFChars(shared_data_dir, shared_dir);
}

// 创建会话（startup 只初始化引擎，session 延迟创建）
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeCreateSession(
    JNIEnv* env,
    jobject thiz
) {
    return Rime::Instance().createSession() ? JNI_TRUE : JNI_FALSE;
}

// 检查会话是否存在
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeHasSession(
    JNIEnv* env,
    jobject thiz
) {
    return Rime::Instance().hasSession() ? JNI_TRUE : JNI_FALSE;
}

// 检查是否正在维护
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeIsMaintaining(
    JNIEnv* env,
    jobject thiz
) {
    return Rime::Instance().isMaintaining() ? JNI_TRUE : JNI_FALSE;
}

// 获取当前方案
JNIEXPORT jstring JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeGetCurrentSchema(
    JNIEnv* env,
    jobject thiz
) {
    std::string schema = Rime::Instance().getCurrentSchema();
    return env->NewStringUTF(schema.c_str());
}

// 处理按键输入
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeProcessKey(
    JNIEnv* env,
    jobject thiz,
    jint keycode,
    jint mask
) {
    return Rime::Instance().processKey(keycode, mask) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobject JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeProcessKeyAndGetResult(
    JNIEnv* env,
    jobject thiz,
    jint keycode,
    jint mask
) {
    ensureJniCache(env);

    ProcessResult result = Rime::Instance().processKeyAndGetResult(keycode, mask);

    jobjectArray candidateArray = env->NewObjectArray(
        result.candidates.size(), gRimeCandidateClass, nullptr);

    for (size_t i = 0; i < result.candidates.size(); ++i) {
        jstring text = env->NewStringUTF(result.candidates[i].first.c_str());
        jstring comment = env->NewStringUTF(result.candidates[i].second.c_str());
        jobject candidate = env->NewObject(gRimeCandidateClass, gRimeCandidateCtor, text, comment);
        env->SetObjectArrayElement(candidateArray, i, candidate);
        env->DeleteLocalRef(text);
        env->DeleteLocalRef(comment);
        env->DeleteLocalRef(candidate);
    }

    jstring jCommitted = env->NewStringUTF(result.committedText.c_str());
    jstring jInput = env->NewStringUTF(result.inputText.c_str());
    jstring jPreedit = env->NewStringUTF(result.preeditText.c_str());
    jstring jT9Panel = env->NewStringUTF(result.t9PanelState.c_str());
    jstring jT9Options = env->NewStringUTF(result.t9SyllableOptions.c_str());

    jobject jResult = env->NewObject(gRimeProcessResultClass, gRimeProcessResultCtor,
        result.processed ? JNI_TRUE : JNI_FALSE,
        jCommitted,
        jInput,
        jPreedit,
        candidateArray,
        result.isAsciiMode ? JNI_TRUE : JNI_FALSE,
        result.hasNextPage ? JNI_TRUE : JNI_FALSE,
        result.hasPrevPage ? JNI_TRUE : JNI_FALSE,
        jT9Panel,
        jT9Options);

    env->DeleteLocalRef(jCommitted);
    env->DeleteLocalRef(jInput);
    env->DeleteLocalRef(jPreedit);
    env->DeleteLocalRef(jT9Panel);
    env->DeleteLocalRef(jT9Options);
    env->DeleteLocalRef(candidateArray);

    return jResult;
}

JNIEXPORT jobject JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeGetProcessResult(
    JNIEnv* env,
    jobject thiz,
    jboolean processed
) {
    ensureJniCache(env);

    ProcessResult result = Rime::Instance().readResult(processed);

    jobjectArray candidateArray = env->NewObjectArray(
        result.candidates.size(), gRimeCandidateClass, nullptr);

    for (size_t i = 0; i < result.candidates.size(); ++i) {
        jstring text = env->NewStringUTF(result.candidates[i].first.c_str());
        jstring comment = env->NewStringUTF(result.candidates[i].second.c_str());
        jobject candidate = env->NewObject(gRimeCandidateClass, gRimeCandidateCtor, text, comment);
        env->SetObjectArrayElement(candidateArray, i, candidate);
        env->DeleteLocalRef(text);
        env->DeleteLocalRef(comment);
        env->DeleteLocalRef(candidate);
    }

    jstring jCommitted = env->NewStringUTF(result.committedText.c_str());
    jstring jInput = env->NewStringUTF(result.inputText.c_str());
    jstring jPreedit = env->NewStringUTF(result.preeditText.c_str());
    jstring jT9Panel = env->NewStringUTF(result.t9PanelState.c_str());
    jstring jT9Options = env->NewStringUTF(result.t9SyllableOptions.c_str());

    jobject jResult = env->NewObject(gRimeProcessResultClass, gRimeProcessResultCtor,
        result.processed ? JNI_TRUE : JNI_FALSE,
        jCommitted,
        jInput,
        jPreedit,
        candidateArray,
        result.isAsciiMode ? JNI_TRUE : JNI_FALSE,
        result.hasNextPage ? JNI_TRUE : JNI_FALSE,
        result.hasPrevPage ? JNI_TRUE : JNI_FALSE,
        jT9Panel,
        jT9Options);

    env->DeleteLocalRef(jCommitted);
    env->DeleteLocalRef(jInput);
    env->DeleteLocalRef(jPreedit);
    env->DeleteLocalRef(jT9Panel);
    env->DeleteLocalRef(jT9Options);
    env->DeleteLocalRef(candidateArray);

    return jResult;
}

// 设置输入字符串（替代逐字符 processKey，减少 JNI 调用次数）
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeSetInput(
    JNIEnv* env,
    jobject thiz,
    jstring input
) {
    const char* input_str = env->GetStringUTFChars(input, nullptr);
    if (!input_str) {
        LOGE("nativeSetInput: null input");
        return JNI_FALSE;
    }
    bool result = Rime::Instance().setInput(input_str);
    env->ReleaseStringUTFChars(input, input_str);
    return result ? JNI_TRUE : JNI_FALSE;
}

// 一次性获取当前 composition 全部信息：input/preedit/commit/candidates/paging/ascii_mode
// 将 updateUI 所需的多次 JNI 查询合并为一次，减少 JNI 往返开销。
JNIEXPORT jobject JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeGetComposition(
    JNIEnv* env,
    jobject thiz
) {
    ensureJniCache(env);

    CompositionResult result = Rime::Instance().getComposition();

    jobjectArray candidateArray = env->NewObjectArray(
        result.candidates.size(), gRimeCandidateClass, nullptr);

    for (size_t i = 0; i < result.candidates.size(); ++i) {
        jstring text = env->NewStringUTF(result.candidates[i].first.c_str());
        jstring comment = env->NewStringUTF(result.candidates[i].second.c_str());
        jobject candidate = env->NewObject(gRimeCandidateClass, gRimeCandidateCtor, text, comment);
        env->SetObjectArrayElement(candidateArray, i, candidate);
        env->DeleteLocalRef(text);
        env->DeleteLocalRef(comment);
        env->DeleteLocalRef(candidate);
    }

    jstring jInput = env->NewStringUTF(result.input.c_str());
    jstring jPreedit = env->NewStringUTF(result.preedit.c_str());
    jstring jCommitted = env->NewStringUTF(result.committedText.c_str());

    jobject jComposition = env->NewObject(gRimeCompositionClass, gRimeCompositionCtor,
        jInput,
        jPreedit,
        jCommitted,
        candidateArray,
        result.hasNextPage ? JNI_TRUE : JNI_FALSE,
        result.hasPrevPage ? JNI_TRUE : JNI_FALSE,
        result.isAsciiMode ? JNI_TRUE : JNI_FALSE);

    env->DeleteLocalRef(jInput);
    env->DeleteLocalRef(jPreedit);
    env->DeleteLocalRef(jCommitted);
    env->DeleteLocalRef(candidateArray);

    return jComposition;
}

// 获取候选词列表
JNIEXPORT jobjectArray JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeGetCandidates(
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

// 获取候选词列表（包含编码注释）
JNIEXPORT jobjectArray JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeGetCandidatesWithComments(
    JNIEnv* env,
    jobject thiz
) {
    std::vector<std::pair<std::string, std::string>> candidates;
    Rime::Instance().getCandidatesWithComments(candidates);
    
    jclass stringClass = env->FindClass("java/lang/String");
    jclass stringArrayClass = env->FindClass("[Ljava/lang/String;");
    
    jobjectArray result = env->NewObjectArray(candidates.size(), stringArrayClass, nullptr);
    
    for (size_t i = 0; i < candidates.size(); ++i) {
        jobjectArray pair = env->NewObjectArray(2, stringClass, nullptr);
        jstring text = env->NewStringUTF(candidates[i].first.c_str());
        jstring comment = env->NewStringUTF(candidates[i].second.c_str());
        env->SetObjectArrayElement(pair, 0, text);
        env->SetObjectArrayElement(pair, 1, comment);
        env->SetObjectArrayElement(result, i, pair);
        env->DeleteLocalRef(text);
        env->DeleteLocalRef(comment);
        env->DeleteLocalRef(pair);
    }
    
    return result;
}

// 获取输入文本
JNIEXPORT jstring JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeGetInput(
    JNIEnv* env,
    jobject thiz
) {
    return env->NewStringUTF(Rime::Instance().getInput());
}

// 选择候选词
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeSelectCandidate(
    JNIEnv* env,
    jobject thiz,
    jint index
) {
    return Rime::Instance().selectCandidate(index) ? JNI_TRUE : JNI_FALSE;
}

// 翻页 - 下一页
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativePageDown(
    JNIEnv* env,
    jobject thiz
) {
    return Rime::Instance().pageDown() ? JNI_TRUE : JNI_FALSE;
}

// 翻页 - 上一页
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativePageUp(
    JNIEnv* env,
    jobject thiz
) {
    return Rime::Instance().pageUp() ? JNI_TRUE : JNI_FALSE;
}

// 是否有下一页
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeHasNextPage(
    JNIEnv* env,
    jobject thiz
) {
    return Rime::Instance().hasNextPage() ? JNI_TRUE : JNI_FALSE;
}

// 是否有上一页
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeHasPrevPage(
    JNIEnv* env,
    jobject thiz
) {
    return Rime::Instance().hasPrevPage() ? JNI_TRUE : JNI_FALSE;
}

// 提交文本
JNIEXPORT jstring JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeCommit(
    JNIEnv* env,
    jobject thiz
) {
    std::string text = Rime::Instance().commit();
    return env->NewStringUTF(text.c_str());
}

// 清除组合
JNIEXPORT void JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeClearComposition(
    JNIEnv* env,
    jobject thiz
) {
    Rime::Instance().clearComposition();
}

// 切换中英文模式（ascii_mode）
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeToggleAsciiMode(
    JNIEnv* env,
    jobject thiz
) {
    return Rime::Instance().toggleAsciiMode() ? JNI_TRUE : JNI_FALSE;
}

// 获取当前是否为英文模式
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeIsAsciiMode(
    JNIEnv* env,
    jobject thiz
) {
    return Rime::Instance().isAsciiMode() ? JNI_TRUE : JNI_FALSE;
}

// 切换输入方案
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeSwitchSchema(
    JNIEnv* env,
    jobject thiz,
    jstring schema_id
) {
    const char* schema = env->GetStringUTFChars(schema_id, nullptr);
    bool result = Rime::Instance().switchSchema(schema);
    env->ReleaseStringUTFChars(schema_id, schema);
    return result ? JNI_TRUE : JNI_FALSE;
}

// 确保个人词库源文件存在（pack_name 形如 user_t9 → user_t9.dict.yaml）。
// 与 Kotlin PersonalDictManager.DEFAULT_HEADER 的 dict 头保持一致，
// 供 librime 编译 translator/packs 引用的用户词典。
static void EnsureT9PackDictFile(const std::string& user_data_dir,
                                 const std::string& pack_name) {
    if (pack_name.empty()) return;
    std::string path = user_data_dir + "/" + pack_name + ".dict.yaml";
    FILE* f = fopen(path.c_str(), "r");
    if (f) {
        fclose(f);
        return;
    }
    f = fopen(path.c_str(), "w");
    if (!f) {
        LOGE("T9Patches: failed to create pack dict file '%s'", path.c_str());
        return;
    }
    const std::string header =
        "# Rime dict\n"
        "---\n"
        "name: " + pack_name + "\n"
        "version: '1.0'\n"
        "sort: original\n"
        "use_preset_vocabulary: false\n"
        "...\n";
    fwrite(header.c_str(), 1, header.size(), f);
    fclose(f);
    LOGI("T9Patches: created pack dict file '%s'", path.c_str());
}

// 判断个人词库是否尚未编译（build/<pack_name>.table.bin 缺失）。
// 用于「补丁写入后词库未编译 → 需要部署」的幂等判定：
// 编译完成后 table.bin 存在，后续不再触发部署。
static bool T9PackTableBinMissing(const std::string& user_data_dir,
                                  const std::string& pack_name) {
    if (pack_name.empty()) return false;
    std::string path = user_data_dir + "/build/" + pack_name + ".table.bin";
    FILE* f = fopen(path.c_str(), "r");
    if (f) {
        fclose(f);
        return false;
    }
    return true;
}

// 读取文件全文到 out（文件不存在或读取失败返回 false）。
static bool ReadFileToString(const std::string& path, std::string& out) {
    FILE* f = fopen(path.c_str(), "r");
    if (!f) return false;
    char buf[8192];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) {
        out.append(buf, n);
    }
    fclose(f);
    return true;
}

// 从文本中剔除含任一 needle 的行。
static std::string StripLinesContaining(const std::string& text,
                                        const std::vector<std::string>& needles) {
    std::string result;
    size_t start = 0;
    while (start < text.size()) {
        size_t nl = text.find('\n', start);
        if (nl == std::string::npos) nl = text.size();
        std::string line = text.substr(start, nl - start);
        bool drop = false;
        for (const auto& needle : needles) {
            if (line.find(needle) != std::string::npos) {
                drop = true;
                break;
            }
        }
        if (!drop) {
            result += line;
            result += '\n';
        }
        if (nl == text.size()) break;
        start = nl + 1;
    }
    return result;
}

// 确保 T9 方案 schema 补丁已注入到 custom.yaml（幂等）。
//   - 判定 T9 方案：schema_id 含 "t9"，或 schema.yaml 的 schema_id 字段含 "t9"。
//   - 补齐缺失组件：t9_processor / t9_filter / t9_date_translator /
//     t9/isDisplayOriginalPreedit / t9/enable_date_translator（schema 已有则尊重）。
//   - 修复畸形 translator/packs（个人词库），合法补丁尊重、缺失交由 PersonalDictManager。
// 返回 true 表示写入后词库/补丁尚未编译，调用方可据此触发部署。
static jboolean DoEnsureT9SchemaPatches(
    JNIEnv* env,
    const char* schema,
    const std::string& user_data_dir,
    const std::string& shared_data_dir) {
    // 读 schema.yaml（先 user_data_dir 后 shared_data_dir，兼容 .schema.yaml/.yaml）。
    std::string schema_content;
    for (const auto& ext : {".schema.yaml", ".yaml"}) {
        if (ReadFileToString(user_data_dir + "/" + schema + ext, schema_content) ||
            ReadFileToString(shared_data_dir + "/" + schema + ext, schema_content)) {
            break;
        }
        schema_content.clear();
    }

    // 判定 T9 方案：JNI schema_id 含 "t9"，或 schema.yaml 的 schema_id 字段含 "t9"。
    bool is_t9_schema = false;
    std::string sid(schema);
    for (auto& c : sid) c = static_cast<char>(tolower(c));
    if (sid.find("t9") != std::string::npos) {
        is_t9_schema = true;
    }
    if (!is_t9_schema && !schema_content.empty()) {
        auto pos = schema_content.find("schema_id:");
        if (pos != std::string::npos) {
            auto line_end = schema_content.find('\n', pos);
            auto line = schema_content.substr(
                pos, line_end == std::string::npos ? line_end : line_end - pos);
            for (auto& c : line) c = static_cast<char>(tolower(c));
            if (line.find("t9") != std::string::npos) {
                is_t9_schema = true;
            }
        }
    }
    if (!is_t9_schema) {
        return JNI_FALSE;
    }
    // 未找到 schema 文件 → 方案可能已卸载，不写补丁。
    if (schema_content.empty()) {
        LOGI("T9Patches: schema file for '%s' not found on disk, skip", schema);
        return JNI_FALSE;
    }

    // 读 custom.yaml 已有补丁。
    std::string custom_path = user_data_dir + "/" + schema + ".custom.yaml";
    std::string existing_content;
    ReadFileToString(custom_path, existing_content);

    // 收集需要追加的补丁行（幂等：schema 或 custom 已含该组件则跳过）。
    std::vector<std::string> patch_lines;

    // 方案自带 preedit 管理型 lua filter（如带声调方案的 super_comment_preedit，
    // HasPreeditLuaFilter 判定）→ 默认 isDisplayOriginalPreedit: true（透传，
    // 让方案 lua 管理 preedit）；否则（t9_pinyin 无声调方案）默认 false。
    // schema/custom 显式声明的 isDisplayOriginalPreedit 始终优先（下方补丁跳过）。
    const bool has_schema_preedit_filter =
        rime::t9_patch_utils::HasPreeditLuaFilter(schema_content) ||
        rime::t9_patch_utils::HasPreeditLuaFilter(existing_content);
    if (schema_content.find("t9_processor") == std::string::npos &&
        existing_content.find("t9_processor") == std::string::npos) {
        patch_lines.push_back("  \"engine/processors/@before 0\": t9_processor");
    }
    if (schema_content.find("t9_filter") == std::string::npos &&
        existing_content.find("t9_filter") == std::string::npos) {
        patch_lines.push_back("  \"engine/filters/@before 0\": t9_filter");
    }
    if (schema_content.find("isDisplayOriginalPreedit") == std::string::npos &&
        existing_content.find("isDisplayOriginalPreedit") == std::string::npos) {
        // 透传 true：避免 t9_filter 转换与方案 lua 的 preedit 管理冲突；
        // 默认 false：t9_filter 管理 preedit（无声调方案）。
        const char* preedit_default = has_schema_preedit_filter ? "true" : "false";
        patch_lines.push_back(
            std::string("  \"t9/isDisplayOriginalPreedit\": ") + preedit_default);
    }
    // t9_user_translator 占据 translators 首位。
    // 旧版 t9_date_translator 注入在 @before 0，与新 user 冲突，迁移到 @after 0。
    const bool legacy_date_before0 =
        existing_content.find("\"engine/translators/@before 0\": t9_date_translator") != std::string::npos;
    if (schema_content.find("t9_user_translator") == std::string::npos &&
        existing_content.find("t9_user_translator") == std::string::npos) {
        patch_lines.push_back("  \"engine/translators/@before 0\": t9_user_translator");
    }
    if (schema_content.find("t9_date_translator") == std::string::npos &&
        (existing_content.find("t9_date_translator") == std::string::npos ||
         legacy_date_before0)) {
        patch_lines.push_back("  \"engine/translators/@after 0\": t9_date_translator");
    }
    if (schema_content.find("t9/enable_date_translator") == std::string::npos &&
        existing_content.find("t9/enable_date_translator") == std::string::npos) {
        patch_lines.push_back("  \"t9/enable_date_translator\": true");
    }

    // 拼音方案判定：含 script_translator 的 schema 才支持词组快通道注入。
    const bool is_pinyin_schema =
        schema_content.find("script_translator") != std::string::npos;

    // 清理已废弃的 enable_abbrev_recall 产物
    const bool has_derive = existing_content.find("derive/^([a-z])") != std::string::npos;
    const bool has_abbrev_custom = existing_content.find("enable_abbrev_recall") != std::string::npos;
    bool need_remove_derive = has_derive || has_abbrev_custom;

    // T9 拼音方案词组快通道：以两条单键 patch 接入 t9_script_translator
    //（@after 1 插入第三位——首位 t9_user_translator @before 0、第二位
    // t9_date_translator @after 0，同 key 会互相覆盖；@translator 复用原
    // script_translator 的词典配置 namespace），不覆盖 translators 列表
    //——用户对方案的 translators 补丁不受影响。
    // 哨兵 tag（_t9_fast_only_）禁用原 script_translator 防同段双计算
    //（空列表方式会自动回填 abc，故必须用单数 tag）；Query 固定处理 abc tag
    // 不受哨兵影响。
    // 幂等：custom 已含哨兵跳过；用户显式配置 translator/tag 或
    // t9/disable_fast_translator 时跳过。
    bool fast_translator_patch_changed = false;
    if (is_pinyin_schema &&
        existing_content.find(rime::t9_patch_utils::kT9FastOnlyTag) == std::string::npos &&
        existing_content.find("\"translator/tag\"") == std::string::npos &&
        existing_content.find("t9/disable_fast_translator") == std::string::npos) {
        for (const auto& line :
             rime::t9_patch_utils::BuildFastTranslatorPatchLines(schema_content)) {
            patch_lines.push_back(line);
            fast_translator_patch_changed = true;
        }
    }

    // translator/packs 个人词库：合法保留 / 畸形修复 / 缺失交由 PersonalDictManager。
    const std::string packs_name = "user_" + rime::t9_patch_utils::SanitizePackName(schema);
    bool need_packs_patch = false;
    std::string actual_pack_name;  // 实际生效的个人词库名（用于词库编译判定）
    if (!packs_name.empty()) {
        switch (rime::t9_patch_utils::EvaluatePacksState(existing_content, &actual_pack_name)) {
          case rime::t9_patch_utils::PacksState::kKeep:
          case rime::t9_patch_utils::PacksState::kMissing:
            break;
          case rime::t9_patch_utils::PacksState::kRepair:
            existing_content = rime::t9_patch_utils::StripPacksLines(existing_content);
            need_packs_patch = true;
            break;
        }
    }
    if (need_packs_patch) {
        patch_lines.push_back("  \"translator/packs\": [\"" + packs_name + "\"]");
    }

    // 无改动 → 仅判断词库是否仍需编译。
    if (patch_lines.empty() && !need_remove_derive) {
        const bool need_deploy = T9PackTableBinMissing(user_data_dir, actual_pack_name);
        return need_deploy ? JNI_TRUE : JNI_FALSE;
    }

    // 清理废弃的 enable_abbrev_recall 产物：移除 derive 简拼行和开关行。
    std::string base = existing_content;
    if (need_remove_derive) {
        base = StripLinesContaining(base, {"derive/^([a-z])", "derive/^([zcs]h)", "enable_abbrev_recall"});
    }
    // 迁移旧版 translators/@before 0 的 date 行（与 user 冲突）
    if (legacy_date_before0) {
        base = StripLinesContaining(base, {"engine/translators/@before 0"});
    }

    // 剥离末尾 "..." 与空白，使追加的 patch 被 RIME 正常解析。
    while (!base.empty() && (base.back() == '\n' || base.back() == '\r' || base.back() == ' ')) {
        base.pop_back();
    }
    if (base.size() >= 3 && base.substr(base.size() - 3) == "...") {
        base.resize(base.size() - 3);
        while (!base.empty() && (base.back() == '\n' || base.back() == '\r' || base.back() == ' ')) {
            base.pop_back();
        }
    }

    std::string patch_content;
    for (const auto& line : patch_lines) {
        patch_content += line;
        patch_content += '\n';
    }

    std::string new_content;
    if (base.empty()) {
        new_content = "patch:\n" + patch_content;
    } else if (base.find("\npatch:") != std::string::npos || base.find("patch:") == 0) {
        new_content = base;
        if (new_content.back() != '\n') new_content += '\n';
        new_content += patch_content;
    } else {
        new_content = base;
        if (new_content.back() != '\n') new_content += '\n';
        new_content += "\n\npatch:\n" + patch_content;
    }

    FILE* f = fopen(custom_path.c_str(), "w");
    if (!f) {
        LOGE("T9Patches: failed to write custom.yaml '%s'", custom_path.c_str());
        return JNI_FALSE;
    }
    fwrite(new_content.c_str(), 1, new_content.size(), f);
    fclose(f);

    // 写入合法 packs 补丁后，确保对应个人词库文件存在（否则 librime 编译时 pack 源缺失）。
    if (need_packs_patch) {
        EnsureT9PackDictFile(user_data_dir, packs_name);
        actual_pack_name = packs_name;
    }

    LOGI("T9Patches: applied '%s' (fast_translator=%d, packs=%s)", schema,
         fast_translator_patch_changed ? 1 : 0, actual_pack_name.c_str());

    // 返回 true 表示词库/补丁尚未编译，调用方可据此触发部署。
    const bool need_deploy =
        T9PackTableBinMissing(user_data_dir, actual_pack_name) ||
        fast_translator_patch_changed;
    return need_deploy ? JNI_TRUE : JNI_FALSE;
}

// 引擎已初始化路径：目录取自 Rime::Instance()
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeEnsureT9SchemaPatches(
    JNIEnv* env,
    jobject thiz,
    jstring schema_id
) {
    const char* schema = env->GetStringUTFChars(schema_id, nullptr);
    jboolean result = DoEnsureT9SchemaPatches(env, schema,
        Rime::Instance().get_user_data_dir(),
        Rime::Instance().get_shared_data_dir());
    env->ReleaseStringUTFChars(schema_id, schema);
    return result;
}

// 获取可用方案列表
JNIEXPORT jobjectArray JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeGetAvailableSchemas(
    JNIEnv* env,
    jobject thiz
) {
    std::vector<std::pair<std::string, std::string>> schemas;
    Rime::Instance().getAvailableSchemas(schemas);
    
    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass) return nullptr;
    
    jobjectArray result = env->NewObjectArray(schemas.size(), stringClass, nullptr);
    if (!result) return nullptr;
    
    for (size_t i = 0; i < schemas.size(); ++i) {
        jstring str = env->NewStringUTF(schemas[i].first.c_str());
        env->SetObjectArrayElement(result, i, str);
        env->DeleteLocalRef(str);
    }
    
    return result;
}

// 读取方案配置列表项（schema + custom.yaml patch 合并后的最终值）
JNIEXPORT jobjectArray JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeGetSchemaList(
    JNIEnv* env,
    jobject thiz,
    jstring schema_id,
    jstring key
) {
    const char* schema = env->GetStringUTFChars(schema_id, nullptr);
    if (!schema) return nullptr;
    const char* key_ptr = env->GetStringUTFChars(key, nullptr);
    if (!key_ptr) {
        env->ReleaseStringUTFChars(schema_id, schema);
        return nullptr;
    }
    std::vector<std::string> items = Rime::Instance().getSchemaList(schema, key_ptr);
    env->ReleaseStringUTFChars(schema_id, schema);
    env->ReleaseStringUTFChars(key, key_ptr);

    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass) return nullptr;

    jobjectArray result = env->NewObjectArray(items.size(), stringClass, nullptr);
    if (!result) return nullptr;

    for (size_t i = 0; i < items.size(); ++i) {
        jstring str = env->NewStringUTF(items[i].c_str());
        env->SetObjectArrayElement(result, i, str);
        env->DeleteLocalRef(str);
    }
    return result;
}

// 读取方案配置字符串项（schema + custom.yaml patch 合并后的最终值）
JNIEXPORT jstring JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeGetSchemaString(
    JNIEnv* env,
    jobject thiz,
    jstring schema_id,
    jstring key
) {
    const char* schema = env->GetStringUTFChars(schema_id, nullptr);
    if (!schema) return nullptr;
    const char* key_ptr = env->GetStringUTFChars(key, nullptr);
    if (!key_ptr) {
        env->ReleaseStringUTFChars(schema_id, schema);
        return nullptr;
    }
    std::string value = Rime::Instance().getSchemaString(schema, key_ptr);
    env->ReleaseStringUTFChars(schema_id, schema);
    env->ReleaseStringUTFChars(key, key_ptr);
    return value.empty() ? nullptr : env->NewStringUTF(value.c_str());
}

// 读取 user.yaml 用户状态字符串
JNIEXPORT jstring JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeGetUserConfigString(
    JNIEnv* env,
    jobject thiz,
    jstring key
) {
    const char* key_ptr = env->GetStringUTFChars(key, nullptr);
    if (!key_ptr) return nullptr;
    std::string value = Rime::Instance().getUserConfigString(key_ptr);
    env->ReleaseStringUTFChars(key, key_ptr);
    return value.empty() ? nullptr : env->NewStringUTF(value.c_str());
}

// 读取 user.yaml 用户状态布尔值
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeGetUserConfigBool(
    JNIEnv* env,
    jobject thiz,
    jstring key
) {
    const char* key_ptr = env->GetStringUTFChars(key, nullptr);
    if (!key_ptr) return JNI_FALSE;
    bool value = Rime::Instance().getUserConfigBool(key_ptr);
    env->ReleaseStringUTFChars(key, key_ptr);
    return value ? JNI_TRUE : JNI_FALSE;
}

// 写 user.yaml 用户状态字符串
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeSetUserConfigString(
    JNIEnv* env,
    jobject thiz,
    jstring key,
    jstring value
) {
    const char* key_ptr = env->GetStringUTFChars(key, nullptr);
    if (!key_ptr) return JNI_FALSE;
    const char* value_ptr = env->GetStringUTFChars(value, nullptr);
    if (!value_ptr) {
        env->ReleaseStringUTFChars(key, key_ptr);
        return JNI_FALSE;
    }
    bool result = Rime::Instance().setUserConfigString(key_ptr, value_ptr);
    env->ReleaseStringUTFChars(key, key_ptr);
    env->ReleaseStringUTFChars(value, value_ptr);
    return result ? JNI_TRUE : JNI_FALSE;
}

// 写 user.yaml 用户状态布尔值
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeSetUserConfigBool(
    JNIEnv* env,
    jobject thiz,
    jstring key,
    jboolean value
) {
    const char* key_ptr = env->GetStringUTFChars(key, nullptr);
    if (!key_ptr) return JNI_FALSE;
    bool result = Rime::Instance().setUserConfigBool(key_ptr, value == JNI_TRUE);
    env->ReleaseStringUTFChars(key, key_ptr);
    return result ? JNI_TRUE : JNI_FALSE;
}

// 设置 Rime 选项
JNIEXPORT void JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeSetOption(
    JNIEnv* env,
    jobject thiz,
    jstring option,
    jboolean value
) {
    const char* option_ptr = env->GetStringUTFChars(option, nullptr);
    if (!option_ptr) return;
    Rime::Instance().setOption(option_ptr, value == JNI_TRUE);
    env->ReleaseStringUTFChars(option, option_ptr);
}

// 读取 Rime 选项
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeGetOption(
    JNIEnv* env,
    jobject thiz,
    jstring option
) {
    const char* option_ptr = env->GetStringUTFChars(option, nullptr);
    if (!option_ptr) return JNI_FALSE;
    Bool result = Rime::Instance().getOption(option_ptr);
    env->ReleaseStringUTFChars(option, option_ptr);
    return result ? JNI_TRUE : JNI_FALSE;
}

// 销毁引擎
JNIEXPORT void JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeDestroy(
    JNIEnv* env,
    jobject thiz
) {
    LOGI("Destroying Rime engine");
    // Compact T9 数字词典（flush WAL 为 .ldb），确保进程退出时数据持久化。
    rime::T9DigitUserDict::CompactDb();
    Rime::Instance().destroy();
}

// 部署
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeDeploy(
    JNIEnv* env,
    jobject thiz
) {
    LOGI("Deploying Rime engine");
    return Rime::Instance().deploy() ? JNI_TRUE : JNI_FALSE;
}

// 启动维护（词库编译/刷新），返回是否成功启动部署
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeStartMaintenance(
    JNIEnv* env,
    jobject thiz,
    jboolean full
) {
    Bool result = Rime::Instance().startMaintenance(full == JNI_TRUE);
    return result ? JNI_TRUE : JNI_FALSE;
}

// 更新 last_build_time 为当前时间，避免下次增量检测误判
JNIEXPORT void JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeUpdateLastBuildTime(
    JNIEnv* env,
    jobject thiz
) {
    Rime::Instance().updateLastBuildTime();
}

// 部署单个方案
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeDeploySchema(
    JNIEnv* env,
    jobject thiz,
    jstring schema_id
) {
    const char* schema_id_ptr = env->GetStringUTFChars(schema_id, nullptr);
    if (!schema_id_ptr) return JNI_FALSE;
    
    LOGI("Deploying schema: %s", schema_id_ptr);
    
    // 构建 schema 文件路径
    Rime::Instance().deploySchema(schema_id_ptr);
    
    env->ReleaseStringUTFChars(schema_id, schema_id_ptr);
    return JNI_TRUE;
}

// 查询词汇编码
JNIEXPORT jstring JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeLookupText(
    JNIEnv* env,
    jobject thiz,
    jstring text
) {
    const char* text_ptr = env->GetStringUTFChars(text, nullptr);
    std::string code;
    bool found = Rime::Instance().lookupText(text_ptr, code);
    env->ReleaseStringUTFChars(text, text_ptr);
    
    if (found && !code.empty()) {
        return env->NewStringUTF(code.c_str());
    }
    return env->NewStringUTF("");
}

// 设置候选词每页数量
JNIEXPORT void JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeSetPageSize(
    JNIEnv* env,
    jobject thiz,
    jstring schema_id,
    jint page_size
) {
    const char* schema = env->GetStringUTFChars(schema_id, nullptr);
    if (!schema) return;
    Rime::Instance().setPageSize(schema, page_size);
    env->ReleaseStringUTFChars(schema_id, schema);
}

// 检查 Rime 模块是否已注册（用于测试插件集成）
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeIsModuleRegistered(
    JNIEnv* env,
    jobject thiz,
    jstring module_name
) {
    const char* name = env->GetStringUTFChars(module_name, nullptr);
    if (!name) return JNI_FALSE;
    
    bool found = false;
    RimeApi* api = rime_get_api();
    if (api && RIME_API_AVAILABLE(api, find_module)) {
        RimeModule* m = api->find_module(name);
        found = (m != nullptr);
    } else {
        RimeModule* m = RimeFindModule(name);
        found = (m != nullptr);
    }
    
    LOGI("Module check: '%s' -> %s", name, found ? "FOUND" : "NOT FOUND");
    env->ReleaseStringUTFChars(module_name, name);
    return found ? JNI_TRUE : JNI_FALSE;
  }

// ═══════════════════════════════════════════════════════════
// T9 Processor JNI 接口
// ═══════════════════════════════════════════════════════════

// 右选候选：根据候选拼音与文本长度执行右侧选词（消费计算）。
// 委托给 T9RightCommitHandler 三层消费算法（设计稿 §6.2）。
// 注：传入候选拼音注释（comment）、候选文本与候选词字数，而非索引，避免翻页后索引错位。
// 用户词典调频不在此处进行——由 Kotlin 在 full commit 上屏后经 nativeT9Memorize 单独调用。
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeT9SelectCandidate(
    JNIEnv* env,
    jobject thiz,
    jstring pinyin,
    jstring text,
    jint text_length
) {
    rime::T9Processor* proc = rime::T9ProcessorRequire();
    if (!proc) {
        LOGE("nativeT9SelectCandidate: no active T9Processor");
        return JNI_FALSE;
    }
    const char* p = env->GetStringUTFChars(pinyin, nullptr);
    if (!p) return JNI_FALSE;
    std::string candidate_text;
    if (text) {
        const char* t = env->GetStringUTFChars(text, nullptr);
        if (t) {
            candidate_text.assign(t);
            env->ReleaseStringUTFChars(text, t);
        }
    }
    bool result = proc->SelectCandidate(std::string(p), candidate_text, text_length);
    env->ReleaseStringUTFChars(pinyin, p);
    return result ? JNI_TRUE : JNI_FALSE;
}

// T9 用户词典写入/回滚公共实现（memorize=true → commits=+1；false → commits=-1）。
// 调频文本与拼音由 Kotlin 在 full commit 上屏后传入（Kotlin 上屏路径为唯一真相源）。
static jboolean T9DictUpdate(JNIEnv* env, jstring text, jstring pinyin, bool memorize) {
    rime::T9Processor* proc = rime::T9ProcessorRequire();
    if (!proc) {
        LOGE("T9DictUpdate: no active T9Processor");
        return JNI_FALSE;
    }
    const char* t = env->GetStringUTFChars(text, nullptr);
    if (!t) return JNI_FALSE;
    std::string p;
    if (pinyin) {
        const char* pp = env->GetStringUTFChars(pinyin, nullptr);
        if (pp) {
            p.assign(pp);
            env->ReleaseStringUTFChars(pinyin, pp);
        }
    }
    bool result = memorize ? proc->MemorizeEntry(std::string(t), p)
                           : proc->ForgetEntry(std::string(t), p);
    env->ReleaseStringUTFChars(text, t);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeT9Memorize(
    JNIEnv* env,
    jobject thiz,
    jstring text,
    jstring pinyin
) {
    return T9DictUpdate(env, text, pinyin, true);
}

JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeT9Forget(
    JNIEnv* env,
    jobject thiz,
    jstring text,
    jstring pinyin
) {
    return T9DictUpdate(env, text, pinyin, false);
}

// 直接选择拼音（替代 SelectSyllable 的候选索引方式）
JNIEXPORT jboolean JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeT9SelectPinyinDirect(
    JNIEnv* env,
    jobject thiz,
    jstring pinyin,
    jint digit_length
) {
    rime::T9Processor* proc = rime::T9ProcessorRequire();
    if (!proc) return JNI_FALSE;
    const char* p = env->GetStringUTFChars(pinyin, nullptr);
    if (!p) return JNI_FALSE;
    proc->SelectPinyinDirect(std::string(p), digit_length);
    env->ReleaseStringUTFChars(pinyin, p);
    return JNI_TRUE;
}

// 清空 T9Processor 全部状态（buffer + undo + state machine）+ RIME composition。
// mode=0: 仅清 RIME composition（保留 local state）
// mode=1: 清 composition + 重置 local state（clearAll 场景）
JNIEXPORT void JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeT9ClearComposition(
    JNIEnv* env,
    jobject thiz,
    jint mode
) {
    rime::T9Processor* proc = rime::T9ProcessorRequire();
    if (!proc) return;
    proc->ClearComposition(mode);
}

// 执行 T9Processor 累积的待发送引擎动作（set_input → compose / clear 等）。
// 异步 flush 模型：T9 处理器在 processKey 内只标记 pending（SendToRime），
// 真正触发引擎的调用延迟到此处执行，由应用层在 processKey 之后的
// 后台线程调用，避免引擎 compose 阻塞 UI 线程。
JNIEXPORT void JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeT9FlushRimeInput(
    JNIEnv* env,
    jobject thiz
) {
    rime::T9Processor* proc = rime::T9ProcessorRequire();
    if (!proc) return;
    proc->FlushRimeInput();
}

// 获取并消费 P1 撤销 RightCommit 的计数。
// 每次 P1 撤销自增 1，Kotlin 层每次查询后自减，避免重复消费。
JNIEXPORT jint JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeT9GetAndConsumeUndoneRightCommitCount(
    JNIEnv* env,
    jobject thiz
) {
    rime::T9Processor* proc = rime::T9ProcessorRequire();
    if (!proc) return 0;
    return proc->GetAndConsumeUndoneRightCommitCount();
}

// 获取 t9_processor 中的剩余数字（partial commit 后重新发送到 RIME）
JNIEXPORT jstring JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeT9GetRemainingDigits(
    JNIEnv* env,
    jobject thiz
) {
    rime::T9Processor* proc = rime::T9ProcessorRequire();
    if (!proc) return env->NewStringUTF("");
    std::string digits = proc->GetRemainingDigits();
    return env->NewStringUTF(digits.c_str());
}

// 获取左侧面板状态（格式：STATE;PINYIN;DIGIT_LEN;SEL_DIGITS;PANEL_DIGITS;LEFT_LOCKED）
JNIEXPORT jstring JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeT9GetLeftPanelState(
    JNIEnv* env,
    jobject thiz
) {
    rime::T9Processor* proc = rime::T9ProcessorRequire();
    if (!proc) return env->NewStringUTF("IDLE;;;;;0");
    std::string state = proc->GetLeftPanelState();
    return env->NewStringUTF(state.c_str());
}

// 获取首音节候选列表（P3 方案 A：替代 Kotlin T9PinyinMap.firstSyllableOptions）
// 返回格式 "pinyin|digitLength" 逗号分隔，如 "ji|2,li|2,j|1,k|1,l|1"
JNIEXPORT jstring JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeT9GetFirstSyllableOptions(
    JNIEnv* env,
    jobject thiz,
    jstring j_digits,
    jint j_max_results
) {
    const char* digits = env->GetStringUTFChars(j_digits, nullptr);
    if (!digits) return env->NewStringUTF("");

    rime::T9Processor* proc = rime::T9ProcessorRequire();
    if (!proc) {
        env->ReleaseStringUTFChars(j_digits, digits);
        return env->NewStringUTF("");
    }

    std::vector<std::string> options;
    proc->GetFirstSyllableOptions(digits, j_max_results, options);
    env->ReleaseStringUTFChars(j_digits, digits);

    // 序列化为 "pinyin|digitLength,pinyin|digitLength,..."
    std::string result;
    for (size_t i = 0; i < options.size(); i++) {
        if (i > 0) result += ",";
        result += options[i];
    }
    return env->NewStringUTF(result.c_str());
}

} // extern "C"