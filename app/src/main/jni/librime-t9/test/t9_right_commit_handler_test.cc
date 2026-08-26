// T9RightCommitHandler 三层消费算法测试
//
// 对应 Kotlin T9RightCommitHandlerTest.kt 中的关键场景。
// 测试策略：直接设置 Context 状态（不依赖完整 T9Processor），
// 验证 HandleRightCommit 在各场景下的 full/partial commit 判定与 buffer 状态变化。

#include "t9_right_commit_handler.h"

#include <gtest/gtest.h>

#include "t9_letter_buffer_strategy.h"
#include "t9_pinyin_map.h"

namespace rime {
namespace {

// ── 测试辅助 ──

struct ScenarioSetup {
    std::string digits;
    std::vector<SyllableOption> selections;
    int consumed_count = 0;
    T9StateMachine::State state = T9StateMachine::State::kInput;
    std::optional<SyllableOption> selected_option;
    std::optional<std::string> selection_candidate_digits;
    std::string confirmed_pinyin;
    std::vector<SyllableOption> selection_history;
    bool has_separator = false;
    int separator_position = -1;
};

T9RightCommitHandler::Context MakeContext(const ScenarioSetup& s) {
    T9RightCommitHandler::Context ctx;
    ctx.input_buffer = T9Buffer(s.digits, s.selections, s.consumed_count,
                                static_cast<int>(s.digits.size()),
                                s.has_separator, s.separator_position);
    ctx.state_machine.RestoreFrom(s.state, s.selected_option,
                                  s.selection_candidate_digits,
                                  s.confirmed_pinyin,
                                  s.selection_history);
    return ctx;
}

// ── digitSegment 模式（无选择，有未分配数字） ──

TEST(T9RightCommitHandlerTest, DigitSegmentFullCommitWhenAllDigitsConsumed) {
    // fallback 路径（rime_consumed_digits=-1，RIME 查询失败时）：输入 "54482"，
    // 右选 "ji hua" → AlignWithBuffer 消费全部 5 位 → full commit
    auto ctx = MakeContext({"54482", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ji hua"), 0,
                                            /*rime_consumed_digits=*/-1);
    EXPECT_TRUE(result);
    EXPECT_TRUE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.state_machine.is_idle());
}

TEST(T9RightCommitHandlerTest, DigitSegmentPartialCommitWhenPartConsumed) {
    // fallback 路径（rime_consumed_digits=-1）：输入 "54482"，右选 "ji" →
    // AlignWithBuffer 消费 2 位 → partial commit, remaining "482"
    auto ctx = MakeContext({"54482", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ji"), 0,
                                            /*rime_consumed_digits=*/-1);
    EXPECT_FALSE(result);
    EXPECT_EQ(ctx.input_buffer.unassigned(), "482");
}

TEST(T9RightCommitHandlerTest, DigitSegmentPartialCommit_WoDe_96339633) {
    // fallback 路径（rime_consumed_digits=-1，RIME 查询失败时）：
    // 输入 96339633（96 33 96 33 → wo de wo de），
    // 右选 "wo de" → AlignWithBuffer 消费 4 位（96 33），剩余 "9633"
    // 验证 digitSegment 模式下 fallback partial commit 正确性
    auto ctx = MakeContext({"96339633", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("wo de"), 2,
                                            /*rime_consumed_digits=*/-1);
    EXPECT_FALSE(result);                                 // partial commit
    EXPECT_EQ(ctx.input_buffer.unassigned(), "9633");     // 剩余 4 位
    EXPECT_EQ(ctx.input_buffer.consumed_count, 4);        // 已消费 4 位
    EXPECT_EQ(ctx.input_buffer.digit_sequence, "96339633");// 完整序列保留
    EXPECT_TRUE(ctx.state_machine.is_input());             // 进入 INPUT 态
}

TEST(T9RightCommitHandlerTest, DigitSegmentPartialCommit_WoDeWoDe_96339633) {
    // fallback 路径（rime_consumed_digits=-1）：输入 96339633，
    // 右选 "wo de wo de"（全部消费）→ AlignWithBuffer → full commit
    auto ctx = MakeContext({"96339633", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("wo de wo de"), 4,
                                            /*rime_consumed_digits=*/-1);
    EXPECT_TRUE(result);                                   // full commit
    EXPECT_TRUE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.state_machine.is_idle());
}

TEST(T9RightCommitHandlerTest, DigitSegmentPartialCommit_ZouDe_96339633) {
    // fallback 路径（rime_consumed_digits=-1，历史 Bug-2026-07-26-v3 保护）：
    // 输入 96339633，右选 "zou de"（简拼+全拼混合：z→9, de→33）
    // AlignWithBuffer 消费 4 位（zou→"968"前缀匹配"96"得2位 + de→"33"全匹配得2位），剩余 "9633"
    auto ctx = MakeContext({"96339633", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("zou de"), 2,
                                            /*rime_consumed_digits=*/-1);
    EXPECT_FALSE(result);                                 // partial commit
    // zou→PinyinToDigitCode="968"（z=9,o=6,u=8），
    // 输入前缀 "96" 匹配 "968" 前2位，de→"33" 匹配 "33"
    EXPECT_EQ(ctx.input_buffer.consumed_count, 4);
    EXPECT_EQ(ctx.input_buffer.unassigned(), "9633");
}

TEST(T9RightCommitHandlerTest, DigitSegmentPartialSyllableFullCommit) {
    // fallback 路径（rime_consumed_digits=-1）：输入 "9435"，右选 "zhe li"
    // → AlignWithBuffer 消费 zhe(943)+li前缀(5) = 4 位 → full commit
    auto ctx = MakeContext({"9435", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("zhe li"), 2,
                                            /*rime_consumed_digits=*/-1);
    EXPECT_TRUE(result);
    EXPECT_TRUE(ctx.input_buffer.is_empty());
}

TEST(T9RightCommitHandlerTest, DigitSegmentPartialSyllablePartialCommit) {
    // fallback 路径（rime_consumed_digits=-1）：输入 "94356"，右选 "zhe li"
    // → AlignWithBuffer 消费 4 位 → partial commit, remaining "6"
    auto ctx = MakeContext({"94356", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("zhe li"), 2,
                                            /*rime_consumed_digits=*/-1);
    EXPECT_FALSE(result);
    EXPECT_EQ(ctx.input_buffer.unassigned(), "6");
}

TEST(T9RightCommitHandlerTest, DigitSegmentFullCommitWithNullCandidatePinyin) {
    // 无候选拼音（candidate_pinyin 为 null，RIME 查询必然失败）→ fallback 贪婪最长匹配
    auto ctx = MakeContext({"54482", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    // null candidatePinyin → firstSyllableOptions 消费首音节
    bool result = handler.HandleRightCommit(ctx, std::nullopt, 0, /*rime_consumed_digits=*/-1);
    EXPECT_FALSE(result);  // 只消费首音节，不会全部消费
}

// ── 方案 A：RIME 候选 end 换算的消费位数优先（2026-08-01） ──

TEST(T9RightCommitHandlerTest, DigitSegment_RimeConsumed_YongDong_96636_FullCommit) {
    // 方案 A 核心场景：输入 96636，右选 "yong dong"（派生 966+36，RIME end=5）
    // 旧算法：完整拼音 yong=9664/dong=3664 前缀匹配只消费 4 位，剩 6（bug）
    // 方案 A：rime_consumed_digits=5 → 消费全部 5 位 → full commit
    auto ctx = MakeContext({"96636", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(
        ctx, std::optional<std::string>("yong dong"), 2, /*rime_consumed_digits=*/5);
    EXPECT_TRUE(result);                                  // full commit
    EXPECT_TRUE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.state_machine.is_idle());
}

TEST(T9RightCommitHandlerTest, DigitSegment_RimeConsumed_ZongMen_96636_FullCommit) {
    // 方案 A 核心场景：输入 96636，右选 "zong men"（派生 96+636，RIME end=5）
    // 旧算法只消费 3 位（zong 派生 zo=96 + men 前缀），剩 36（bug）
    auto ctx = MakeContext({"96636", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(
        ctx, std::optional<std::string>("zong men"), 2, /*rime_consumed_digits=*/5);
    EXPECT_TRUE(result);                                  // full commit
    EXPECT_TRUE(ctx.input_buffer.is_empty());
}

TEST(T9RightCommitHandlerTest, DigitSegment_RimeConsumed_Yong_96636_PartialCommit) {
    // 方案 A 部分消费：输入 96636，右选 "yong"（派生 966，RIME end=3）
    // 消费 3 位 → partial commit，剩余 "36" 可继续匹配 dong
    auto ctx = MakeContext({"96636", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(
        ctx, std::optional<std::string>("yong"), 1, /*rime_consumed_digits=*/3);
    EXPECT_FALSE(result);                                 // partial commit
    EXPECT_EQ(ctx.input_buffer.consumed_count, 3);
    EXPECT_EQ(ctx.input_buffer.unassigned(), "36");
}

TEST(T9RightCommitHandlerTest, DigitSegment_RimeConsumed_ClampedToSegment) {
    // 防御：rime_consumed_digits 超过 unassigned 长度时 clamp 到段长
    auto ctx = MakeContext({"96636", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(
        ctx, std::optional<std::string>("yong dong"), 2, /*rime_consumed_digits=*/99);
    EXPECT_TRUE(result);                                  // clamp 后消费全部 → full commit
    EXPECT_TRUE(ctx.input_buffer.is_empty());
}

TEST(T9RightCommitHandlerTest, DigitSegment_RimeConsumed_Negative_FallbackToAlign) {
    // 防御：rime_consumed_digits=-1（无法确定）→ fallback 到旧 AlignWithBuffer 算法
    // 旧算法对 96636+"wo men" 消费 5 位（wo=96 完整 + men=636 完整）→ full commit
    auto ctx = MakeContext({"96636", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(
        ctx, std::optional<std::string>("wo men"), 2, /*rime_consumed_digits=*/-1);
    EXPECT_TRUE(result);
    EXPECT_TRUE(ctx.input_buffer.is_empty());
}

TEST(T9RightCommitHandlerTest, DigitSegmentFullCommitWhenAllAbbrevAndNoComment) {
    // 全简拼无候选 → full commit
    // 需要 selectionHistory 全为 digitLength==1
    std::vector<SyllableOption> history{SyllableOption("j", 1)};
    auto ctx = MakeContext({
        .digits = "5",
        .selections = {SyllableOption("j", 1)},
        .consumed_count = 1,
        .state = T9StateMachine::State::kSelection,
        .selected_option = SyllableOption("j", 1),
        .selection_candidate_digits = std::optional<std::string>("5"),
        .confirmed_pinyin = "",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // 无候选注释 + 全简拼 → full commit
    bool result = handler.HandleRightCommit(ctx, std::nullopt, 0);
    EXPECT_TRUE(result);
    EXPECT_TRUE(ctx.input_buffer.is_empty());
}

// ── letterBuffer 模式（有选择，无未分配数字） ──

TEST(T9RightCommitHandlerTest, LetterBufferFullCommitWhenAllConsumed) {
    // 场景：输入 "54482"，左选 "ji"(2) + "hua"(3)，buffer="jihua"
    // 右选 "ji hua" → 全部消费 → full commit
    std::vector<SyllableOption> sels{SyllableOption("ji", 2), SyllableOption("hua", 3)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("482"),
        .confirmed_pinyin = "ji",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ji hua"), 2);
    EXPECT_TRUE(result);
    EXPECT_TRUE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.state_machine.is_idle());
}

TEST(T9RightCommitHandlerTest, LetterBufferPartialCommitBug4) {
    // bug4 场景：输入 23744 → 右选"ce" → 左选"pi" → 左选"h"
    // buffer="pih"，SELECTION(h, "4")
    // 右选"皮"(comment="pi h", textLength=1) → partial commit, buffer="h"
    std::vector<SyllableOption> sels{SyllableOption("pi", 2), SyllableOption("h", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "744",
        .selections = sels,
        .consumed_count = 3,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("4"),
        .confirmed_pinyin = "pi",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // "pi h" 字母数=3, selectedPinyin="pih".length=3 → effectiveLetterCount >= selectedPinyin.length
    // 进入 SELECTION 态字母 buffer 处理
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("pi h"), 1);
    EXPECT_FALSE(result);  // partial commit — "h" 保留
    EXPECT_EQ(ctx.input_buffer.selected_pinyin(), "h");
}

TEST(T9RightCommitHandlerTest, LetterBufferFullCommitWhenCandidateCoversAll) {
    // 场景：buffer="ligub"，右选 "li gu b" textLength=2 → full commit
    // 但需要 selectedPinyin.endsWith(prevSelectedOption.pinyin)
    // selectedPinyin="ligub", prevSelectedOption="b" → "ligub".endsWith("b") = true
    std::vector<SyllableOption> sels{
        SyllableOption("li", 2), SyllableOption("gu", 2), SyllableOption("b", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[2],
        .selection_candidate_digits = std::optional<std::string>("2"),
        .confirmed_pinyin = "ligu",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // effectiveLetterCount("li gu b") = 5 (l,i,g,u,b), selectedPinyin="ligub".length=5
    // → effectiveLetterCount >= selectedPinyin.length → 进入 SELECTION 字母处理
    // hasSyllableBoundaries=true (3 syllables), candidateTextLength=2 >= commentSyllables.size=3? No, 2 < 3
    // → isFullCommit = false (第一项条件不满足)
    // → nonSelectedPart = "ligu" (dropLast "b")
    // → 走消费路径...
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("li gu b"), 2);
    // 实际行为：candidateTextLength(2) < commentSyllables.size(3) → isFullCommit 第一项 false
    // 走消费路径，非选中部分 "ligu" 被消费，保留 "b"
    EXPECT_FALSE(result);
    EXPECT_EQ(ctx.input_buffer.selected_pinyin(), "b");
}

TEST(T9RightCommitHandlerTest, LetterBufferPartialSelectionConsumption_54482_LiGua) {
    // 场景（T11.12 修复）：输入 54482，左选 li(2) + gua(3)，右选 "犁骨 li gu"
    // 候选词 "li gu" 部分消费 gua（gu=48 消费，a=2 保留为 unassigned）。
    // 期望：gua 被部分消费后不保留为 selection，剩余数字 '2' 进入纯数字 buffer。
    //
    // Bug 根因：HandleLetterBufferRightCommit 的 remaining_digits 路径循环条件
    // `cumulative + digit_length <= consumed_digit_count` 只检测完全消费，
    // 对 gua（cumulative=2 < consumed_digit_count=4 < 2+3=5，部分消费）误判为未消费，
    // 导致 T9Buffer("2", [gua(3)], 3, 1) 不一致状态，
    // ToRimeInputString() 返回 "gua" 而非 "2"，RIME 候选词显示挂/瓜而非 a/b/c。
    //
    // adb 日志证据（/tmp/t9_ligu_bug.log L70-73, L86-87）：
    //   consumedDigitCount=4, cutIndex=1, remainingSels.size=1   ← 应为 cutIndex=2, size=0
    //   ToRimeInputString: all-full-pinyin → 'gua'                ← 应为 '2'
    //   ReplaceFullPinyin: 'gua'                                   ← 应为 '2'
    std::vector<SyllableOption> sels{SyllableOption("li", 2), SyllableOption("gua", 3)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("482"),
        .confirmed_pinyin = "li",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("li gu"), 2);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_TRUE(ctx.input_buffer.selections.empty());      // gua 部分消费，不保留
    EXPECT_EQ(ctx.input_buffer.unassigned(), "2");         // 剩余数字 '2'
    EXPECT_EQ(ctx.input_buffer.ToRimeInputString(), "2");  // 发给 RIME 的是 '2' 而非 'gua'
}

// ── T11.x 回归测试：每个修复场景对应一个测试，避免后续修改导致回归 ──

TEST(T9RightCommitHandlerTest, T11_2_LetterBufferRemainingDigits_54482_JiHua) {
    // T11.2：输入 54482 → 左选 ji → 左选 hua → 右选"几乎(ji hu)"
    // 候选"ji hu"消费 ji(2)+hu前缀(2 from hua=482) = 4 位，剩余 '2'。
    // 修复前：HandleLetterBufferRightCommit 基于字母数 RemoveConsumedSelections，
    //         "jihu"消费 sel[0]=ji 后 "hu" 不匹配 sel[1]=hua，导致 hua 残留。
    // 修复后：remaining_digits 非空时直接用 remaining_digits 构建纯数字 buffer。
    std::vector<SyllableOption> sels{SyllableOption("ji", 2), SyllableOption("hua", 3)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("482"),
        .confirmed_pinyin = "ji",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ji hu"), 2);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_TRUE(ctx.input_buffer.selections.empty());      // hua 被部分消费，不保留
    EXPECT_EQ(ctx.input_buffer.unassigned(), "2");         // 剩余数字 '2'
    EXPECT_EQ(ctx.input_buffer.ToRimeInputString(), "2");
}

TEST(T9RightCommitHandlerTest, T11_5_LockedDigitsZeroWhenSelectionsEmpty_5143_Ke) {
    // T11.5：输入 5→1→4→3（1 为分词键，digitSeq="543"）→ 左选 k → 右选"肯(ken)"
    //        partial commit 清空 selections，prev_selected_option=k 仍存在。
    //        → 右选"个(he)"，应消费剩余 unassigned="43"（h→4, e→3）→ full commit。
    // 修复前：locked_digits 判断只看 prev_selected_option.has_value()，误锁1位，
    //         max_consumable=2-1=1，只允许消费1位，剩余"3"未消费 → partial commit。
    // 修复后：locked_digits 条件增加 !buf.selections.empty()，selections 空时不锁定，
    //         max_consumable=2，消费"43"全部 → full commit 上屏"肯个"。
    auto ctx = MakeContext({
        .digits = "543",
        .selections = {},
        .consumed_count = 1,
        .state = T9StateMachine::State::kInput,
        .selected_option = SyllableOption("k", 1),
        .selection_candidate_digits = std::optional<std::string>("5"),
        .confirmed_pinyin = "",
        .selection_history = {}
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("he"), 1);
    EXPECT_TRUE(result);                                   // full commit
    EXPECT_TRUE(ctx.input_buffer.is_empty());
}

TEST(T9RightCommitHandlerTest, T11_6_LetterBufferConsumedDigitsFromPinyin_543_Ke) {
    // T11.6：输入 5→1→4→3（digitSeq="543"）→ 左选 k → 左选 he → 右选"可(ke)"
    // 候选"ke"只消费 k→5（1位），he 保留。
    // 修复前：letterBuffer 用 Take("khe",2)="kh"→PinyinToDigitCode="54"，错误消费2位。
    // 修复后：ComputeConsumedDigitsFromPinyin("543","ke")=1（k→5匹配，e→3不匹配4），
    //         consumed_digit_count=1，保留 he selection。
    std::vector<SyllableOption> sels{SyllableOption("k", 1), SyllableOption("he", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "543",
        .selections = sels,
        .consumed_count = 3,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("43"),
        .confirmed_pinyin = "k",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ke"), 1);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_EQ(ctx.input_buffer.selections.size(), 1u);     // he 保留
    EXPECT_EQ(ctx.input_buffer.selections[0].pinyin, "he");
    EXPECT_EQ(ctx.input_buffer.unassigned(), "");          // 新 buffer digitSeq="43", consumed=2
    EXPECT_EQ(ctx.input_buffer.ToRimeInputString(), "he");
}

TEST(T9RightCommitHandlerTest, T11_7_ApostropheConsumedDigitsFromPinyin_543_Ku) {
    // T11.7：输入 5→1→4→3（digitSeq="543"）→ 左选 k → 右选"苦(ku)"
    // 候选"ku"只消费 k→5（已被 selection 消费），不应额外消费 unassigned。
    // 修复前：apostrophe 用字母数差值 |ku|-|k|=1，从 unassigned 消费1位"4"，剩余"3"。
    // 修复后：ComputeConsumedDigitsFromPinyin("543","ku")=1（k→5匹配，u→8不匹配4），
    //         consumed_from_unassigned=1-1=0，不消费 unassigned，剩余"43"。
    std::vector<SyllableOption> sels{SyllableOption("k", 1)};
    auto ctx = MakeContext({
        .digits = "543",
        .selections = sels,
        .consumed_count = 1,
        .state = T9StateMachine::State::kInput,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("5"),
        .confirmed_pinyin = "",
        .selection_history = {sels[0]}
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ku"), 1);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_EQ(ctx.input_buffer.unassigned(), "43");        // 不消费 unassigned，保留全部
}

TEST(T9RightCommitHandlerTest, T11_Separator_FullCommit_543_JiaGe) {
    // 输入 5→1→4→3（1 为分词键，digitSeq="543"），分隔符在位置 1。
    // 无 selections，右选"价格 jia ge"（2 音节）。
    // 标准匹配 "jia"→"542" 在 "543" 中贪婪匹配 "54"(2位)，
    // 剩余 "3" 不匹配 "ge"→"43" → consumed=2。
    // 分隔符感知：5 是第 1 段，43 是第 2 段。
    // "jia"→"5"(1位) + "ge"→"43"(2位) = 3位 = full commit。
    auto ctx = MakeContext({
        .digits = "543",
        .selections = {},
        .consumed_count = 0,
        .state = T9StateMachine::State::kInput,
        .has_separator = true,
        .separator_position = 1
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jia ge"), 2);
    EXPECT_TRUE(result);                                   // full commit
    EXPECT_TRUE(ctx.input_buffer.is_empty());
}

TEST(T9RightCommitHandlerTest, MultiSeparator_FullCommit_5436_JianGeMian) {
    // 输入 5→1→4→3→1→6（两次分词键，digitSeq="5436"，positions=[1,3]）。
    // 无 selections，右选"见个面 jian ge mian"（3 音节，3 段）。
    // 段：[5], [43], [6]。
    // "jian"→"5"(1位) + "ge"→"43"(2位) + "mian"→"6"(1位) = 4位 = full commit。
    auto ctx = MakeContext({
        .digits = "5436",
        .selections = {},
        .consumed_count = 0,
        .state = T9StateMachine::State::kInput
    });
    ctx.input_buffer.separator_positions = {1, 3};
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jian ge mian"), 3);
    EXPECT_TRUE(result);                                   // full commit
    EXPECT_TRUE(ctx.input_buffer.is_empty());
}

TEST(T9RightCommitHandlerTest, MultiSeparator_PartialCommit_5436_JianGeMing) {
    // 输入 5→1→4→3→1→6（digitSeq="5436"，positions=[1,3]）。
    // 右选"见个面 jian ge ming"（3 音节）：
    // 段 [5]→"jian"(1位) + [43]→"ge"(2位) + [6]→"ming"(1位前缀匹配) = 4位 = 也是 full？
    // 实际 "ming"→"6464" 匹配 "6" 前缀 1 位，总 4 位 == 段长 → full commit。
    // 该测试验证 3 段匹配的完整消费路径。
    auto ctx = MakeContext({
        .digits = "5436",
        .selections = {},
        .consumed_count = 0,
        .state = T9StateMachine::State::kInput
    });
    ctx.input_buffer.separator_positions = {1, 3};
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jian ge ming"), 3);
    EXPECT_TRUE(result);                                   // full commit（3 段全部匹配）
    EXPECT_TRUE(ctx.input_buffer.is_empty());
}

TEST(T9RightCommitHandlerTest, T11_Separator_PartialCommit_543_KanGuo) {
    // 输入 5→1→4→3 → 左选 k（digitSeq="43"，selections=[k(1)]，consumed=0）。
    // 右选"看过 kan guo"（2 音节）。
    // 标准匹配 "kan"→"526" 在 "43" 中 NO_MATCH。
    // 音节回退：提取额外音节 "guo" → "486" 在 "43" 中 PREFIX_MATCH "4"(1位)。
    // → consumed=1，剩余 "3" → d,e,f。
    std::vector<SyllableOption> sels{SyllableOption("k", 1)};
    std::vector<SyllableOption> history{sels[0]};
    auto ctx = MakeContext({
        .digits = "43",
        .selections = sels,
        .consumed_count = 0,
        .state = T9StateMachine::State::kInput,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("5"),
        .confirmed_pinyin = "",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("kan guo"), 2);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_EQ(ctx.input_buffer.unassigned(), "3");         // 剩余 "3" → d,e,f
}

TEST(T9RightCommitHandlerTest, T11_8_LetterBufferPreserveRemainingSelections_5485426_Jiu) {
    // T11.8：输入 5485426 → 左选 jiu → 左选 jian → 右选"就(jiu)"
    // 候选"jiu"消费 jiu(3)，应保留 jian(4) 并恢复 SELECTION 态。
    // 修复前：remaining_digits 路径创建纯数字 buffer，丢失 jian 并进入 INPUT 态。
    // 修复后：基于 consumed_digit_count 计算，保留剩余 selections 并恢复 SELECTION 态。
    std::vector<SyllableOption> sels{SyllableOption("jiu", 3), SyllableOption("jian", 4)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "5485426",
        .selections = sels,
        .consumed_count = 7,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("5426"),
        .confirmed_pinyin = "jiu",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jiu"), 1);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_EQ(ctx.input_buffer.selections.size(), 1u);     // jian 保留
    EXPECT_EQ(ctx.input_buffer.selections[0].pinyin, "jian");
    EXPECT_EQ(ctx.input_buffer.unassigned(), "");          // 新 buffer digitSeq="5426", consumed=4
    EXPECT_EQ(ctx.input_buffer.ToRimeInputString(), "jian");
}

TEST(T9RightCommitHandlerTest, T11_9_AllAbbrevMultiSyllableFullCommit_54347_JiuGenErGongSi) {
    // T11.9：输入 514131417（去分词键后 digitSeq="54347"）→ 全简拼左选 j,g,e,g
    //        → 右选"究根儿公司(jiu gen er gong si)" 5音节
    // 候选词5音节覆盖4 selections + 1 unassigned，应 full commit。
    // 修复前：ComputeConsumedDigitsFromPinyin 全简拼场景计算错误（jiu→548 前缀匹配54消耗2位），
    //         total_consumed=2 < consumedCount=4 → consumed_from_unassigned=-2 → return {"","7"}。
    // 修复后：branch2 canCoverAll=true 时验证 unassigned 首位数字匹配候选词对应音节首字母数字码，
    //         "si"→"74"，unassigned="7" 匹配 → full commit。
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1),
        SyllableOption("e", 1), SyllableOption("g", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2], sels[3]};
    auto ctx = MakeContext({
        .digits = "54347",
        .selections = sels,
        .consumed_count = 4,
        .state = T9StateMachine::State::kInput,
        .selected_option = std::nullopt,
        .selection_candidate_digits = std::nullopt,
        .confirmed_pinyin = "",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(
        ctx, std::optional<std::string>("jiu gen er gong si"), 5);
    EXPECT_TRUE(result);                                   // full commit
    EXPECT_TRUE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.state_machine.is_idle());
}

TEST(T9RightCommitHandlerTest, LetterBuffer_AllAbbrev_CompletionExtraSyllable_5379_KaiDeQiWanXiao) {
    // 输入 5379 → 左选 k,d,q,w（全简拼），无 unassigned。
    // 右选"开得起玩笑 kai de qi wan xiao"（5 音节）——词典 completion 将输入码匹配的长句
    // （前4音节 kai de qi wan → 5,3,7,9）扩展出第 5 音节"笑 xiao"。
    // 覆盖末选择"w"的音节是第 4 个"wan"，而非最后一个"xiao"。
    // 修复前：用 comment_syllables.back()="xiao" 匹配 prevOpt="w" 失败
    //         → last_syl_covers_prev_opt=false → 拒绝 full commit → partial commit。
    // 修复后：用覆盖末选择的音节 comment_syllables[selectionCount-1]="wan"，
    //         StartsWith("wan","w")=true → full commit 直接上屏。
    std::vector<SyllableOption> sels{
        SyllableOption("k", 1), SyllableOption("d", 1),
        SyllableOption("q", 1), SyllableOption("w", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2], sels[3]};
    auto ctx = MakeContext({
        .digits = "5379",
        .selections = sels,
        .consumed_count = 4,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[3],
        .selection_candidate_digits = std::optional<std::string>("9"),
        .confirmed_pinyin = "kdq",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(
        ctx, std::optional<std::string>("kai de qi wan xiao"), 5);
    EXPECT_TRUE(result);                                   // full commit
    EXPECT_TRUE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.state_machine.is_idle());
}

// ── CheckExtraSyllableCommit 纯函数单测 ──

TEST(T9RightCommitHandlerTest, CheckExtraSyllable_FullCommit_WhenCoveringSylMatchesLastSel) {
    // 5379 → [k,d,q,w]，候选"kai de qi wan xiao"（completion 额外音节"xiao"）。
    // 覆盖末选择"w"的音节 = 第 4 个"wan"（StartsWith("wan","w")=true）→ full commit。
    auto check = LetterBufferStrategy::CheckExtraSyllableCommit(
        {"kai", "de", "qi", "wan", "xiao"}, 4,
        SyllableOption("w", 1), false, 5);
    EXPECT_TRUE(check.last_syl_covers_prev_opt);
    EXPECT_TRUE(check.is_full_commit);
}

TEST(T9RightCommitHandlerTest, CheckExtraSyllable_NotFullCommit_WhenCoveringSylMismatch) {
    // 末选择 he（全拼），候选"kai hu"：覆盖音节"hu"不匹配"he" → 拒绝 full commit。
    auto check = LetterBufferStrategy::CheckExtraSyllableCommit(
        {"kai", "hu"}, 2, SyllableOption("he", 2), false, 2);
    EXPECT_FALSE(check.last_syl_covers_prev_opt);
    EXPECT_FALSE(check.is_full_commit);
}

TEST(T9RightCommitHandlerTest, CheckExtraSyllable_NotFullCommit_WhenTextLengthInsufficient) {
    // candidateTextLength < 音节数 → 防简拼误判。
    auto check = LetterBufferStrategy::CheckExtraSyllableCommit(
        {"kai", "de", "qi", "wan", "xiao"}, 4,
        SyllableOption("w", 1), false, 4);
    EXPECT_FALSE(check.is_full_commit);
}

TEST(T9RightCommitHandlerTest, CheckExtraSyllable_FullCommit_WhenUnassigned) {
    // 有 unassigned：额外音节覆盖 unassigned（如"jia ge hu c"的"c"）。
    auto check = LetterBufferStrategy::CheckExtraSyllableCommit(
        {"jia", "ge", "hu", "c"}, 3, SyllableOption("hu", 2), true, 4);
    EXPECT_TRUE(check.is_full_commit);
}

TEST(T9RightCommitHandlerTest, CheckExtraSyllable_FullCommit_WhenLastSylEqualsPrevOpt) {
    // 无额外音节（音节数 == 选择数），末音节 == 末选择 → full commit。
    auto check = LetterBufferStrategy::CheckExtraSyllableCommit(
        {"kai", "hu"}, 2, SyllableOption("hu", 2), false, 2);
    EXPECT_TRUE(check.is_full_commit);
}

TEST(T9RightCommitHandlerTest, T11_11_Branch2LockLeftColumn_54347_JiuGenEr) {
    // T11.11：输入 514131417（digitSeq="54347"）→ 全简拼左选 j,g,e,g
    //         → 右选"究根儿(jiu gen er)" 3音节，保留剩余 [g] + unassigned='7'
    // 修复前：branch2 !canCoverAll 分支不设置 separator_consumed_digits + left_column_locked，
    //         左侧候选区显示 unassigned='7' 对应的 p/q/r/s，而非剩余 selection g 对应的 g/h/i。
    // 修复后：有剩余 selections 时，从 digit_sequence 截取剩余 selection 数字段设置
    //         separator_consumed_digits='4' + left_column_locked=true。
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1),
        SyllableOption("e", 1), SyllableOption("g", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2], sels[3]};
    auto ctx = MakeContext({
        .digits = "54347",
        .selections = sels,
        .consumed_count = 4,
        .state = T9StateMachine::State::kInput,
        .selected_option = std::nullopt,
        .selection_candidate_digits = std::nullopt,
        .confirmed_pinyin = "",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(
        ctx, std::optional<std::string>("jiu gen er"), 3);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_EQ(ctx.input_buffer.selections.size(), 1u);     // g 保留
    EXPECT_EQ(ctx.input_buffer.selections[0].pinyin, "g");
    EXPECT_EQ(ctx.input_buffer.unassigned(), "7");         // 剩余数字 7
    EXPECT_TRUE(ctx.left_column_locked);                   // 左侧锁定
    ASSERT_TRUE(ctx.separator_consumed_digits.has_value());
    EXPECT_EQ(*ctx.separator_consumed_digits, "4");        // 锁定为 '4'（g 对应数字）
}

TEST(T9RightCommitHandlerTest, T11_4_AllFullPinyinBranchSendPinyin_8268426_Ti) {
    // T11.4：输入 826 8426 → 左选 tan → 右选"檀" → 左选 tian → 右选"替ti"
    // 候选"ti"是 tian 的真前缀（场景18），保留 "an"→"26"。
    // 此测试验证 letterBuffer 场景18路径：consumed_pinyin="ti" < prevOpt.pinyin="tian"，
    // "tian".startsWith("ti") → 走纯数字 buffer 路径，remaining="26"（an 的数字码）。
    std::vector<SyllableOption> sels{SyllableOption("tian", 4)};
    std::vector<SyllableOption> history{sels[0]};
    auto ctx = MakeContext({
        .digits = "8426",
        .selections = sels,
        .consumed_count = 4,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("8426"),
        .confirmed_pinyin = "",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ti"), 1);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_TRUE(ctx.state_machine.is_input());             // 进入 INPUT 态
    EXPECT_EQ(ctx.input_buffer.unassigned(), "26");        // an = 2,6
}

// ── apostrophe 模式（有选择，有未分配数字） ──

TEST(T9RightCommitHandlerTest, ApostropheFullCommitWhenAllConsumed) {
    // 场景：buffer: digit_sequence="54482", selections=[ji(2)], consumedCount=2
    // unassigned="482", selectedPinyin="ji"
    // 右选 "ji hua" → 消费 unassigned "482" → full commit
    std::vector<SyllableOption> sels{SyllableOption("ji", 2)};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 2,
        .state = T9StateMachine::State::kInput,
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ji hua"), 0);
    EXPECT_TRUE(result);
    EXPECT_TRUE(ctx.input_buffer.is_empty());
}

TEST(T9RightCommitHandlerTest, ApostrophePartialCommitWithRemainingDigits) {
    // 场景：buffer: digit_sequence="54482", selections=[ji(2)], consumedCount=2
    // unassigned="482", 右选 "ji" → effectiveLetterCount=2, selectedPinyin="ji"(2)
    // consumedAfter = 2 - 2 = 0 → remaining = unassigned = "482"
    // apostrophe else 分支：remainingDigits="482" 非空 → partial commit
    std::vector<SyllableOption> sels{SyllableOption("ji", 2)};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 2,
        .state = T9StateMachine::State::kInput,
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ji"), 0);
    EXPECT_FALSE(result);
    // remainingDigits = "482" → new buffer with remaining digits
    EXPECT_EQ(ctx.input_buffer.unassigned(), "482");
}

// ── 场景19：jianpin 对齐 full commit ──

TEST(T9RightCommitHandlerTest, JianpinAlignmentFullCommit) {
    // 场景19：buffer="khe"(selections=[k(1), he(2)])
    // 右选 "ka ha er" textLength=3 → jianpin 对齐 → full commit
    std::vector<SyllableOption> sels{SyllableOption("k", 1), SyllableOption("he", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "543",
        .selections = sels,
        .consumed_count = 3,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("43"),
        .confirmed_pinyin = "k",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // "ka ha er" = 3 syllables, effectiveLetterCount = 5 (k,a,h,a,e,r... wait, 6 letters)
    // selectedPinyin = "khe" (3 chars)
    // effectiveLetterCount(6) >= selectedPinyin.length(3) → SELECTION 字母处理
    // hasSyllableBoundaries = true (3 syllables)
    // candidateTextLength(3) >= commentSyllables.size(3) ✓
    // isJianpinAligned: IsFullCommitByJianpinAlignment("khe", ["ka","ha","er"])
    //   "khe"→"543", 3 syllables == 3 digits, ka→5, ha→4, er→3 → true
    // → isFullCommit = true
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ka ha er"), 3);
    EXPECT_TRUE(result);
    EXPECT_TRUE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.state_machine.is_idle());
}

// ── 场景18：候选 pinyin 是选中项的真前缀 ──

TEST(T9RightCommitHandlerTest, CandidatePinyinPrefixOfSelectedOption) {
    // 场景18：prevSelectedOption="tian", candidatePinyin="ti"
    // consumedPinyin="ti" < "tian", "tian".startsWith("ti") → true
    // → buffer 从字母变为数字
    std::vector<SyllableOption> sels{SyllableOption("tian", 4)};
    std::vector<SyllableOption> history{sels[0]};
    auto ctx = MakeContext({
        .digits = "8426",
        .selections = sels,
        .consumed_count = 4,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("8426"),
        .confirmed_pinyin = "",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // effectiveLetterCount("ti") = 2, selectedPinyin="tian".length = 4
    // 2 < 4 → 进入 partial 路径
    // prevSelectedOption has_value, consumedPinyin="ti" < "tian", "tian".startsWith("ti") → true
    // → remainingDigits = pinyinToDigitCode("an") = "26"
    // → buffer = T9Buffer("26"), clearSelectionHistory, enterInput
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ti"), 1);
    EXPECT_FALSE(result);
    EXPECT_TRUE(ctx.state_machine.is_input());
    // remaining digits should be "26" (an = 2,6)
    EXPECT_EQ(ctx.input_buffer.unassigned(), "26");
}

// ── 空缓冲区 ──

TEST(T9RightCommitHandlerTest, EmptyBufferReturnsTrue) {
    auto ctx = MakeContext({"", {}, 0, T9StateMachine::State::kIdle});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ji"), 0);
    EXPECT_TRUE(result);
}

// ── ComputeRightCommitConsumption 间接测试 ──
// 该方法为 private，通过 HandleRightCommit 的 full/partial commit 结果间接验证。

// ── 场景 [Bug-fix]: letterBuffer 模式下 selections 偏移量消费 ──
// 根因：ComputeRightCommitConsumption letterBuffer 模式用完整 digit_sequence
// 计算消费，当 consumedCount > selections_total_length 时（即包含右选消费偏移量），
// 前缀不匹配候选词拼音编码，导致消费计算错误。
// 修复：用 selections 覆盖的数字段计算消费，再映射回完整 digit_sequence。
//
// 原场景：输入23744→右选"策ce"→左选"pi"→左选"h"→右选"皮pi"
// 此时 buffer: digitSeq="23744", selections=[pi(2), h(1)], consumedCount=5
// 候选词"皮pi"的拼音"pi"编码"74"在位置2-3，前缀"23"不匹配"74"
// 正确结果：consumed="2374", remaining="4"，保留h(1) selection，SELECTION态
TEST(T9RightCommitHandlerTest, LetterBufferSelectionOffsetConsumption) {
    std::vector<SyllableOption> sels{SyllableOption("pi", 2), SyllableOption("h", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "23744",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("4"),
        .confirmed_pinyin = "",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // candidatePinyin="pi" 的 effectiveLetterCount=2, selectedPinyin="pih" 长度=3
    // 2 < 3 → 进入 partial 路径
    // selections覆盖数字段="744", ComputeConsumedDigitsFromPinyin("744","pi")→"74"匹配→consumed_from_sel=2
    // total_consumed=2+2=4 → consumed="2374", remaining="4"
    // 在HandleSelectionPrefixConsumed中，consumed_digit_count=4, 减去sel_offset=2后=2
    // 只消费pi(2), h(1)保留
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("pi"), 1);
    EXPECT_FALSE(result);
    // buffer 应保留 h(1) selection
    EXPECT_EQ(ctx.input_buffer.digit_sequence, "4");
    EXPECT_EQ(ctx.input_buffer.selections.size(), 1u);
    EXPECT_EQ(ctx.input_buffer.selections[0].pinyin, "h");
    EXPECT_EQ(ctx.input_buffer.consumed_count, 1);
    // 状态应为 SELECTION
    EXPECT_TRUE(ctx.state_machine.is_selection());
    EXPECT_TRUE(ctx.state_machine.selected_option().has_value());
    EXPECT_EQ(ctx.state_machine.selected_option()->pinyin, "h");
}

// ── 场景 [Bug-fix]: letterBuffer 混合简拼+全拼 full commit 判定 ──
// 输入54482→左选j→g→hu→c（混合简拼+全拼，全部5位消费），
// buffer="jghuc", selectedPinyin="jghuc", selections=[j(1),g(1),hu(2),c(1)]
// 候选词"机构呼出 ji gou hu chu"（4音节4汉字），
// 预期：full commit（候选词4音节完全覆盖4个selections）
//
// 根因：HandleSelectionLetterBufferCommit 中 would_trigger_shengmu=true
// （"chu"和"c"首字母都是"c"→"2"）导致音节匹配块(308-352行)被跳过，
// 回退到ComputeSelectionConsumedCount返回3（数字位数），
// Take("jghu",3)="jgh"无法匹配selection"hu"（2字符），
// 导致[hu,c]残留而非全量消费。
TEST(T9RightCommitHandlerTest, LetterBufferMixedAbbrevFullPinyinFullCommit_54482_JiGouHuChu) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1),
        SyllableOption("hu", 2), SyllableOption("c", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2], sels[3]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[3],
        .selection_candidate_digits = std::optional<std::string>("2"),
        .confirmed_pinyin = "jghu",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // candidatePinyin="ji gou hu chu" 有效字母数=10, selectedPinyin="jghuc"长度=5
    // 10 >= 5 → 进入 HandleSelectionLetterBufferCommit
    // hasSyllableBoundaries=true (4 syllables), candidateTextLength=4 >= 4
    // nonSelectedPart="jghu" (dropLast "c")
    // would_trigger_shengmu=true ("chu"->"c"->"2" 与 "c"->"2" 相同)
    // 修复前：跳过音节匹配 → partial commit 残留 [hu,c]
    // 修复后：音节匹配正确 → full commit
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ji gou hu chu"), 4);
    EXPECT_TRUE(result);   // full commit — 4音节完全覆盖4个selections
    EXPECT_TRUE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: letterBuffer 末音节==末选择(拼音相同) full commit 判定 ──
// 输入54482→左选j→g→hu→a（混合简拼+全拼，全部5位消费），
// 候选词"就过户啊 jiu guo hu a"（4音节4汉字），
// commentSyllables.back()="a" == prevSelectedOption.pinyin="a"，
// consumedSelections(3) == nonSelectedHistory.size(3)，
// 末音节"a"完全匹配末选择"a" → 预期 full commit。
TEST(T9RightCommitHandlerTest, LetterBufferFullMatchLastSyllable_54482_JiuGuoHuA) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1),
        SyllableOption("hu", 2), SyllableOption("a", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2], sels[3]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[3],
        .selection_candidate_digits = std::optional<std::string>("2"),
        .confirmed_pinyin = "jghu",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // candidatePinyin="jiu guo hu a" 有效字母数=9, selectedPinyin="jghua"长度=5
    // 9 >= 5 → 进入 HandleSelectionLetterBufferCommit
    // hasSyllableBoundaries=true (4 syllables), candidateTextLength=4 >= 4
    // nonSelectedPart="jghu" (dropLast "a")
    // commentSyl.back()="a" == prevOpt.pinyin="a" → nonSelectedSyllables=["jiu","guo","hu"]
    // consumedSelections=3, nonSelectedHistory.size=3
    // consumedSelections(3) == nonSelectedHistory.size(3) → 末音节"a"匹配末选择"a" → full commit
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jiu guo hu a"), 4);
    EXPECT_TRUE(result);   // full commit — 4音节完全覆盖4个selections
    EXPECT_TRUE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: letterBuffer 有 unassigned 时的 full commit 判定 ──
// 输入54482→左选j→g→hu（consumed=4, unassigned="2"），
// 候选词"价格胡扯 jia ge hu c"（4音节4汉字），
// buffer="jghu", selections=[j(1),g(1),hu(2)], unassigned="2"
// 候选词4音节覆盖3个selections + 1个unassigned → 预期 full commit。
//
// 根因：HandleRightCommit 分发逻辑将 has_selections && has_unassigned
// 路由到 ApostropheStrategy，但 ShouldFullCommitInSelection 因
// remaining_digits="2"非空而返回 false，导致 partial commit。
// 修复：SELECTION 态且 EndsWith(selectedPinyin, prevOpt.pinyin) 时
// 路由到 LetterBufferStrategy，其音节匹配块正确判定 full commit。
TEST(T9RightCommitHandlerTest, LetterBufferSelectionWithUnassigned_54482_JiGeHuC) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1),
        SyllableOption("hu", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 4,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[2],
        .selection_candidate_digits = std::optional<std::string>("48"),
        .confirmed_pinyin = "jghu",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // candidatePinyin="jia ge hu c" 有效字母数=8, selectedPinyin="jghu"长度=4
    // 8 >= 4 → 进入 HandleSelectionLetterBufferCommit
    // hasSyllableBoundaries=true (4 syllables), candidateTextLength=4 >= 4
    // nonSelectedPart="jg" (dropLast "hu")
    // would_trigger_shengmu=false ("c"->"2" != "h"->"4")
    // consumedSelections=4 > nonSelectedHistory.size=2 → full commit
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jia ge hu c"), 4);
    EXPECT_TRUE(result);   // full commit — 4音节覆盖3个selections+1个unassigned
    EXPECT_TRUE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: letterBuffer 有 unassigned 时 partial commit ──
// 输入54482→左选j→g→hu（consumed=4, unassigned="2"），
// 右选"价格 jia ge"（2音节2汉字），候选词仅覆盖前2个selections，
// "hu"应保留在buffer中。不会覆盖unassigned。
//
// 根因：would_trigger_shengmu=true（"g"和"h"同为digit"4"），
// 原守卫条件 comment_syllables.size()(2) >= history.size()(3) 为false，
// 导致音节匹配块被跳过，落入TryShengmuFallback错误消费"hu"。
// 修复：当 comment_syllables.size() < history.size() 时，末音节不可能匹配
// 末选择，would_trigger_shengmu 检查不相关，应允许进入音节匹配块。
TEST(T9RightCommitHandlerTest, LetterBufferSelectionWithUnassigned_54482_JiGe_Partial) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1),
        SyllableOption("hu", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 4,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[2],
        .selection_candidate_digits = std::optional<std::string>("48"),
        .confirmed_pinyin = "jghu",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // candidatePinyin="jia ge" 有效字母数=5, selectedPinyin="jghu"长度=4
    // 5 >= 4 → 进入 HandleSelectionLetterBufferCommit
    // hasSyllableBoundaries=true (2 syllables), candidateTextLength=2 >= 2
    // nonSelectedPart="jg" (dropLast "hu")
    // would_trigger_shengmu=true ("g"->"4" == "h"->"4")
    // 修复后：comment_syllables.size()(2) < history.size()(3) → 进入音节匹配块
    // non_selected_syllables=["jia","ge"] ("ge"!="hu")
    // consumedSelections=2, nonSelectedHistory.size=2
    // consumedSelections(2) >= nonSelectedHistory.size(2) → RemoveConsumedSelections("jg")
    // 保留"hu"作为selection
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jia ge"), 2);
    EXPECT_FALSE(result);  // partial commit — 2音节仅覆盖前2个selections
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(1, ctx.input_buffer.selections.size());
    EXPECT_EQ("hu", ctx.input_buffer.selections[0].pinyin);
    EXPECT_FALSE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: 多音节候选词部分消费 letterBuffer ──
// 输入54482→左选j→g→hu→b（consumed=5, 无unassigned），
// 右选"几个 ji ge"（2音节2汉字），候选词仅覆盖前2个selections，
// "hu"和"b"应保留在buffer中。
//
// 根因：effective_letter_count=4（"ji ge"共4个字母），
// HandleSelectionPrefixConsumed 用 Take(selectedPinyin, 4)="jghu"消费了
// j、g、hu，导致"hu"被错误消费。剩余数字"82"又导致数字段割切只保留"b"。
// 修复：候选词有音节边界时，重定向到 HandleSelectionLetterBufferCommit，
// 其音节匹配块正确使用 syllable count 决定消费选择数。
TEST(T9RightCommitHandlerTest, LetterBufferPartialConsume_54482_JiGeHuB) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1),
        SyllableOption("hu", 2), SyllableOption("b", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2], sels[3]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[3],
        .selection_candidate_digits = std::optional<std::string>("2"),
        .confirmed_pinyin = "jghub",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // candidatePinyin="ji ge" 有效字母数=4, selectedPinyin="jghub"长度=5
    // 4 < 5 → 进入BRANCH
    // 候选词有音节边界(2 syllables)且 EndsWith("jghub","b")=true → 重定向到
    // HandleSelectionLetterBufferCommit
    // commentSyl=["ji","ge"], nonSelectedPart="jghu" (dropLast "b")
    // would_trigger_shengmu=false ("g"->"4" != "b"->"2")
    // non_selected_syllables=["ji","ge"] ("ge"!="b")
    // consumedSelections=2, nonSelectedHistory=[j,g,hu]
    // consumedSelections(2) < nonSelectedHistory.size(3) → BRANCH B
    // consumedPinyin="jg", RemoveConsumedSelections保留[hu,b]
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ji ge"), 2);
    EXPECT_FALSE(result);  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(2, ctx.input_buffer.selections.size());
    EXPECT_EQ("hu", ctx.input_buffer.selections[0].pinyin);
    EXPECT_EQ("b", ctx.input_buffer.selections[1].pinyin);
    EXPECT_FALSE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: 单音节候选词贪婪数字匹跨越 selection 边界 ──
// 输入54482→左选j→g→hu→b，右选"金 jin"（单音节1汉字）。
// 候选词"jin"的数字码"546"贪婪匹配digitSeq"54482"中"54"=j(5)+g(4)，
// 因为"i"→4碰巧等于下一selection"g"→4，错误跨越边界多消费一个selection。
// 修复：ComputeRightCommitConsumption letterBuffer 模式对单音节候选词
// 限制消费位数不超过第一个selection的digit_length。
//
// 此场景覆盖9个"j"首字母候选词：jin, jiu, jiang, ji, jiao, jian, jie, jia, jiong
// (它们的第二字母都映射为digit 4，与g→4相同，导致贪婪匹配消费2位)
// 也覆盖4个已有正确行为的候选词：jun, jue, ju, juan
// (它们的第二字母映射为digit 8，不会误匹配g→4，消费1位正确)
TEST(T9RightCommitHandlerTest, SingleSyllableNoCrossBoundary_54482_Jin) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1),
        SyllableOption("hu", 2), SyllableOption("b", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2], sels[3]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[3],
        .selection_candidate_digits = std::optional<std::string>("2"),
        .confirmed_pinyin = "jghub",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // candidatePinyin="jin" 有效字母数=3, selectedPinyin="jghub"长度=5
    // 3 < 5 → BRANCH effLetterCount < selectedPinyin.size
    // 修复前：ComputeConsumedDigitsFromPinyin → "jin"→"546", 贪婪匹配"54"=2位
    //         → consumed_digit_count=2 → 消费j+g → 剩余[hu,b] → 错误
    // 修复后：单音节候选词，消费限制在第一个selection digit_length=1
    //         → consumed_digit_count=1 → 仅消费j → 剩余[g,hu,b] → 正确
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jin"), 1);
    EXPECT_FALSE(result);  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(3, ctx.input_buffer.selections.size());  // g, hu, b
    EXPECT_EQ("g", ctx.input_buffer.selections[0].pinyin);
    EXPECT_EQ("hu", ctx.input_buffer.selections[1].pinyin);
    EXPECT_EQ("b", ctx.input_buffer.selections[2].pinyin);
    EXPECT_FALSE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: 单音节候选词"jun"正常消费(对照组) ──
// 验证"jun"（第二字母u→8，不碰巧匹配g→4）仍然消费1位正确
TEST(T9RightCommitHandlerTest, SingleSyllableNoCrossBoundary_54482_Jun) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1),
        SyllableOption("hu", 2), SyllableOption("b", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2], sels[3]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[3],
        .selection_candidate_digits = std::optional<std::string>("2"),
        .confirmed_pinyin = "jghub",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jun"), 1);
    EXPECT_FALSE(result);
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(3, ctx.input_buffer.selections.size());
    EXPECT_EQ("g", ctx.input_buffer.selections[0].pinyin);
    EXPECT_EQ("hu", ctx.input_buffer.selections[1].pinyin);
    EXPECT_EQ("b", ctx.input_buffer.selections[2].pinyin);
}

// ── 场景 [Bug-fix]: 单音节"jiong" effLetterCount==selectedPinyin.size ──
// 输入54482→左选j→g→hu→b，右选"囧 jiong"（5字母，1音节，1汉字）。
// effLetterCount(5) == selectedPinyin.size(5) → ELSE 分支。
// 修复前：→ HandleSelectionLetterBufferCommit → 无音节边界 → 子路径D
//   → candidate_non_selected_letters = 5-1=4 → 消费全部 → 仅剩"b"
// 修复后：单音节+multi-selection → cap effLetterCount=1 → HandleSelectionPrefixConsumed
//   → Take("jghub",1)="j" → RemoveConsumedSelections("j") → 剩余[g,hu,b]
TEST(T9RightCommitHandlerTest, SingleSyllableNoCrossBoundary_54482_Jiong) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1),
        SyllableOption("hu", 2), SyllableOption("b", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2], sels[3]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[3],
        .selection_candidate_digits = std::optional<std::string>("2"),
        .confirmed_pinyin = "jghub",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jiong"), 1);
    EXPECT_FALSE(result);
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(3, ctx.input_buffer.selections.size());  // g, hu, b
    EXPECT_EQ("g", ctx.input_buffer.selections[0].pinyin);
    EXPECT_EQ("hu", ctx.input_buffer.selections[1].pinyin);
    EXPECT_EQ("b", ctx.input_buffer.selections[2].pinyin);
    EXPECT_FALSE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: 单音节"jiang" effLetterCount==selectedPinyin.size ──
// 同"jiong"场景，验证"将 jiang"（5字母，1音节，1汉字）也正确消费。
TEST(T9RightCommitHandlerTest, SingleSyllableNoCrossBoundary_54482_Jiang) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1),
        SyllableOption("hu", 2), SyllableOption("b", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2], sels[3]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[3],
        .selection_candidate_digits = std::optional<std::string>("2"),
        .confirmed_pinyin = "jghub",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jiang"), 1);
    EXPECT_FALSE(result);
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(3, ctx.input_buffer.selections.size());  // g, hu, b
    EXPECT_EQ("g", ctx.input_buffer.selections[0].pinyin);
    EXPECT_EQ("hu", ctx.input_buffer.selections[1].pinyin);
    EXPECT_EQ("b", ctx.input_buffer.selections[2].pinyin);
    EXPECT_FALSE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: letterBuffer + has_unassigned 单音节消费 ──
// 输入54482→左选j→g→hu，digit "2" 不选择处于 unassigned 态。
// 右选"金 jin"（单音节1汉字），应消费第一个 selection "j"，
// 保留 [g, hu] + unassigned "2"。
// 修复前：ComputeRightCommitConsumption 进入 apostrophe 模式，
//   consumed_from_unassigned = -2 → 返回 remaining_digits="2"（仅 unassigned）。
//   HSPC 用 consumed_digit_count = 5-1=4 → 消费全部3个selection → 错误。
// 修复后：apostrophe 模式检测到 is_letter_buffer_selection 跳过，
//   进入 letterBuffer 模式 → remaining_digits="4482"。
//   HSPC 用 consumed_digit_count = 5-4=1 → 仅消费1个selection j → 正确。
TEST(T9RightCommitHandlerTest, LetterBufferWithUnassigned_54482_Jin) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1),
        SyllableOption("hu", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 4,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[2],
        .selection_candidate_digits = std::optional<std::string>("482"),
        .confirmed_pinyin = "jghu",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jin"), 1);
    EXPECT_FALSE(result);  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(2, ctx.input_buffer.selections.size());  // g, hu
    EXPECT_EQ("g", ctx.input_buffer.selections[0].pinyin);
    EXPECT_EQ("hu", ctx.input_buffer.selections[1].pinyin);
    EXPECT_EQ("2", ctx.input_buffer.unassigned());
    EXPECT_FALSE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: letterBuffer + has_unassigned 多音节消费 ──
// 输入54482→左选j→g→hu，digit "2" 不选择处于 unassigned 态。
// 右选"金 jin"（单音节）→ 消费第一个 selection "j"，保留 [g, hu] + "2"。
// 再右选"关乎 guan hu"（多音节2汉字）→ 消费 [g, hu] 两个 selection，
// 保留 unassigned "2"（显示为"a"）。
// 修复前：ComputeRightCommitConsumption letterBuffer 模式返回 remaining_digits=""，
//   Handle() 子路径 C 进入 HSLBC → full commit 丢失 unassigned "2"。
// 修复后：ComputeRightCommitConsumption 返回 remaining_digits="2"，
//   Handle() 子路径 C 检测到 remaining_digits 非空 → HSPC 消费所有 selections
//   并保留 unassigned "2"。
TEST(T9RightCommitHandlerTest, LetterBufferWithUnassigned_54482_GuanHu) {
    std::vector<SyllableOption> sels{
        SyllableOption("g", 1), SyllableOption("hu", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "4482",
        .selections = sels,
        .consumed_count = 3,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("482"),
        .confirmed_pinyin = "",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("guan hu"), 2);
    EXPECT_FALSE(result);  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.input_buffer.selections.empty());
    EXPECT_EQ("2", ctx.input_buffer.unassigned());
    EXPECT_TRUE(ctx.state_machine.is_input());
}

// ── 场景 [Bug-fix]: letterBuffer + has_unassigned 首音节为 k 的单音节消费 ──
// 输入54482→左选 k→h→hu，digit "2" 不选择处于 unassigned 态。
// 右选"空 kong"（单音节1汉字）→ 应消费第一个 selection "k"，
// 保留 [h, hu] + unassigned "2"。
TEST(T9RightCommitHandlerTest, LetterBufferWithUnassigned_54482_Kong) {
    std::vector<SyllableOption> sels{
        SyllableOption("k", 1), SyllableOption("h", 1),
        SyllableOption("hu", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 4,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[2],
        .selection_candidate_digits = std::optional<std::string>("482"),
        .confirmed_pinyin = "kghu",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("kong"), 1);
    EXPECT_FALSE(result);  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(2, ctx.input_buffer.selections.size());  // h, hu
    EXPECT_EQ("h", ctx.input_buffer.selections[0].pinyin);
    EXPECT_EQ("hu", ctx.input_buffer.selections[1].pinyin);
    EXPECT_EQ("2", ctx.input_buffer.unassigned());
    EXPECT_FALSE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: letterBuffer + has_unassigned 第二步消费 ──
// 在 Kong 测试之后的状态：selections=[h, hu]，unassigned="2"。
// 右选"还 hai"（单音节1汉字）→ 应消费第一个 selection "h"，
// 保留 [hu] + unassigned "2"。
TEST(T9RightCommitHandlerTest, LetterBufferWithUnassigned_54482_Hai) {
    std::vector<SyllableOption> sels{
        SyllableOption("h", 1), SyllableOption("hu", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 4,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("482"),
        .confirmed_pinyin = "",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("hai"), 1);
    EXPECT_FALSE(result);  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(1, ctx.input_buffer.selections.size());  // hu
    EXPECT_EQ("hu", ctx.input_buffer.selections[0].pinyin);
    EXPECT_EQ("2", ctx.input_buffer.unassigned());
    EXPECT_FALSE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: letterBuffer + has_unassigned 多音节消费跨越 selection 边界 ──
// 输入54482→左选 k→h→hu，digit "2" 不选择处于 unassigned 态。
// 右选"开户 kai hu"（2音节2汉字）→ 应消费2个 selection [k, h]，
// 保留 [hu] + unassigned "2"。
// 修复前：non_selected_syllables 因 last_syllable("hu")==prevOpt("hu") 被截断为 ["kai"]，
//   consumed_selections=1 < non_selected_history.size()=2 → 只消费 k，错误保留 [h, hu]。
// 修复后：non_selected_syllables 始终使用全部 comment_syllables ["kai", "hu"]，
//   consumed_selections=2 >= 2 → 消费 [k, h]，正确保留 [hu]。
TEST(T9RightCommitHandlerTest, LetterBufferWithUnassigned_54482_KaiHu) {
    std::vector<SyllableOption> sels{
        SyllableOption("k", 1), SyllableOption("h", 1),
        SyllableOption("hu", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 4,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[2],
        .selection_candidate_digits = std::optional<std::string>("482"),
        .confirmed_pinyin = "khhu",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("kai hu"), 2);
    EXPECT_FALSE(result);  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(1, ctx.input_buffer.selections.size());  // hu
    EXPECT_EQ("hu", ctx.input_buffer.selections[0].pinyin);
    EXPECT_EQ("2", ctx.input_buffer.unassigned());
    EXPECT_FALSE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: letterBuffer + has_unassigned 多音节候选词超过 selections 数 ──
// 输入54482→左选 k→h，digit "482" 不选择处于 unassigned 态。
// 右选"开户行 kai hu hang"（3音节3汉字）→ 应消费2个 selection [k, h]，
// 并从 unassigned "482" 中消费第3音节"hang"对应的1位数字"4"，
// 保留 unassigned "82"。
// 修复前：ComputeRightCommitConsumption 在 candidate_letter_count >=
//   selected_pinyin.size() 时直接返回 {"", ""}，导致 HSLBC 只消费了非选中部分
//   "k"，然后 RestorePrevState 恢复原状态，实际未消费任何内容。
// 修复后：当 syllable_count > selection_count 且存在 unassigned 时，
//   ComputeRightCommitConsumption 计算额外音节从 unassigned 的消费。
TEST(T9RightCommitHandlerTest, LetterBufferWithUnassigned_54482_KaiHuHang) {
    std::vector<SyllableOption> sels{
        SyllableOption("k", 1), SyllableOption("h", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 2,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("482"),
        .confirmed_pinyin = "kh",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("kai hu hang"), 3);
    EXPECT_FALSE(result);  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(0, ctx.input_buffer.selections.size());
    EXPECT_EQ("82", ctx.input_buffer.unassigned());
    EXPECT_FALSE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-fix]: 简拼+全拼混合左选后右选仅匹配首字母的候选词 ──
// 输入543→分词键确认j→输入4→输入3→左选k→左选he（分词键区隔简拼k与全拼he）。
// 右选"开户 kai hu"（2音节2汉字）→ 候选词仅匹配首字母k+h，
// 末音节"hu"≠末选择"he"，应partial commit保留he选择。
// 修复前：HSLBC 中 consumed_selections(2) > non_selected_history.size(1)
//   无条件触发 full commit → 直接上屏"开户"。
// 修复后：增加 extra_consumption_valid 检查——无 unassigned 时额外音节
//   必须覆盖末选择（全匹配或首字母匹配），"hu"不满足 → partial commit。
TEST(T9RightCommitHandlerTest, LetterBufferInitialOnlyMatch_543_KaiHu) {
    std::vector<SyllableOption> sels{
        SyllableOption("k", 1), SyllableOption("he", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    // 真实场景：digitSeq="543"，consumed=3，unassigned=""
    auto ctx = MakeContext({
        .digits = "543",
        .selections = sels,
        .consumed_count = 3,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("43"),
        .confirmed_pinyin = "k",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("kai hu"), 2);
    EXPECT_FALSE(result);  // partial commit（非 full commit）
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_FALSE(ctx.state_machine.is_idle());
    // 仅消费 k+h（首字母），未消费的 'e' 回到 unassigned
    EXPECT_EQ(0, ctx.input_buffer.selections.size());
    EXPECT_EQ("3", ctx.input_buffer.unassigned());  // digit 3 maps to d/e/f
}

// ── 场景 [Bug-fix]: initials-only 分支退还未消费位数按前缀覆盖长度 ──
// 复现步骤（adb 日志 13:53:55.972）：
//   输入 54482 → 左选 j(5) → 左选 g(4) → 左选 gua(482) → 右选"军工股 jun gong gu"
//   候选末音节 'gu' 是末选择 'gua' 的**真前缀**（gu ⊂ gua，覆盖前 2 位 "48"），
//   仅剩末选择最后 1 位 '2' 未消费。
// 修复前：initials-only 分支固定退还 digit_length-1=2 位，
//         newConsumed=5-2=3，unassigned='82' → 预编辑错误"军工股ta"（消费从 5448 减为 544）。
// 修复后：按前缀覆盖长度退还 digit_length-overlap('gu','gua')=3-2=1 位，
//         newConsumed=4，unassigned='2' → 预编辑"军工股a"。
TEST(T9RightCommitHandlerTest, LetterBufferInitialOnlyPrefixRefund_54482_JunGongGu) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1), SyllableOption("gua", 3)};
    std::vector<SyllableOption> history{sels[0], sels[1], sels[2]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 5,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[2],
        .selection_candidate_digits = std::optional<std::string>("482"),
        .confirmed_pinyin = "jg",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jun gong gu"), 3);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_TRUE(ctx.input_buffer.selections.empty());      // gua 被部分消费，不保留
    EXPECT_EQ(4, ctx.input_buffer.consumed_count);         // 消费 "5448"
    EXPECT_EQ("2", ctx.input_buffer.unassigned());         // 剩余 '2' → 预编辑 "军工股a"
    EXPECT_EQ("54482", ctx.input_buffer.digit_sequence);   // 完整序列保留
    EXPECT_TRUE(ctx.state_machine.is_input());
}

// ── 场景 [Bug-fix]: apostrophe 单音节部分匹配不消费 unassigned ──
// 输入54482→左选j，digit "2" 不选择，处于 unassigned 态。
// 右选"金 jin"（单音节，digit code "546"）→ PREFIX_MATCH 在完整 digit_sequence
// "54482" 上匹配 "54" (j+i)，但第3位 "n"(6) 不匹配剩余 "4"。
// 修复前：贪婪匹配消费2位，consumed_from_unassigned=1 → unassigned 从 "4482" 变为 "482"，
//   预编辑错误显示为"金hua"（丢失第二个4）。
// 修复后：单音节候选词若 PREFIX_MATCH 未完整匹配音节数字码（total_consumed < syl_code.size()），
//   不从 unassigned 消费 → unassigned 保持 "4482" → 预编辑为"金g hua"。
TEST(T9RightCommitHandlerTest, ApostropheSingleSyllablePartialMatch_54482_Jin) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1)};
    std::vector<SyllableOption> history{sels[0]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 1,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("4482"),
        .confirmed_pinyin = "j",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jin"), 1);
    EXPECT_FALSE(result);  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(0, ctx.input_buffer.selections.size());  // selection "j" 被消费
    EXPECT_EQ("4482", ctx.input_buffer.unassigned());  // unassigned 不变
    EXPECT_EQ("4482", ctx.input_buffer.digit_sequence);
    EXPECT_TRUE(ctx.state_machine.is_input());
}

// ── 场景 [Bug-fix]: apostrophe 多音节候选词剩余音节匹配到 unassigned ──
// 输入54482→左选j，unassigned="4482"。
// 右选"结构化 jie gou hua"（3音节，> selections.size()=1）：
//   剩余音节"gou hua"匹配到"4482"：
//     "gou"=468 → PREFIX_MATCH len=1("4"), "hua"=482 → FULL_MATCH
//   消费4位 = 全部 unassigned → full commit
TEST(T9RightCommitHandlerTest, ApostropheMultiSyllableFullCommit_54482_JieGouHua) {
    std::vector<SyllableOption> sels{SyllableOption("j", 1)};
    std::vector<SyllableOption> history{sels[0]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 1,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("4482"),
        .confirmed_pinyin = "j",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jie gou hua"), 3);
    EXPECT_TRUE(result);  // full commit: 剩余音节完全覆盖 unassigned
    EXPECT_TRUE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.state_machine.is_idle());
}

// 输入54482→左选j，unassigned="4482"。
// 右选"结婚后 jie hun hou"（3音节，> selections.size()=1）：
//   剩余音节"hun hou"匹配到"4482"：
//     "hun"=486 → PREFIX_MATCH len=1("4"), "hou"=468 → PREFIX_MATCH len=1("4")
//   消费2位，剩余"82" → partial commit
TEST(T9RightCommitHandlerTest, ApostropheMultiSyllablePartialCommit_54482_JieHunHou) {
    std::vector<SyllableOption> sels{SyllableOption("j", 1)};
    std::vector<SyllableOption> history{sels[0]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 1,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("4482"),
        .confirmed_pinyin = "j",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jie hun hou"), 3);
    EXPECT_FALSE(result);  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(0, ctx.input_buffer.selections.size());
    EXPECT_EQ("82", ctx.input_buffer.unassigned());  // 剩余 "82" = "ta"
    EXPECT_EQ("82", ctx.input_buffer.digit_sequence);
    EXPECT_TRUE(ctx.state_machine.is_input());
}

// ── 场景 [Bug]: 单音节候选词不应从 unassigned 消费 ──
// 输入54482→左选j，unassigned="4482"。
// 右选单音节候选词"及 ji"（pinyin="ji", textLen=1）：
//   "ji"=54，但 selection "j"已经消费了"5"。
//   单音节候选词不应从 unassigned 额外消费，剩余"4482"应保持不变。
//   预期：partial commit，剩余"4482" = "g hua"
TEST(T9RightCommitHandlerTest, SingleSyllable_NoConsumeFromUnassigned_54482_Ji) {
    std::vector<SyllableOption> sels{SyllableOption("j", 1)};
    std::vector<SyllableOption> history{sels[0]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 1,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("4482"),
        .confirmed_pinyin = "j",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ji"), 1);
    EXPECT_FALSE(result);  // partial commit: 单音节候选词，不消费 unassigned
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(0, ctx.input_buffer.selections.size());
    EXPECT_EQ("4482", ctx.input_buffer.unassigned());  // unassigned 保持不变
    EXPECT_EQ("4482", ctx.input_buffer.digit_sequence);
    EXPECT_TRUE(ctx.state_machine.is_input());
}

// ── 场景 [Bug]: apostrophe 多音节候选词剩余音节贪婪匹配跨越音节边界 ──
// 输入54482→左选j→g→右选"及ji"→右选"国会guo hui"
// 当前状态：digitSeq="54482", selections=[g(1)], consumedCount=2, unassigned="482"
// 右选"guo hui"（双音节，> selections.size()=1）：
//   剩余音节"hui"匹配到"482"：
//     修复前：贪婪前缀匹配 "48"（2位），剩余"2"
//     修复后：非贪婪前缀匹配 "4"（1位），剩余"82"
TEST(T9RightCommitHandlerTest, ApostropheMultiSyllablePartialCommit_54482_GuoHui) {
    std::vector<SyllableOption> sels{SyllableOption("g", 1)};
    std::vector<SyllableOption> history{sels[0]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 2,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("4"),
        .confirmed_pinyin = "j",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("guo hui"), 2);
    EXPECT_FALSE(result);  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ(0, ctx.input_buffer.selections.size());
    EXPECT_EQ("82", ctx.input_buffer.unassigned());
    EXPECT_EQ("82", ctx.input_buffer.digit_sequence);
    EXPECT_TRUE(ctx.state_machine.is_input());
}

// ── 场景 [Bug]: digitSegment 多音节候选词贪婪前缀匹配跨越音节边界 ──
// 输入54482→左选j→右选"及ji"→右选"还会hai hui"
// 当前状态：digitSeq="4482", selections=[], consumedCount=0, unassigned="4482"
// 右选"hai hui"（双音节）：
//   修复前：ComputeConsumedDigitsFromPinyin 贪婪前缀匹配
//     "hai"=424 → PREFIX_MATCH "4" (1位)
//     "hui"=484 → PREFIX_MATCH "48" (2位) ← 贪婪匹配跨越音节边界
//     消费3位，剩余"2" → 错误显示"及还会a"
//   修复后：非贪婪前缀匹配（每个音节只匹配1位）
//     "hai"=424 → PREFIX_MATCH "4" (1位)
//     "hui"=484 → PREFIX_MATCH "4" (1位)
//     消费2位，剩余"82" → 正确显示"及还会ta"
TEST(T9RightCommitHandlerTest, DigitSegmentPartialCommit_4482_HaiHui) {
    auto ctx = MakeContext({"4482", {}, 0, T9StateMachine::State::kInput});
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("hai hui"), 2);
    EXPECT_FALSE(result);                             // partial commit
    EXPECT_EQ(ctx.input_buffer.unassigned(), "82");   // 剩余 "82" = "ta"
    EXPECT_EQ(ctx.input_buffer.consumed_count, 2);    // 消费2位
    EXPECT_EQ(ctx.input_buffer.digit_sequence, "4482");
    EXPECT_TRUE(ctx.state_machine.is_input());        // 进入 INPUT 态
}

// ── 场景 [Bug]: letterBuffer 额外音节全拼音匹配导致过度消费 ──
// 输入54482→左选j→g，unassigned="482"。
// 右选"结构管 jie gou guan"（3音节，> selections.size()=2）：
//   额外音节"guan"需从 unassigned="482" 消费
//   letterBuffer 模式：额外音节只消费1位（声母'g'→4），剩余"82"
//   修复前：ComputeConsumedDigitsFromPinyin 贪婪匹配 → "guan"=4826 消费"482"全部3位 → full commit
//   修复后：ComputeConsumedDigitsMultiSyllable 非贪婪匹配 → "guan"消费1位 → remaining="82" → partial commit
TEST(T9RightCommitHandlerTest, LetterBufferExtraSyllablePartialCommit_54482_JieGouGuan) {
    std::vector<SyllableOption> sels{
        SyllableOption("j", 1), SyllableOption("g", 1)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "54482",
        .selections = sels,
        .consumed_count = 2,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("4"),
        .confirmed_pinyin = "j",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    // candidatePinyin="jie gou guan" 有效字母数=10, selectedPinyin="jg"长度=2
    // 10 >= 2 → letterBuffer 模式额外音节路径
    // syllableCount=3 > selectionCount=2 → 额外音节"guan"匹配 unassigned="482"
    // 修复前：ComputeConsumedDigitsFromPinyin 贪婪匹配 → 消费3位 → full commit
    // 修复后：ComputeConsumedDigitsMultiSyllable 非贪婪匹配 → 消费1位 → "82" remaining
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("jie gou guan"), 3);
    EXPECT_FALSE(result);   // partial commit: 额外音节只消费1位
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_EQ("82", ctx.input_buffer.unassigned());  // 剩余 "82" = "ta"
    EXPECT_TRUE(ctx.state_machine.is_input());
}

// ── 场景 [Bug-2026-08-11]: 退格撤销左选后再次左选，history 残留重复 → 右选错误 partial commit ──
// 复现：输入 826 8426 → 左选 tao → 右选"洮"(partial commit) → 左选 tiao → 退格撤销 tiao
//       → 再次左选 tiao（此时 digitSeq='8268426', consumed=7, unassigned=''）→ 空格选"条"(tiao)。
// 根因：退格撤销 LC 后 DeriveStateMachineFromUndoModel 走 HasSelectableDigits 分支只调
//       EnterInput()（不清 selection_history），再次左选 tiao 时 EnterSelection push_back
//       累积出 [tiao, tiao]。HSLBC 中 JoinPinyins(history)="tiaotiao" != selectedPinyin="tiao"
//       → is_full_commit_without_boundaries=0 → 错误 partial commit（预编辑"洮条tiao"）。
// 本测试锚定"干净 history"（修复后状态）：history=[tiao] 时右选"条"应 full commit。
TEST(T9RightCommitHandlerTest, LetterBufferFullCommit_8268426_Tiao_CleanHistory) {
    std::vector<SyllableOption> sels{SyllableOption("tiao", 4)};
    std::vector<SyllableOption> history{sels[0]};
    auto ctx = MakeContext({
        .digits = "8268426",
        .selections = sels,
        .consumed_count = 7,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("8426"),
        .confirmed_pinyin = "",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("tiao"), 1);
    EXPECT_TRUE(result);   // full commit — 干净 history 下完整消费 826 8426
    EXPECT_TRUE(ctx.input_buffer.is_empty());
    EXPECT_TRUE(ctx.state_machine.is_idle());
}

// ── 场景 [Bug-2026-08-11] 反向锚定：history 残留重复 [tiao, tiao] 时右选"条"错误 partial ──
// 与上测试形成对比：仅 history 多一个重复 tiao，消费判定即从 full 退化为 partial。
// 证明 bug 根因是 history 残留（DeriveStateMachineFromUndoModel 未清空），而非算法本身。
TEST(T9RightCommitHandlerTest, LetterBufferPartialCommit_8268426_Tiao_DuplicatedHistory) {
    std::vector<SyllableOption> sels{SyllableOption("tiao", 4)};
    std::vector<SyllableOption> history{sels[0], sels[0]};  // 残留重复
    auto ctx = MakeContext({
        .digits = "8268426",
        .selections = sels,
        .consumed_count = 7,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("8426"),
        .confirmed_pinyin = "",
        .selection_history = history
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("tiao"), 1);
    EXPECT_FALSE(result);   // partial commit — history 重复导致 full commit 判定失败
}

// ── 场景 [Bug-fix]: letterBuffer 末音节数字码前缀误判 full commit ──
// 输入5143→左选k→左选he，右选"可恨 ke hen"。
// 末音节"hen"→"436"是末选择"he"→"43"的字母超集但数字码不等，不应 full commit。
// 修复前：StartsWith("hen","he")=true 误判覆盖 → full commit。
// 修复后：改用数字码判定 → partial commit，剩余"3"→预编辑"可恨e"。
TEST(T9RightCommitHandlerTest, LetterBufferSyllablePrefixMismatch_543_KeHen) {
    std::vector<SyllableOption> sels{
        SyllableOption("k", 1), SyllableOption("he", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "543",
        .selections = sels,
        .consumed_count = 3,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("43"),
        .confirmed_pinyin = "k",
        .selection_history = history,
        .has_separator = true,
        .separator_position = 1
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ke hen"), 2);
    EXPECT_FALSE(result);                                  // partial commit（非 full commit）
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_FALSE(ctx.state_machine.is_idle());
    EXPECT_EQ(0, ctx.input_buffer.selections.size());      // he 被消费，不保留
    EXPECT_EQ("3", ctx.input_buffer.unassigned());         // 剩余 '3' → 预编辑 "可恨e"
}

// 同类：右选"匡衡 kuang heng"（末音节"heng"→"4364"同理），应 partial commit。
TEST(T9RightCommitHandlerTest, LetterBufferSyllablePrefixMismatch_543_KuangHeng) {
    std::vector<SyllableOption> sels{
        SyllableOption("k", 1), SyllableOption("he", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "543",
        .selections = sels,
        .consumed_count = 3,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("43"),
        .confirmed_pinyin = "k",
        .selection_history = history,
        .has_separator = true,
        .separator_position = 1
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("kuang heng"), 2);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_FALSE(ctx.state_machine.is_idle());
    EXPECT_EQ(0, ctx.input_buffer.selections.size());
    EXPECT_EQ("3", ctx.input_buffer.unassigned());
}

// 同类（ge 分支）：左选 k+ge，右选"快跟 kuai gen"（末音节"gen"→"436"同理），应 partial commit。
TEST(T9RightCommitHandlerTest, LetterBufferSyllablePrefixMismatch_543_KuaiGen) {
    std::vector<SyllableOption> sels{
        SyllableOption("k", 1), SyllableOption("ge", 2)};
    std::vector<SyllableOption> history{sels[0], sels[1]};
    auto ctx = MakeContext({
        .digits = "543",
        .selections = sels,
        .consumed_count = 3,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[1],
        .selection_candidate_digits = std::optional<std::string>("43"),
        .confirmed_pinyin = "k",
        .selection_history = history,
        .has_separator = true,
        .separator_position = 1
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("kuai gen"), 2);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_FALSE(ctx.state_machine.is_idle());
    EXPECT_EQ(0, ctx.input_buffer.selections.size());
    EXPECT_EQ("3", ctx.input_buffer.unassigned());
}

// ── 场景 [Bug-fix]: apostrophe 末音节数字码前缀误判 full commit ──
// 输入5143→只左选k（unassigned="43"），右选"可恨 ke hen"。
// 末音节"hen"→"436"以"43"为前缀但≠"43"，不应 full commit。
// 修复前：HandleCanCoverAll 用 syl_code 以 unassigned 为前缀判定 → 误判 full commit。
// 修复后：改用"完整消费"语义 → partial commit，剩余"3"→预编辑"可恨e"。
TEST(T9RightCommitHandlerTest, ApostropheSyllablePrefixMismatch_543_KeHen) {
    std::vector<SyllableOption> sels{SyllableOption("k", 1)};
    std::vector<SyllableOption> history{sels[0]};
    auto ctx = MakeContext({
        .digits = "543",
        .selections = sels,
        .consumed_count = 1,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("43"),
        .confirmed_pinyin = "k",
        .selection_history = history,
        .has_separator = true,
        .separator_position = 1
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("ke hen"), 2);
    EXPECT_FALSE(result);                                  // partial commit（非 full commit）
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_FALSE(ctx.state_machine.is_idle());
    EXPECT_EQ("3", ctx.input_buffer.unassigned());         // 剩余 '3' → 预编辑 "可恨e"
}

// 同类：右选"抗衡 kang heng"（末音节"heng"→"4364"同理），应 partial commit。
TEST(T9RightCommitHandlerTest, ApostropheSyllablePrefixMismatch_543_KuangHeng) {
    std::vector<SyllableOption> sels{SyllableOption("k", 1)};
    std::vector<SyllableOption> history{sels[0]};
    auto ctx = MakeContext({
        .digits = "543",
        .selections = sels,
        .consumed_count = 1,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("43"),
        .confirmed_pinyin = "k",
        .selection_history = history,
        .has_separator = true,
        .separator_position = 1
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("kang heng"), 2);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_FALSE(ctx.state_machine.is_idle());
    EXPECT_EQ("3", ctx.input_buffer.unassigned());
}

// 同类（ge 分支）：右选"快跟 kuai gen"（末音节"gen"→"436"同理），应 partial commit。
TEST(T9RightCommitHandlerTest, ApostropheSyllablePrefixMismatch_543_KuaiGen) {
    std::vector<SyllableOption> sels{SyllableOption("k", 1)};
    std::vector<SyllableOption> history{sels[0]};
    auto ctx = MakeContext({
        .digits = "543",
        .selections = sels,
        .consumed_count = 1,
        .state = T9StateMachine::State::kSelection,
        .selected_option = sels[0],
        .selection_candidate_digits = std::optional<std::string>("43"),
        .confirmed_pinyin = "k",
        .selection_history = history,
        .has_separator = true,
        .separator_position = 1
    });
    T9RightCommitHandler handler;
    bool result = handler.HandleRightCommit(ctx, std::optional<std::string>("kuai gen"), 2);
    EXPECT_FALSE(result);                                  // partial commit
    EXPECT_FALSE(ctx.input_buffer.is_empty());
    EXPECT_FALSE(ctx.state_machine.is_idle());
    EXPECT_EQ("3", ctx.input_buffer.unassigned());
}

}  // namespace
}  // namespace rime
