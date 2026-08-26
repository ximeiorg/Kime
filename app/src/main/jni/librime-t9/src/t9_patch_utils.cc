#include "t9_patch_utils.h"

#include <algorithm>
#include <cctype>
#include <sstream>
#include <vector>

namespace rime {
namespace t9_patch_utils {

std::string SanitizePackName(const std::string& schema_id) {
  std::string result;
  result.reserve(schema_id.size());
  for (char c : schema_id) {
    if (std::isalnum(static_cast<unsigned char>(c)) || c == '_') {
      result += c;
    }
  }
  return result;
}

// 从一行文本中提取 translator/packs 的包名。
// 兼容两种形态：
//   "translator/packs": ["user_xxx"]      （含引号，PersonalDictManager / 本模块写入）
//   translator/packs: [user_xxx]          （无引号）
// 仅提取第一个 token；token 的边界由引号/空白/逗号/右括号界定。
// 返回空串表示「有该 key 但值为空」→ 视为畸形；无该 key 返回 nullopt。
static std::optional<std::string> ExtractPackToken(const std::string& line) {
  const size_t colon = line.find(':');
  if (colon == std::string::npos) return std::nullopt;

  const size_t lb = line.find('[', colon);
  const size_t rb = line.find(']', colon);
  if (lb != std::string::npos && rb != std::string::npos && rb > lb) {
    // [] 包裹形式：取第一个 token
    const std::string inner = line.substr(lb + 1, rb - lb - 1);
    const size_t s = inner.find_first_not_of(" \t\"'");
    if (s == std::string::npos) return std::string();  // 空列表 → 畸形
    const size_t e = inner.find_first_of(" \t\"',]", s);
    return inner.substr(s, e == std::string::npos ? std::string::npos : e - s);
  }

  // 无 [] 的裸值形式：translator/packs: user_xxx
  const std::string after = line.substr(colon + 1);
  const size_t s = after.find_first_not_of(" \t\"'");
  if (s == std::string::npos) return std::string();  // 空值 → 畸形
  const size_t e = after.find_first_of(" \t\"'", s);
  return after.substr(s, e == std::string::npos ? std::string::npos : e - s);
}

PacksState EvaluatePacksState(const std::string& custom_yaml_content,
                              std::string* out_name) {
  size_t pos = 0;
  while (pos < custom_yaml_content.size()) {
    const size_t eol = custom_yaml_content.find('\n', pos);
    const size_t end = eol == std::string::npos ? custom_yaml_content.size() : eol;
    std::string line = custom_yaml_content.substr(pos, end - pos);

    // 去行首空白，判定是否为 translator/packs 键行
    const size_t s = line.find_first_not_of(" \t\r");
    if (s != std::string::npos &&
        line.find("translator/packs") != std::string::npos) {
      auto token = ExtractPackToken(line.substr(s));
      if (!token.has_value()) return PacksState::kMissing;  // 键形式不符，视为缺失
      const std::string& name = *token;
      if (out_name) *out_name = name;
      // 合法：user_ 前缀 + 后缀仅 [A-Za-z0-9_]
      const bool valid =
          name.rfind("user_", 0) == 0 && name.size() > 5 &&
          std::all_of(name.begin() + 5, name.end(), [](char c) {
            return std::isalnum(static_cast<unsigned char>(c)) || c == '_';
          });
      return valid ? PacksState::kKeep : PacksState::kRepair;
    }
    if (eol == std::string::npos) break;
    pos = eol + 1;
  }
  return PacksState::kMissing;
}

std::string StripPacksLines(const std::string& custom_yaml_content) {
  std::string result;
  size_t pos = 0;
  while (pos < custom_yaml_content.size()) {
    const size_t eol = custom_yaml_content.find('\n', pos);
    const size_t end = eol == std::string::npos ? custom_yaml_content.size() : eol;
    std::string line = custom_yaml_content.substr(pos, end - pos);

    const size_t s = line.find_first_not_of(" \t\r");
    if (s == std::string::npos || line.find("translator/packs") == std::string::npos) {
      result += line;
      result += '\n';
    }
    if (eol == std::string::npos) break;
    pos = eol + 1;
  }
  return result;
}

bool HasPreeditLuaFilter(const std::string& content) {
  // 逐行扫描 `lua_filter@` 引用名，含 "preedit" 即命中。
  // 行首注释跳过（避免示例误判）；引用名用"包含"匹配（兼容
  // *super_comment_preedit 前缀写法）。
  std::istringstream iss(content);
  std::string line;
  while (std::getline(iss, line)) {
    if (!line.empty() && line.back() == '\r') line.pop_back();
    const size_t first = line.find_first_not_of(" \t");
    if (first == std::string::npos || line[first] == '#') continue;

    const size_t pos = line.find("lua_filter@");
    if (pos == std::string::npos) continue;
    size_t name_start = pos + std::string("lua_filter@").size();
    size_t name_end = name_start;
    while (name_end < line.size() && line[name_end] != ' ' && line[name_end] != '\t' &&
           line[name_end] != '#' && line[name_end] != ']' && line[name_end] != '"' &&
           line[name_end] != '\r') {
      ++name_end;
    }
    const std::string ref = line.substr(name_start, name_end - name_start);
    if (ref.find("preedit") != std::string::npos) {
      return true;
    }
  }
  return false;
}

std::vector<std::string> BuildFastTranslatorPatchLines(
    const std::string& schema_yaml_content) {
  // 防御：schema 显式自定义 translator/tags（列表）时，tag 哨兵会被追加的
  // abc 绕过（tags_ = [哨兵, abc, ...]），原 script_translator 仍查询 abc 段
  // 造成双计算——保守不干预。YAML 嵌套形式：translator: 块内的 tags: 键。
  size_t pos = 0;
  bool in_translator_block = false;
  size_t translator_indent = 0;
  while (pos < schema_yaml_content.size()) {
    const size_t eol = schema_yaml_content.find('\n', pos);
    const size_t end =
        eol == std::string::npos ? schema_yaml_content.size() : eol;
    std::string line = schema_yaml_content.substr(pos, end - pos);
    const size_t s = line.find_first_not_of(" \t\r");
    if (s != std::string::npos) {
      const size_t indent = s;
      const std::string content = line.substr(s);
      if (!in_translator_block) {
        if (content.rfind("translator:", 0) == 0) {
          in_translator_block = true;
          translator_indent = indent;
        }
      } else {
        if (indent > translator_indent) {
          if (content.rfind("tags:", 0) == 0) {
            return {};
          }
        } else {
          in_translator_block = false;  // 块结束
          if (content.rfind("translator:", 0) == 0) {
            in_translator_block = true;
            translator_indent = indent;
          }
        }
      }
    }
    if (eol == std::string::npos) break;
    pos = eol + 1;
  }
  return {
      "  \"engine/translators/@after 1\": \"t9_script_translator@translator\"",
      std::string("  \"translator/tag\": \"") + kT9FastOnlyTag + "\"",
  };
}

}  // namespace t9_patch_utils
}  // namespace rime
