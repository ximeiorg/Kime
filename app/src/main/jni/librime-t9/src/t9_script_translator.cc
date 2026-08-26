// T9 快速词组翻译器实现（设计文档见同目录 .h 注释）。
//
// 词组路径与整句 wordgraph 构建复刻自 librime src/rime/gear/script_translator.cc
// （ScriptTranslation/ScriptSyllabifier 定义于 .cc 内，无法跨翻译单元引用，
// 故按行为复刻；上游演进时需人工同步）。与上游的有意差异见 .h 头注释。

#include "t9_script_translator.h"

#include <algorithm>
#include <cmath>
#include <stack>
#include <utility>
#include <vector>

#include <boost/range/adaptor/reversed.hpp>

#include <rime/candidate.h>
#include <rime/config.h>
#include <rime/context.h>
#include <rime/dict/corrector.h>
#include <rime/dict/dictionary.h>
#include <rime/dict/user_dictionary.h>
#include <rime/engine.h>
#include <rime/gear/poet.h>
#include <rime/gear/translator_commons.h>
#include <rime/schema.h>
#include <rime/translation.h>
#include <rime/algo/syllabifier.h>

namespace rime {

namespace {

// ── T9 性能埋点（对齐 librime-t9 RimePerf 模式）──
#if defined(RIME_ENABLE_LOGGING) && defined(__ANDROID__)
#include <android/log.h>
#include <chrono>
struct T9ScriptPerfTimer {
  using Clock = std::chrono::steady_clock;
  const char* name;
  Clock::time_point start;
  explicit T9ScriptPerfTimer(const char* n) : name(n), start(Clock::now()) {}
  ~T9ScriptPerfTimer() {
    auto us = std::chrono::duration_cast<std::chrono::microseconds>(
                  Clock::now() - start)
                  .count();
    __android_log_print(ANDROID_LOG_DEBUG, "RimePerf",
                        "[TIMING] [T9Script] %s: %lld us", name,
                        static_cast<long long>(us));
  }
};
#define T9_SCRIPT_PERF_TIMER(name) \
  T9ScriptPerfTimer _t9_script_perf_timer_##__LINE__(name)
#else
#define T9_SCRIPT_PERF_TIMER(name) ((void)0)
#endif

// 复刻自 script_translator.cc 匿名命名空间：音节图 DFS 边遍历。
struct SyllabifyTask {
  const Code& code;
  const SyllableGraph& graph;
  size_t target_pos;
  function<void(SyllabifyTask* task,
                size_t depth,
                size_t current_pos,
                size_t next_pos)>
      push;
  function<void(SyllabifyTask* task, size_t depth)> pop;
};

static bool syllabify_dfs(SyllabifyTask* task,
                          size_t depth,
                          size_t current_pos) {
  if (depth == task->code.size()) {
    return current_pos == task->target_pos;
  }
  SyllableId syllable_id = task->code.at(depth);
  auto z = task->graph.edges.find(current_pos);
  if (z == task->graph.edges.end())
    return false;
  // favor longer spellings
  for (const auto& y : boost::adaptors::reverse(z->second)) {
    size_t end_vertex_pos = y.first;
    if (end_vertex_pos > task->target_pos)
      continue;
    auto x = y.second.find(syllable_id);
    if (x != y.second.end()) {
      task->push(task, depth, current_pos, end_vertex_pos);
      if (syllabify_dfs(task, depth + 1, end_vertex_pos))
        return true;
      task->pop(task, depth);
    }
  }
  return false;
}

// 复刻自 ScriptSyllabifier（不含纠错路径）。
class T9ScriptSyllabifier : public PhraseSyllabifier {
 public:
  T9ScriptSyllabifier(ScriptTranslator* translator,
                      const string& input,
                      size_t start)
      : translator_(translator),
        input_(input),
        start_(start),
        syllabifier_(translator->delimiters(),
                     translator->enable_completion(),
                     translator->strict_spelling()) {}

  Spans Syllabify(const Phrase* phrase) override;
  size_t BuildSyllableGraph(Prism& prism);
  string GetPreeditString(const Phrase& cand) const;
  string GetOriginalSpelling(const Phrase& cand) const;

  const SyllableGraph& syllable_graph() const { return syllable_graph_; }

 protected:
  ScriptTranslator* translator_;
  string input_;
  size_t start_;
  Syllabifier syllabifier_;
  SyllableGraph syllable_graph_;
};

Spans T9ScriptSyllabifier::Syllabify(const Phrase* phrase) {
  Spans result;
  vector<size_t> vertices;
  vertices.push_back(start_);
  SyllabifyTask task{
      phrase->code(), syllable_graph_, phrase->end() - start_,
      [&](SyllabifyTask* task, size_t depth, size_t current_pos,
          size_t next_pos) { vertices.push_back(start_ + next_pos); },
      [&](SyllabifyTask* task, size_t depth) { vertices.pop_back(); }};
  if (syllabify_dfs(&task, 0, phrase->start() - start_)) {
    result.set_vertices(std::move(vertices));
  }
  return result;
}

size_t T9ScriptSyllabifier::BuildSyllableGraph(Prism& prism) {
  return (size_t)syllabifier_.BuildSyllableGraph(input_, prism,
                                                 &syllable_graph_);
}

string T9ScriptSyllabifier::GetPreeditString(const Phrase& cand) const {
  const auto& delimiters = translator_->delimiters();
  std::stack<size_t> lengths;
  string output;
  SyllabifyTask task{cand.matching_code(), syllable_graph_, cand.end() - start_,
                     [&](SyllabifyTask* task, size_t depth, size_t current_pos,
                         size_t next_pos) {
                       size_t len = output.length();
                       if (depth > 0 && len > 0 &&
                           delimiters.find(output[len - 1]) == string::npos) {
                         output += delimiters.at(0);
                       }
                       output +=
                           input_.substr(current_pos, next_pos - current_pos);
                       lengths.push(len);
                     },
                     [&](SyllabifyTask* task, size_t depth) {
                       output.resize(lengths.top());
                       lengths.pop();
                     }};
  if (syllabify_dfs(&task, 0, cand.start() - start_)) {
    return translator_->FormatPreedit(output);
  } else {
    return string();
  }
}

string T9ScriptSyllabifier::GetOriginalSpelling(const Phrase& cand) const {
  if (translator_ &&
      static_cast<int>(cand.code().size()) <= translator_->spelling_hints()) {
    return translator_->Spell(cand.code());
  }
  return string();
}

template <class Ptr, class Iter>
static bool has_exact_match_phrase(Ptr ptr, Iter iter, size_t consumed) {
  return ptr && iter->first == consumed && !iter->second.exhausted() &&
         iter->second.Peek()->IsExactMatch();
}

static bool always_true() {
  return true;
}

template <typename T>
inline static bool prefer_user_phrase(
    T user_phrase_weight,
    T sys_phrase_weight,
    function<bool()> compare_on_tie = always_true) {
  return user_phrase_weight > sys_phrase_weight ||
         (user_phrase_weight == sys_phrase_weight && compare_on_tie());
}

// 词组 + 增量整句翻译流（复刻 ScriptTranslation；wordgraph 经 translator
// 跨键增量缓存，整句生成保持上游时机与候选排序）。
class T9FastTranslation : public Translation {
 public:
  T9FastTranslation(T9ScriptTranslator* translator,
                    Poet* poet,
                    const string& input,
                    size_t start,
                    size_t end_of_input)
      : translator_(translator),
        poet_(poet),
        input_(input),
        start_(start),
        end_of_input_(end_of_input),
        syllabifier_(
            New<T9ScriptSyllabifier>(translator, input, start)) {
    set_exhausted(true);
  }

  bool Evaluate(Dictionary* dict, UserDictionary* user_dict);

  bool Next() override;
  an<Candidate> Peek() override;

 protected:
  bool CheckEmpty();
  bool PrepareCandidate();
  an<Sentence> MakeSentence(Dictionary* dict, UserDictionary* user_dict);

  T9ScriptTranslator* translator_;
  Poet* poet_;
  string input_;
  size_t start_;
  size_t end_of_input_;
  an<T9ScriptSyllabifier> syllabifier_;

  an<DictEntryCollector> phrase_;
  an<UserDictEntryCollector> user_phrase_;
  an<Sentence> sentence_;

  an<Phrase> candidate_ = nullptr;
  size_t candidate_index_ = 0;
  enum CandidateSource {
    kUninitialized,
    kUserPhrase,
    kSysPhrase,
    kSentence,
  };
  CandidateSource candidate_source_ = kUninitialized;

  DictEntryCollector::reverse_iterator phrase_iter_;
  UserDictEntryCollector::reverse_iterator user_phrase_iter_;
};

bool T9FastTranslation::Evaluate(Dictionary* dict, UserDictionary* user_dict) {
  T9_SCRIPT_PERF_TIMER("T9FastTranslation::Evaluate");
  size_t consumed = syllabifier_->BuildSyllableGraph(*dict->prism());
  const auto& syllable_graph = syllabifier_->syllable_graph();
  bool predict_word = translator_->enable_word_completion() &&
                      start_ + consumed == end_of_input_;

  {
    T9_SCRIPT_PERF_TIMER("T9FastTranslation::dict.Lookup");
    phrase_ = dict->Lookup(syllable_graph, 0, &translator_->blacklist(),
                           predict_word);
  }
  if (user_dict) {
    const size_t kUnlimitedDepth = 0;
    const size_t kNumSyllablesToPredictWord = 4;
    T9_SCRIPT_PERF_TIMER("T9FastTranslation::user_dict.Lookup");
    user_phrase_ =
        user_dict->Lookup(syllable_graph, 0, kUnlimitedDepth,
                          predict_word ? kNumSyllablesToPredictWord : 0);
  }
  if (phrase_)
    phrase_iter_ = phrase_->rbegin();
  if (user_phrase_)
    user_phrase_iter_ = user_phrase_->rbegin();

  auto has_reliable = [&](auto ptr, auto iter) {
    return ptr && iter->first == consumed && !iter->second.exhausted() &&
           iter->second.Peek()->IsExactMatch();
  };
  bool has_reliable_phrase = has_reliable(phrase_, phrase_iter_);
  bool has_reliable_user_phrase = has_reliable(user_phrase_, user_phrase_iter_);
  bool has_at_least_two_syllables = syllable_graph.edges.size() >= 2;
  // make sentences when there is no exact-matching phrase candidate
  // （上游时机：整句候选回到流头部，随尾部输入变化——用户核心诉求；
  // wordgraph 由 translator 增量缓存加速，见 MakeSentence）
  if (has_at_least_two_syllables && !has_reliable_phrase &&
      !has_reliable_user_phrase) {
    T9_SCRIPT_PERF_TIMER("T9FastTranslation::MakeSentence");
    sentence_ = MakeSentence(dict, user_dict);
  } else {
    // 追加方向（可靠词组存在但输入增长）：保留缓存，下次 MakeSentence 走增量。
    // 非前缀关联（退格/改字/右选重发）：清空缓存元数据，防陈旧缓存导致
    // prefix_related 误判为 true 而复用过期条目。
    const string& cached = translator_->cached_input();
    const size_t common = std::min(input_.size(), cached.size());
    const bool prefix_related = !cached.empty() && input_.size() != cached.size() &&
        input_.compare(0, common, cached, 0, common) == 0;
    if (!prefix_related) {
      translator_->ClearWordGraphCache();
    }
  }

  return !CheckEmpty();
}

bool T9FastTranslation::Next() {
  if (exhausted())
    return false;
  if (candidate_source_ == kUninitialized) {
    PrepareCandidate();  // to determine candidate_source_
  }
  switch (candidate_source_) {
    case kUninitialized:
      break;
    case kSentence:
      sentence_.reset();
      break;
    case kUserPhrase: {
      UserDictEntryIterator& uter(user_phrase_iter_->second);
      if (!uter.Next()) {
        ++user_phrase_iter_;
      }
    } break;
    case kSysPhrase: {
      DictEntryIterator& iter(phrase_iter_->second);
      if (!iter.Next()) {
        ++phrase_iter_;
      }
    } break;
  }
  candidate_.reset();
  candidate_source_ = kUninitialized;
  if (!CheckEmpty()) {
    ++candidate_index_;
    return true;
  }
  return false;
}

an<Candidate> T9FastTranslation::Peek() {
  if (candidate_source_ == kUninitialized && !PrepareCandidate()) {
    return nullptr;
  }
  if (candidate_->preedit().empty()) {
    candidate_->set_preedit(syllabifier_->GetPreeditString(*candidate_));
  }
  if (candidate_->comment().empty()) {
    auto spelling = syllabifier_->GetOriginalSpelling(*candidate_);
    if (!spelling.empty() && (translator_->always_show_comments() ||
                              spelling != candidate_->preedit())) {
      candidate_->set_comment(spelling);
    }
  }
  candidate_->set_syllabifier(syllabifier_);
  return candidate_;
}

bool T9FastTranslation::PrepareCandidate() {
  if (exhausted()) {
    candidate_source_ = kUninitialized;
    candidate_ = nullptr;
    return false;
  }
  if (sentence_) {
    // 上游排序：整句候选优先（随尾部输入变化，快速输入的动态内容来源）。
    candidate_source_ = kSentence;
    candidate_ = sentence_;
    return true;
  }
  const size_t full_code_length = end_of_input_ - start_;
  size_t user_phrase_code_length = 0;
  if (user_phrase_ && user_phrase_iter_ != user_phrase_->rend()) {
    user_phrase_code_length = user_phrase_iter_->first;
  }
  size_t phrase_code_length = 0;
  if (phrase_ && phrase_iter_ != phrase_->rend()) {
    phrase_code_length = phrase_iter_->first;
  }
  if (user_phrase_code_length > 0 &&
      prefer_user_phrase(
          user_phrase_code_length, phrase_code_length,
          // 編碼長度相同時, 用戶詞優先
          [this, full_code_length]() {
            // 長詞聯想之前須至少出一個嚴格匹配的候選
            // 故確定首選之際, 系統嚴格匹配 > 用戶長詞聯想
            const int kNumExactMatchOnTop = 1;
            return candidate_index_ >= kNumExactMatchOnTop ||
                   prefer_user_phrase(
                       has_exact_match_phrase(user_phrase_, user_phrase_iter_,
                                              full_code_length),
                       has_exact_match_phrase(phrase_, phrase_iter_,
                                              full_code_length));
          })) {
    UserDictEntryIterator& uter = user_phrase_iter_->second;
    const auto& entry = uter.Peek();
    candidate_source_ = kUserPhrase;
    candidate_ =
        New<Phrase>(translator_->language(),
                    entry->IsPredictiveMatch() ? "completion" : "user_phrase",
                    start_, start_ + user_phrase_code_length, entry);
    candidate_->set_quality(std::exp(entry->weight) +
                            translator_->initial_quality() +
                            (entry->quality_len / full_code_length));
    return true;
  } else if (phrase_code_length > 0) {
    DictEntryIterator& iter = phrase_iter_->second;
    const auto& entry = iter.Peek();
    candidate_source_ = kSysPhrase;
    candidate_ =
        New<Phrase>(translator_->language(),
                    entry->IsPredictiveMatch() ? "completion" : "phrase",
                    start_, start_ + phrase_code_length, entry);
    candidate_->set_quality(std::exp(entry->weight) +
                            translator_->initial_quality() +
                            (entry->quality_len / full_code_length));
    return true;
  } else {
    candidate_source_ = kUninitialized;
    candidate_ = nullptr;
    return false;
  }
}

bool T9FastTranslation::CheckEmpty() {
  set_exhausted((!phrase_ || phrase_iter_ == phrase_->rend()) &&
                (!user_phrase_ || user_phrase_iter_ == user_phrase_->rend()) &&
                !sentence_);
  return exhausted();
}

// 复刻自 ScriptTranslation::MakeSentence：wordgraph 构建委托 translator 的
  // 跨键增量缓存（见 T9ScriptTranslator::BuildWordGraphIncremental），
  // Viterbi/评分部分与上游一致。
  an<Sentence> T9FastTranslation::MakeSentence(Dictionary* dict, UserDictionary* user_dict) {
    if (!poet_)
      return nullptr;
    const auto& syllable_graph = syllabifier_->syllable_graph();
  WordGraph graph;
  {
    T9_SCRIPT_PERF_TIMER("T9FastTranslation::MakeSentence.wordgraph(incremental)");
    translator_->BuildWordGraphIncremental(dict, user_dict, syllable_graph,
                                           input_, start_, graph);
  }
  if (auto sentence =
          poet_->MakeSentence(graph, syllable_graph.interpreted_length,
                              translator_->GetPrecedingText(start_))) {
    sentence->Offset(start_);
    sentence->set_syllabifier(syllabifier_);
    return sentence;
  }
  return nullptr;
}

}  // anonymous namespace

// ── T9ScriptTranslator ──

T9ScriptTranslator::T9ScriptTranslator(const Ticket& ticket)
    : ScriptTranslator(ticket) {}

// 文件级模板（translation 与 translator 共用）：消费 collector 至 max_homophones。
template <class QueryResult>
static void EnrollEntriesInto(map<int, DictEntryList>& entries_by_end_pos,
                               const an<QueryResult>& query_result,
                               int max_homophones) {
  if (query_result) {
    for (auto& y : *query_result) {
      DictEntryList& homophones = entries_by_end_pos[y.first];
      while (homophones.size() < (size_t)max_homophones &&
             !y.second.exhausted()) {
        homophones.push_back(y.second.Peek());
        if (!y.second.Next())
          break;
      }
    }
  }
}

void T9ScriptTranslator::BuildWordGraphIncremental(
    Dictionary* dict, UserDictionary* user_dict,
    const SyllableGraph& syllable_graph, const string& input,
    size_t segment_start, WordGraph& graph) {
  const size_t cache_len = wordgraph_cache_.input.size();
  // 前缀关联判定（双向）：追加（九键连打）或缩短（退格删除）都构成前缀关联
  // ——音节图边 (s,e) 由子串 [s,e) 决定，两版本共有的边完全一致，Lookup(s)
  // 结果一致，可复用词条快照。非前缀关联（改字/清空/右选重发）或段起点变化
  // → 缓存整体失效全量重建。
  const size_t common_len = std::min(cache_len, input.size());
  const bool prefix_related =
      cache_len > 0 && cache_len != input.size() &&
      wordgraph_cache_.start == segment_start &&
      input.compare(0, common_len, wordgraph_cache_.input, 0, common_len) == 0;
  if (!prefix_related) {
    wordgraph_cache_.graph.clear();
  }

  const int kMaxSyllablesForUserPhraseQuery = 5;
  size_t reused_starts = 0;
  size_t queried_starts = 0;
  for (const auto& x : syllable_graph.edges) {
    const int s = x.first;
    // 复用判定：该起点在缓存中存在，且当前音节图里它的全部邻接终点都未
    // 超出两版本输入的公共长度——这些边在两版图中完全一致，Lookup(s)
    // 结果不变。缩短方向（退格）缓存条目可能含更长终点的词条，按当前
    // 最大终点截断后复用。
    if (prefix_related) {
      const size_t max_end = x.second.rbegin()->first;
      auto cit = wordgraph_cache_.graph.find(s);
      if (max_end <= common_len && cit != wordgraph_cache_.graph.end()) {
        auto& dst = graph[s];
        for (const auto& e : cit->second) {
          if ((size_t)e.first <= max_end) {
            dst[e.first] = e.second;
          }
        }
        ++reused_starts;
        continue;
      }
    }
    auto& same_start_pos = graph[s];
    if (user_dict) {
      EnrollEntriesInto(same_start_pos,
                        user_dict->Lookup(syllable_graph, s,
                                          kMaxSyllablesForUserPhraseQuery),
                        max_homophones());
    }
    // merge lookup results
    EnrollEntriesInto(
        same_start_pos,
        dict->Lookup(syllable_graph, s, &blacklist()),
        max_homophones());
    ++queried_starts;
  }
#if defined(RIME_ENABLE_LOGGING) && defined(__ANDROID__)
  __android_log_print(ANDROID_LOG_DEBUG, "RimePerf",
                      "[TIMING] [T9Script] wordgraph incremental: "
                      "input_len=%zu cache_len=%zu reused=%zu queried=%zu",
                      input.size(), cache_len, reused_starts, queried_starts);
#endif
  // 回写缓存（含失效后的全量重建结果）。
  wordgraph_cache_.input = input;
  wordgraph_cache_.start = segment_start;
  wordgraph_cache_.graph = graph;
}

an<Translation> T9ScriptTranslator::Query(const string& input,
                                          const Segment& segment) {
  if (!dict_ || !dict_->loaded())
    return nullptr;
  // 不依赖 translator/tags 配置：t9 patch 以 "translator/tags": [] 禁用原
  // script_translator（同 namespace，避免双计算），该 patch 会连带清空本组件
  // 读到的 tags_——故此处固定处理 abc tag（拼音方案的默认翻译 tag），并兼容
  // 方案自定义 tags（tags_ 保留时按其判定）。
  if (!segment.HasTag("abc") && !segment.HasAnyTagIn(tags_))
    return nullptr;
  DLOG(INFO) << "t9 input = '" << input << "', [" << segment.start << ", "
             << segment.end << ")";

  FinishSession();

  bool enable_user_dict =
      user_dict_ && user_dict_->loaded() && !IsUserDictDisabledFor(input);

  size_t end_of_input = engine_->context()->input().length();
  // the translator should survive translations it creates
  auto result = New<T9FastTranslation>(this, poet_.get(),
                                       input, segment.start, end_of_input);
  if (!result || !result->Evaluate(
                     dict_.get(), enable_user_dict ? user_dict_.get() : NULL)) {
    return nullptr;
  }
  auto deduped = New<DistinctTranslation>(result);
  if (contextual_suggestions_ && poet_) {
    return poet_->ContextualWeighted(deduped, input, segment.start, this);
  }
  return deduped;
}

}  // namespace rime
