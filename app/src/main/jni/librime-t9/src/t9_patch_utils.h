// T9 方案补丁工具函数集
//
// 设计目标：
//   1. 为 nativeEnsureT9SchemaPatches（rime_jni.cc）提供个人词库
//      （translator/packs）补丁的纯判定函数。
//   2. 保证 pack 名确定性派生（user_<schemaId>，仅保留 [A-Za-z0-9_]），
//      从根源杜绝 user_"" 类畸形（如第三方九键方案 custom_phrase.dictionary: ""
//      被旧跨块正则捕获后写入畸形 YAML）。
//   3. 纯算法（无 RIME 依赖），纳入 T9_ALGO_ONLY_BUILD 独立单测。
//
// 背景（2026-08）：维护者要求 PersonalDictManager 保持 main 原样（统一优化中），
// 九键方案 custom.yaml 的 packs 治理权收归 C++：合法补丁尊重、畸形补丁修复、
// 缺失补丁补充，全部收敛到 nativeEnsureT9SchemaPatches 既有四阶段管线。

#ifndef T9_PATCH_UTILS_H_
#define T9_PATCH_UTILS_H_

#include <optional>
#include <string>
#include <vector>

namespace rime {
namespace t9_patch_utils {

// 从 schemaId 派生确定性 pack 名：仅保留 [A-Za-z0-9_]。
// 全部字符非法时返回空串，调用方应跳过 packs 写入（防御）。
std::string SanitizePackName(const std::string& schema_id);

// custom.yaml 中 translator/packs 补丁的状态
enum class PacksState {
  kKeep,    // 存在且合法（^user_[A-Za-z0-9_]+$）→ 尊重已有配置，不干预
  kRepair,  // 存在但畸形（引号包裹空值 / user_"" / 非法字符）→ 需替换
  kMissing  // 无 → 需写入
};

// 判定 custom.yaml 中现有 translator/packs 行的状态。
// 兼容 PersonalDictManager 的写入格式：  "translator/packs": ["user_xxx"]。
// 当存在该行时（kKeep/kRepair），out_name（可空）会收到提取到的 pack 名，
// 供调用方定位对应的 .dict.yaml / .table.bin 产物。
PacksState EvaluatePacksState(const std::string& custom_yaml_content,
                              std::string* out_name = nullptr);

// 剔除 custom.yaml 中所有含 translator/packs 的行（kRepair 场景先清理畸形行）。
// 不触碰其他行（PersonalDictManager 写入的合法补丁与 t9 补丁均保留），
// 返回值末尾无多余的连续空行，供调用方继续追加合法补丁。
std::string StripPacksLines(const std::string& custom_yaml_content);

// 判定内容是否自带 preedit 管理型 lua filter（如带声调方案的 super_comment_preedit）：
// 任意 `lua_filter@` 引用名含 "preedit" 即命中。相较旧版全表 "preedit" 子串
// 扫描，不误伤仅含 preedit_format / 注释提及 preedit 的三方方案。
// 命中时 DoEnsureT9SchemaPatches 默认 t9/isDisplayOriginalPreedit: true。
bool HasPreeditLuaFilter(const std::string& content);

// 生成词组快通道 translators 补丁行（插入式）：
// 以两条单键 patch 接入 t9_script_translator（不覆盖 translators 列表，
// 不冻结方案快照——用户对方案的 translators 补丁不受影响）：
//   "engine/translators/@after 1": "t9_script_translator@translator"
//     ↑ 插入第二位（首位是 t9_user_translator @before 0、第二位
//       t9_date_translator @after 0，同 key 会互相覆盖）；@translator
//       复用原 script_translator 的词典配置 namespace
//   "translator/tag": "_t9_fast_only_"
//     ↑ 哨兵 tag 禁用原 script_translator（避免同段双计算；tags_ 读到哨兵
//       后不匹配 abc 段——空列表方式会自动回填 abc，故必须用单数 tag）
// t9_script_translator::Query 固定处理 abc tag，不受哨兵影响。
// 防御：schema 显式自定义 translator/tags（列表）时哨兵会被追加的 abc 绕过
// → 返回空（不干预，保守回退）。
// 返回 0 或 2 行（含缩进）；纯文本算法，纳入单测。
std::vector<std::string> BuildFastTranslatorPatchLines(const std::string& schema_yaml_content);

// 哨兵 tag 常量（与 t9_script_translator 接入约定一致）。
inline constexpr const char* kT9FastOnlyTag = "_t9_fast_only_";

}  // namespace t9_patch_utils
}  // namespace rime

#endif  // T9_PATCH_UTILS_H_
