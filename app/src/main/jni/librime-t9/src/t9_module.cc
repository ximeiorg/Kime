#include <rime/common.h>
#include <rime/registry.h>
#include <rime_api.h>
#include <vector>
#include "t9_processor.h"
#include "t9_filter.h"
#include "t9_date_translator.h"
#include "t9_user_translator.h"
#include "t9_script_translator.h"

using namespace rime;

namespace {

static void rime_t9_initialize() {
    LOG(INFO) << "registering components from module 't9'.";
    Registry& r = Registry::instance();
    r.Register("t9_processor", new Component<T9Processor>);
    r.Register("t9_filter", new Component<T9Filter>);
    r.Register("t9_date_translator", new Component<T9DateTranslator>);
    // 九键数字序列用户词召回（按实际输入数字序列召回用户词，
    // 解决声母简拼组词无法被 pinyin 用户词典召回的问题）。
    r.Register("t9_user_translator", new Component<T9UserTranslator>);
    // T9 快速词组翻译器：wordgraph 跨键增量缓存，由 T9 patch 管线以
    // 插入式两条 patch 接入（@after 1 + 哨兵 tag），不覆盖 translators 列表。
    r.Register("t9_script_translator", new Component<T9ScriptTranslator>);
}

static void rime_t9_finalize() {
}

}  // namespace

// P7（2026-07-19）：T9 方案 schema 注入 patch 定义。
//
// 组件注册名与注入配置名在此单一地点维护，
// Kotlin 端通过 JNI 查询，不再硬编码。
// 格式："search_pattern|patch_key|patch_value"
std::vector<std::string> rime::GetT9SchemaPatches() {
    return {
        "t9_processor|engine/processors/@before 0|t9_processor",
        "t9_filter|engine/filters/@before 0|t9_filter",
        "t9_date_translator|engine/translators/@before 0|t9_date_translator",
        "isDisplayOriginalPreedit|t9/isDisplayOriginalPreedit|false",
    };
}

RIME_REGISTER_MODULE(t9)
