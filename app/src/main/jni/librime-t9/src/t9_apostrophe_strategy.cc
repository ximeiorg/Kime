#include "t9_apostrophe_strategy.h"

#include "t9_log.h"
#include "t9_right_commit_handler.h"
#include "t9_right_commit_utils.h"
#include "t9_string_utils.h"

namespace rime {

bool ApostropheStrategy::Handle(
    T9RightCommitHandler& owner,
    T9RightCommitHandler::Context& ctx,
    const std::string& remaining_digits,
    const SyllableAlignment& alignment,
    const std::optional<std::string>& candidate_pinyin,
    const std::optional<SyllableOption>& prev_selected_option,
    const std::optional<std::string>& prev_selection_candidate_digits,
    const std::string& prev_confirmed_pinyin) {

    T9_SCOPED_TIMER_TAG("T9RightCommit", "ApostropheStrategy.Handle");
    const T9Buffer& buf = ctx.input_buffer;
    std::string confirmed_pinyin = buf.selected_pinyin();

    RCLOG(">> Apostrophe: confirmedPinyin='%s', remainingDigits='%s'",
          confirmed_pinyin.c_str(), remaining_digits.c_str());
    RCLOG(">>   prevOpt='%s'(%d), prevDigits='%s', prevConf='%s'",
          prev_selected_option.has_value() ? prev_selected_option->pinyin.c_str() : "(null)",
          prev_selected_option.has_value() ? prev_selected_option->digit_length : 0,
          prev_selection_candidate_digits.has_value() ? prev_selection_candidate_digits->c_str() : "(null)",
          prev_confirmed_pinyin.c_str());

    // S4-3：复用 HandleRightCommit 一次构造的 alignment，消除 ParseSyllables 重复调用
    const auto& comment_syllables = alignment.syllables;
    int required_syllables = static_cast<int>(buf.selections.size()) +
        (buf.unassigned().empty() ? 0 : 1);
    bool can_cover_all = static_cast<int>(comment_syllables.size()) >= required_syllables;

    // branch1：SELECTION 态的选中项是新候选词的最后一个音节
    // 复用 T9RightCommitHandler::IsLetterBufferSelection 判断条件
    if (prev_selected_option.has_value() &&
        T9RightCommitHandler::IsLetterBufferSelection(
            confirmed_pinyin, prev_selected_option)) {
        RCLOG(">> Apostrophe: ENTER branch1 (confirmedPinyin endsWith prevOpt && longer)");
        return HandleBranch1SelectedEndingWithPrevOpt(
            owner, ctx, buf, remaining_digits, alignment, candidate_pinyin,
            prev_selected_option, prev_selection_candidate_digits,
            prev_confirmed_pinyin);
    }

    // branch2：非 SELECTION 或 confirmedPinyin 不以 selectedPinyin 结尾
    RCLOG(">> Apostrophe: ENTER branch2 (non-SELECTION or not ending with prevOpt)");
    RCLOG(">> Apostrophe branch2: canCoverAll=%d, commentSylCount=%zu, requiredSyl=%d, remainingDigits='%s'",
          can_cover_all ? 1 : 0, comment_syllables.size(), required_syllables, remaining_digits.c_str());

    // 路径 A：remaining 空 && !canCoverAll → 移除全部已消费 selections + 恢复
    if (remaining_digits.empty() && !can_cover_all) {
        RCLOG(">> Apostrophe branch2: remaining empty && !canCoverAll → removeConsumed + restore");
        ctx.input_buffer = owner.RemoveConsumedSelections(ctx, buf, buf.selected_pinyin());
        ctx.left_column_locked = false;
        return owner.RestorePrevState(ctx, prev_selected_option,
                                       prev_selection_candidate_digits, prev_confirmed_pinyin);
    }

    // 路径 B：remaining 空 → buffer 清空
    if (remaining_digits.empty()) {
        RCLOG(">> Apostrophe branch2: remaining empty → EMPTY buffer");
        ctx.TransitionToIdle();
        return true;
    }

    // 路径 C：canCoverAll && remaining 非空
    if (can_cover_all) {
        return HandleCanCoverAll(owner, ctx, buf, remaining_digits, alignment);
    }

    // 路径 D：!canCoverAll && remaining 非空
    return HandlePartialConsume(owner, ctx, buf, remaining_digits, alignment);
}

bool ApostropheStrategy::HandleBranch1SelectedEndingWithPrevOpt(
    T9RightCommitHandler& owner,
    T9RightCommitHandler::Context& ctx,
    const T9Buffer& buf,
    const std::string& remaining_digits,
    const SyllableAlignment& alignment,
    const std::optional<std::string>& candidate_pinyin,
    const std::optional<SyllableOption>& prev_selected_option,
    const std::optional<std::string>& prev_selection_candidate_digits,
    const std::string& prev_confirmed_pinyin) {

    std::string selected_pinyin = prev_selected_option->pinyin;
    std::string confirmed_pinyin = buf.selected_pinyin();
    std::string non_selected_pinyin = T9RightCommitHandler::NonSelectedPinyin(
        confirmed_pinyin, *prev_selected_option);
    int candidate_letter_count = CountLetters(candidate_pinyin);
    int consumed_from_non_selected = std::min(
        candidate_letter_count,
        static_cast<int>(non_selected_pinyin.size()));
    std::string unconsumed_pinyin = Drop(non_selected_pinyin, consumed_from_non_selected);

    // S4-3：复用 alignment.syllables 替代局部 comment_syllables
    const auto& comment_syllables = alignment.syllables;
    int required_syllables = static_cast<int>(buf.selections.size()) +
        (buf.unassigned().empty() ? 0 : 1);
    bool can_cover_all = static_cast<int>(comment_syllables.size()) >= required_syllables;

    // 子路径 1a：canCoverAll 且满足 full commit 条件
    if (can_cover_all &&
        ShouldFullCommitInSelection(consumed_from_non_selected,
                                    static_cast<int>(non_selected_pinyin.size()),
                                    remaining_digits, candidate_pinyin, selected_pinyin)) {
        owner.ClearAndEnterIdle(ctx);
        return true;
    }

    // 移除非选中部分中已被消费的拼音
    std::string consumed_non_selected = Take(non_selected_pinyin, consumed_from_non_selected);
    ctx.input_buffer = owner.RemoveConsumedSelections(ctx, buf, consumed_non_selected);
    ctx.left_column_locked = false;

    // 子路径 1b：候选词最后一个音节与 prevOpt 匹配（声母或全拼）
    if (TryLastSyllableMatch(ctx, alignment, prev_selected_option,
                              prev_selection_candidate_digits, unconsumed_pinyin)) {
        return false;
    }

    // 子路径 1c：默认回退
    return owner.RestorePrevState(ctx, prev_selected_option,
                                   prev_selection_candidate_digits, prev_confirmed_pinyin);
}

bool ApostropheStrategy::TryLastSyllableMatch(
    T9RightCommitHandler::Context& ctx,
    const SyllableAlignment& alignment,
    const std::optional<SyllableOption>& prev_selected_option,
    const std::optional<std::string>& prev_selection_candidate_digits,
    const std::string& unconsumed_pinyin) {

    if (unconsumed_pinyin.empty() || alignment.empty()) return false;

    // S4-3：用 alignment.syllable_codes 替代手动 PinyinToDigitCode（预计算值）
    const std::string& last_syl_code = alignment.syllable_codes.back();
    auto sel_code = T9PinyinMap::Instance().PinyinToDigitCode(prev_selected_option->pinyin);
    std::string sel_digits = prev_selection_candidate_digits.value_or("");

    // last_syl_code 为空（无效音节）时视为不匹配，等价于旧逻辑 nullopt
    bool is_shengmu_match = prev_selected_option->digit_length == 1 &&
        !last_syl_code.empty() && sel_code.has_value() &&
        StartsWith(last_syl_code, *sel_code) &&
        StartsWith(sel_digits, *sel_code);

    bool is_full_pinyin_match = prev_selected_option->digit_length > 1 &&
        !last_syl_code.empty() && sel_code.has_value() &&
        last_syl_code == *sel_code;

    if (is_shengmu_match || is_full_pinyin_match) {
        // 移除最后一个选择
        std::vector<SyllableOption> new_sels = ctx.input_buffer.selections;
        if (!new_sels.empty()) new_sels.pop_back();
        ctx.input_buffer = T9Buffer(
            ctx.input_buffer.digit_sequence, new_sels,
            ctx.input_buffer.consumed_count, ctx.input_buffer.total_digits_entered);
        ctx.TransitionToInput(/*keep_history=*/false);
        return true;
    }
    return false;
}

bool ApostropheStrategy::HandleCanCoverAll(
    T9RightCommitHandler& owner,
    T9RightCommitHandler::Context& ctx,
    const T9Buffer& buf,
    const std::string& remaining_digits,
    const SyllableAlignment& alignment) {

    // canCoverAll=true 但 remaining_digits 非空：
    // 说明 ComputeConsumedDigitsFromPinyin 无法正确计算全简拼场景的消费
    // 此时需验证候选词的对应音节是否覆盖了 unassigned
    // S4-3：用 alignment.syllable_codes 替代手动 PinyinToDigitCode（预计算值）
    const std::string& unassigned_str = buf.unassigned();
    bool unassigned_covered = false;
    if (!alignment.empty() && !unassigned_str.empty()) {
        int unassigned_syl_index = static_cast<int>(buf.selections.size());
        if (unassigned_syl_index < alignment.syllable_count()) {
            const std::string& syl_code = alignment.syllable_codes[unassigned_syl_index];
            if (!syl_code.empty()) {
                // S9：改用"完整消费"语义——unassigned 被完整消费才算覆盖，
                // 不允许候选音节数字码更长（如"hen"→"436"以"43"为前缀但≠"43"）。
                // 简拼 unassigned（1 位）首字母匹配即覆盖；全拼级要求 syl_code 更短或相等。
                if (unassigned_str.size() == 1 &&
                    syl_code[0] == unassigned_str[0]) {
                    unassigned_covered = true;
                } else if (unassigned_str.size() > 1 &&
                           syl_code.size() <= unassigned_str.size() &&
                           unassigned_str.compare(0, syl_code.size(), syl_code) == 0) {
                    unassigned_covered = true;
                }
            }
        }
    }

    if (unassigned_covered) {
        RCLOG(">> Apostrophe branch2: canCoverAll + unassigned covered → full commit");
        owner.ClearAndEnterIdle(ctx);
        return true;
    }

    RCLOG(">> Apostrophe branch2: remaining='%s' → WithRemainingDigits", remaining_digits.c_str());
    ctx.input_buffer = buf.WithRemainingDigits(remaining_digits, buf);
    ctx.left_column_locked = false;
    ctx.TransitionToInput(false);
    return ctx.input_buffer.is_empty();
}

bool ApostropheStrategy::IsSyllableInitialMatch(
    const std::vector<std::string>& comment_syllables,
    const std::vector<SyllableOption>& selections,
    int consumed_sel_count) {
    if (consumed_sel_count <= 0 ||
        consumed_sel_count >= static_cast<int>(selections.size())) {
        return false;
    }
    for (int i = 0; i < consumed_sel_count; ++i) {
        if (comment_syllables[i].empty() || selections[i].pinyin.empty()) return false;
        if (comment_syllables[i][0] != selections[i].pinyin[0]) return false;
    }
    return true;
}

void ApostropheStrategy::ApplyPartialConsumeAndLock(
    T9RightCommitHandler& owner,
    T9RightCommitHandler::Context& ctx,
    const T9Buffer& buf,
    const std::vector<SyllableOption>& selections,
    int consumed_sel_count) {

    // 用 selections 的拼音构造 consumed_pinyin（而非候选词音节）
    std::string consumed_pinyin;
    for (int i = 0; i < consumed_sel_count; ++i) {
        consumed_pinyin += selections[i].pinyin;
    }
    RCLOG(">> Apostrophe branch2: !canCoverAll, consume %d selections, consumedPinyin='%s'",
          consumed_sel_count, consumed_pinyin.c_str());
    ctx.input_buffer = owner.RemoveConsumedSelections(ctx, buf, consumed_pinyin);

    // 右选后若有剩余 selections，锁定左侧候选区为剩余 selection 对应的数字段
    if (!ctx.input_buffer.selections.empty()) {
        int remaining_sel_total_len = 0;
        for (const auto& s : ctx.input_buffer.selections) {
            remaining_sel_total_len += s.digit_length;
        }
        int start = ctx.input_buffer.consumed_count - remaining_sel_total_len;
        if (start >= 0 && start + remaining_sel_total_len <=
                static_cast<int>(ctx.input_buffer.digit_sequence.size())) {
            ctx.separator_consumed_digits =
                ctx.input_buffer.digit_sequence.substr(start, remaining_sel_total_len);
            ctx.left_column_locked = true;
            RCLOG(">> Apostrophe branch2: lock left column to remaining sel digits='%s'",
                  ctx.separator_consumed_digits->c_str());
        } else {
            ctx.left_column_locked = false;
        }
    } else {
        ctx.left_column_locked = false;
    }

    ctx.TransitionToInput(false);
}

bool ApostropheStrategy::HandlePartialConsume(
    T9RightCommitHandler& owner,
    T9RightCommitHandler::Context& ctx,
    const T9Buffer& buf,
    const std::string& remaining_digits,
    const SyllableAlignment& alignment) {

    // S4-3：复用 alignment.syllables
    const auto& comment_syllables = alignment.syllables;
    int consumed_sel_count = static_cast<int>(comment_syllables.size());
    if (consumed_sel_count > 0 && !buf.selections.empty() &&
        IsSyllableInitialMatch(comment_syllables, buf.selections, consumed_sel_count)) {
        ApplyPartialConsumeAndLock(owner, ctx, buf, buf.selections, consumed_sel_count);
        return ctx.input_buffer.is_empty();
    }

    RCLOG(">> Apostrophe branch2: remaining='%s' !canCoverAll → WithRemainingDigits", remaining_digits.c_str());
    ctx.input_buffer = buf.WithRemainingDigits(remaining_digits, buf);
    ctx.left_column_locked = false;
    ctx.TransitionToInput(false);
    return ctx.input_buffer.is_empty();
}

}  // namespace rime
