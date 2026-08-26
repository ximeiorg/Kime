// T9 快速词组翻译器——wordgraph 跨键增量缓存。
//
// 设计文档：docs/issues/2026-08-16-t9-快速输入性能优化方案.md
//
// 问题：librime ScriptTranslator::Query 创建 ScriptTranslation 后立即 Evaluate，
// 长数字串（无全串词组精确匹配）时强制整句生成 MakeSentence——对音节图每条边
// 做一次词典 Lookup（实测 18 位单键 wordgraph 213-335ms），且 Sentence 候选
// 位于流头部，Menu 取第一页即触发全部计算 → 九键快速输入视觉卡顿。
//
// 方案：整句生成保持上游时机与排序（Evaluate 内、候选流头部）——整句候选是
// 随尾部输入变化的动态内容，不能砍，只能加速。加速手段：wordgraph 构建改为
// 跨键增量缓存（挂在 translator，非每次 Query 重建）：
//  - 音节图边 (s,e) 由输入子串 [s,e) 决定——追加/删除尾部输入不改变旧边；
//  - 起点查询 dict->Lookup(syllable_graph, s) 的结果只依赖 s 的邻接边集；
//  - 若起点 s 的全部邻接终点 ≤ 两版本输入公共长度 → 结果不变，直接复用
//    （缩短方向按当前终点截断条目）；只有边界起点需要重查；
//  - 非前缀关联（改字/右选重发/清空）或段起点变化 → 缓存整体失效全量重建。
//  - 缓存内容为已 Enroll 的 an<DictEntry> 词条快照（shared_ptr 保活），
//    输入期间 user_dict 不变（仅 commit 时更新），快照安全。
//  - Evaluate 跳过 MakeSentence 时（有可靠词组）清除缓存元数据，防止跨
//    输入序列的陈旧缓存（cache_len 与当前输入不匹配）在下次触发全量重建。
//
// 与 librime 上游 script_translator.cc 的差异（有意为之）：
//  1. wordgraph 增量缓存（见上）；MakeSentence 其余复刻不变。
//  2. 不支持纠错（Corrector）：T9 九键输入为数字串，与 QWERTY 键盘布局的
//     keyboard_map 不兼容，开启不会产生有意义的结果；若需 T9 纠错应在
//     词级别独立设计。其余行为（词组优先序、quality 计算、preedit/comment
//     格式化、completion、DistinctTranslation 去重、contextual_suggestions）
//     与上游保持一致。
//
// 接入：T9 patch 管线以插入式两条 patch 接入（@after 1 + 哨兵 tag），
// 不覆盖 translators 列表。上游词组路径演进时需同步本复刻。

#ifndef T9_SCRIPT_TRANSLATOR_H_
#define T9_SCRIPT_TRANSLATOR_H_

#include <string>

#include <rime/gear/poet.h>
#include <rime/gear/script_translator.h>

namespace rime {

struct SyllableGraph;

class T9ScriptTranslator : public ScriptTranslator {
 public:
  explicit T9ScriptTranslator(const Ticket& ticket);

  an<Translation> Query(const string& input, const Segment& segment) override;

  // 增量构建 wordgraph（T9FastTranslation::MakeSentence 调用）：
  // 优先复用上一键缓存中「邻接终点未超出缓存输入长度」的起点条目，
  // 仅重查边界/新起点。segment_start 用于段坐标一致性校验（音节图边为
  // 段内相对坐标，段起点变化时缓存必须失效）。graph 为输出参数（已清空）。
  void BuildWordGraphIncremental(Dictionary* dict,
                                 UserDictionary* user_dict,
                                 const SyllableGraph& syllable_graph,
                                 const string& input,
                                 size_t segment_start,
                                 WordGraph& graph);

  // 清除 wordgraph 缓存元数据：当 Evaluate 未生成整句时调用，
  // 防止跨输入序列的陈旧缓存引起下次 MakeSentence 全量重建。
  void ClearWordGraphCache() { wordgraph_cache_.input.clear(); }
  // 获取缓存对应的输入串（空 = 无缓存）。
  const string& cached_input() const { return wordgraph_cache_.input; }

 private:
  struct WordGraphCache {
    string input;   // 缓存对应的段输入（空 = 无缓存）
    size_t start = 0;  // 缓存对应的段起点（段内坐标系基准）
    WordGraph graph;
  };
  WordGraphCache wordgraph_cache_;
};

}  // namespace rime

#endif  // T9_SCRIPT_TRANSLATOR_H_
