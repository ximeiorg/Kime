// t9_patch_utils 单元测试
//
// 覆盖个人词库（translator/packs）补丁的纯判定逻辑：
//   - SanitizePackName：确定性 pack 名派生（杜绝 user_"" 畸形）
//   - EvaluatePacksState：合法保留 / 畸形修复 / 缺失补充
//   - StripPacksLines：只剔除畸形行，保留其他补丁（t9 四要素等）
#include <gtest/gtest.h>

#include "t9_patch_utils.h"

using rime::t9_patch_utils::EvaluatePacksState;
using rime::t9_patch_utils::HasPreeditLuaFilter;
using rime::t9_patch_utils::PacksState;
using rime::t9_patch_utils::SanitizePackName;
using rime::t9_patch_utils::BuildFastTranslatorPatchLines;
using rime::t9_patch_utils::StripPacksLines;

// ── SanitizePackName ──

TEST(T9PatchUtilsTest, Sanitize_Keeps_Alnum_Underscore) {
  EXPECT_EQ(SanitizePackName("t9_pinyin"), "t9_pinyin");
  EXPECT_EQ(SanitizePackName("t9"), "t9");
}

TEST(T9PatchUtilsTest, Sanitize_Strips_Illegal_Chars) {
  EXPECT_EQ(SanitizePackName("rime_ice.t9"), "rime_icet9");
  EXPECT_EQ(SanitizePackName("a-b/c"), "abc");
}

TEST(T9PatchUtilsTest, Sanitize_Empty_When_All_Illegal) {
  EXPECT_EQ(SanitizePackName("\"\"'"), "");
  EXPECT_EQ(SanitizePackName(""), "");
}

// ── EvaluatePacksState ──

TEST(T9PatchUtilsTest, Evaluate_Keep_Valid_Pack) {
  const std::string content =
      "patch:\n"
      "  \"engine/processors/@before 0\": t9_processor\n"
      "  \"translator/packs\": [\"user_pinyin_simp\"]\n";
  EXPECT_EQ(EvaluatePacksState(content), PacksState::kKeep);
}

TEST(T9PatchUtilsTest, Evaluate_Keep_Unquoted_Pack) {
  const std::string content =
      "patch:\n"
      "  \"translator/packs\": [user_t9]\n";
  EXPECT_EQ(EvaluatePacksState(content), PacksState::kKeep);
}

TEST(T9PatchUtilsTest, Evaluate_Repair_Quoted_Empty_Value) {
  // 旧跨块正则捕获 custom_phrase.dictionary: "" → packName = user_"" → 畸形行
  const std::string content =
      "patch:\n"
      "  \"translator/packs\": [\"user_\"\"\"]\n";
  EXPECT_EQ(EvaluatePacksState(content), PacksState::kRepair);
}

TEST(T9PatchUtilsTest, Evaluate_Repair_Empty_List) {
  const std::string content =
      "patch:\n"
      "  \"translator/packs\": []\n";
  EXPECT_EQ(EvaluatePacksState(content), PacksState::kRepair);
}

TEST(T9PatchUtilsTest, Evaluate_Repair_Illegal_Name) {
  const std::string content =
      "patch:\n"
      "  \"translator/packs\": [\"user_\"\"]\n";
  EXPECT_EQ(EvaluatePacksState(content), PacksState::kRepair);
}

TEST(T9PatchUtilsTest, Evaluate_Missing_No_Packs_Line) {
  const std::string content =
      "patch:\n"
      "  \"engine/filters/@before 0\": t9_filter\n";
  EXPECT_EQ(EvaluatePacksState(content), PacksState::kMissing);
}

TEST(T9PatchUtilsTest, Evaluate_Missing_Empty_Content) {
  EXPECT_EQ(EvaluatePacksState(""), PacksState::kMissing);
}

// ── StripPacksLines ──

TEST(T9PatchUtilsTest, Strip_Removes_Only_Packs_Lines) {
  const std::string content =
      "patch:\n"
      "  \"engine/processors/@before 0\": t9_processor\n"
      "  \"translator/packs\": [\"user_\"\"\"]\n"
      "  \"t9/isDisplayOriginalPreedit\": false\n";
  const std::string stripped = StripPacksLines(content);
  EXPECT_EQ(stripped.find("translator/packs"), std::string::npos);
  EXPECT_NE(stripped.find("t9_processor"), std::string::npos);
  EXPECT_NE(stripped.find("isDisplayOriginalPreedit"), std::string::npos);
}

TEST(T9PatchUtilsTest, Strip_No_Packs_Line_Keeps_Content) {
  const std::string content =
      "patch:\n"
      "  \"t9/isDisplayOriginalPreedit\": false\n";
  const std::string stripped = StripPacksLines(content);
  EXPECT_NE(stripped.find("t9/isDisplayOriginalPreedit"), std::string::npos);
  EXPECT_EQ(stripped.find("translator/packs"), std::string::npos);
}

// ── HasPreeditLuaFilter（isDisplayOriginalPreedit 默认值启发式）──

TEST(T9PatchUtilsTest, PreeditFilter_Detects_Wanxiang_Schema) {
  // 带声调九键方案：filters 段含 lua_filter@*super_comment_preedit 引用
  const std::string content =
      "engine:\n"
      "  filters:\n"
      "    - lua_filter@*wanxiang.super_lookup\n"
      "    - lua_filter@*wanxiang.super_comment_preedit\n"
      "    - lua_filter@*wanxiang.super_filter\n"
      "    - uniquifier\n";
  EXPECT_TRUE(HasPreeditLuaFilter(content));
}

TEST(T9PatchUtilsTest, PreeditFilter_Detects_CustomYaml_Patch) {
  // 用户经 custom.yaml 追加的 lua preedit filter（补丁管线亦需识别）
  const std::string content =
      "patch:\n"
      "  \"engine/filters/+prepend\": [lua_filter@*wanxiang.super_comment_preedit]\n"
      "  \"t9/isDisplayOriginalPreedit\": true\n";
  EXPECT_TRUE(HasPreeditLuaFilter(content));
}

TEST(T9PatchUtilsTest, PreeditFilter_Ignores_PreeditFormat_Only) {
  // 仅含 preedit_format（翻译器层格式器，非 lua preedit filter）→ 不命中
  const std::string content =
      "translator:\n"
      "  preedit_format:\n"
      "    - xlit/ABCDEFGHIJKLMNOPQRSTUVWXYZ/abcdefghijklmnopqrstuvwxyz/\n";
  EXPECT_FALSE(HasPreeditLuaFilter(content));
}

TEST(T9PatchUtilsTest, PreeditFilter_Ignores_Comment_Mention) {
  // 注释提及 preedit（无 lua_filter@）→ 不命中（旧全表子串扫描会误判）
  const std::string content =
      "engine:\n"
      "  filters:\n"
      "    # 注释说明：此方案由 t9_filter 管理 preedit\n"
      "    - t9_filter\n";
  EXPECT_FALSE(HasPreeditLuaFilter(content));
}

TEST(T9PatchUtilsTest, PreeditFilter_Ignores_LuaTranslator) {
  // lua_translator 不是 filter（不参与候选过滤），名称含 preedit 亦不命中
  const std::string content =
      "engine:\n"
      "  translators:\n"
      "    - lua_translator@*wanxiang.preedit_demo\n";
  EXPECT_FALSE(HasPreeditLuaFilter(content));
}

TEST(T9PatchUtilsTest, PreeditFilter_Ignores_Doc_Example_In_Comment) {
  // 行首注释中的 lua_filter@ 示例（文档）不参与判定
  const std::string content =
      "# 用法示例：lua_filter@*my.preedit_filter\n"
      "engine:\n"
      "  filters:\n"
      "    - uniquifier\n";
  EXPECT_FALSE(HasPreeditLuaFilter(content));
}

TEST(T9PatchUtilsTest, PreeditFilter_Empty_Content) {
  EXPECT_FALSE(HasPreeditLuaFilter(""));
}

// ── BuildFastTranslatorPatchLines（v3 插入式）──

TEST(T9PatchUtilsTest, FastTranslator_InsertionTwoLines) {
  const std::string schema =
      "engine:\n"
      "  translators:\n"
      "    - punct_translator  #注释\n"
      "    - script_translator\n";
  const auto lines = BuildFastTranslatorPatchLines(schema);
  ASSERT_EQ(lines.size(), 2u);
  EXPECT_EQ(lines[0],
            "  \"engine/translators/@after 1\": \"t9_script_translator@translator\"");
  EXPECT_EQ(lines[1], "  \"translator/tag\": \"_t9_fast_only_\"");
}

TEST(T9PatchUtilsTest, FastTranslator_SkipWhenCustomTags) {
  // schema 显式自定义 translator/tags：哨兵会被追加的 abc 绕过 → 保守不干预
  const std::string schema =
      "translator:\n"
      "  dictionary: wanxiang\n"
      "  tags:\n"
      "    - abc\n"
      "    - custom_tag\n";
  EXPECT_TRUE(BuildFastTranslatorPatchLines(schema).empty());
}

TEST(T9PatchUtilsTest, FastTranslator_PlainSchemaNoTags) {
  const std::string schema = "schema_id: t9_x\nengine:\n  translators:\n    - script_translator\n";
  EXPECT_EQ(BuildFastTranslatorPatchLines(schema).size(), 2u);
}
