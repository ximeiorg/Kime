#include "t9_letter_buffer_strategy.h"

#include <algorithm>

#include "t9_log.h"
#include "t9_right_commit_handler.h"
#include "t9_right_commit_utils.h"
#include "t9_string_utils.h"

namespace rime {

// ── 子路径 C 条件谓词 ──

bool LetterBufferStrategy::IsFullSelectionOnly(
    const std::string& remaining_digits, const T9Buffer& buf,
    int syllable_count, int selection_count) {
    return remaining_digits.empty() && !buf.unassigned().empty() &&
           syllable_count == selection_count;
}

bool LetterBufferStrategy::IsExtraSyllablePartial(
    const std::string& remaining_digits, const T9Buffer& buf,
    int syllable_count, int selection_count) {
    return syllable_count > selection_count && !buf.unassigned().empty() &&
           !remaining_digits.empty();
}

ExtraSyllableCommitCheck LetterBufferStrategy::CheckExtraSyllableCommit(
    const std::vector<std::string>& comment_syllables,
    int selection_count,
    const SyllableOption& prev_selected_option,
    bool has_unassigned,
    int candidate_text_length) {

    ExtraSyllableCommitCheck check;
    if (comment_syllables.empty()) return check;

    // 覆盖末选择的音节：候选词前 selection_count 个音节中的最后一个。
    // 存在 completion 额外音节（commentSylCount > selectionCount）时，
    // back() 是额外音节（如"kai de qi wan xiao"的"xiao"不以"w"开头），
    // 需用第 selection_count 个音节（"wan"）判断覆盖。
    int cover_idx = std::min(
        selection_count, static_cast<int>(comment_syllables.size())) - 1;
    if (cover_idx < 0) cover_idx = 0;
    const std::string& syl_covering_prev_opt = comment_syllables[cover_idx];

    // S9：覆盖判定改用数字码而非字母前缀。
    // 字母前缀（StartsWith("hen","he")）会把更长的不同音节误判为覆盖，
    // 但"hen"→"436"≠"he"→"43"，用户从未输入末音节所需的额外数字。
    // 覆盖仅当：数字码相等（同一音节），或末选择是简拼且末音节以该字母开头（补全）。
    const auto& pmap = T9PinyinMap::Instance();
    auto syl_code_opt = pmap.PinyinToDigitCode(syl_covering_prev_opt);
    auto opt_code_opt = pmap.PinyinToDigitCode(prev_selected_option.pinyin);
    bool syl_digit_eq_opt =
        syl_code_opt.has_value() && opt_code_opt.has_value() &&
        *syl_code_opt == *opt_code_opt;
    bool prev_opt_is_abbrev = prev_selected_option.pinyin.size() == 1;
    bool syl_starts_with_opt_letter = prev_opt_is_abbrev &&
        !syl_covering_prev_opt.empty() &&
        syl_covering_prev_opt[0] == prev_selected_option.pinyin[0];
    check.last_syl_covers_prev_opt = syl_digit_eq_opt || syl_starts_with_opt_letter;

    // 公共前缀长度：用数字码计算，供 initials-only 分支退还未消费位数。
    // 末音节更长（数字码以末选择为前缀但不等）时，仅首字母匹配，限制为 1。
    int overlap = 0;
    if (syl_code_opt.has_value() && opt_code_opt.has_value()) {
        const std::string& syl_code = *syl_code_opt;
        const std::string& opt_code = *opt_code_opt;
        int max_overlap = std::min(
            static_cast<int>(syl_code.size()),
            static_cast<int>(opt_code.size()));
        while (overlap < max_overlap &&
               syl_code[overlap] == opt_code[overlap]) {
            ++overlap;
        }
        // 末音节更长且以末选择数字码为前缀 → 仅首字母匹配，限制 overlap 为 1
        bool syl_code_longer_starts_with_opt =
            syl_code.size() > opt_code.size() &&
            syl_code.compare(0, opt_code.size(), opt_code) == 0;
        if (syl_code_longer_starts_with_opt && overlap > 1) {
            overlap = 1;
        }
    } else {
        // 数字码查询失败时回退到字母公共前缀
        int max_overlap = std::min(
            static_cast<int>(syl_covering_prev_opt.size()),
            static_cast<int>(prev_selected_option.pinyin.size()));
        while (overlap < max_overlap &&
               syl_covering_prev_opt[overlap] == prev_selected_option.pinyin[overlap]) {
            ++overlap;
        }
    }
    check.covered_prefix_len = overlap;

    bool extra_consumption_valid = has_unassigned || check.last_syl_covers_prev_opt;
    int consumed_selections = static_cast<int>(comment_syllables.size());
    int non_selected_history_size = selection_count - 1;
    check.is_full_commit =
        ((consumed_selections > non_selected_history_size &&
          extra_consumption_valid) ||
         (comment_syllables.back() == prev_selected_option.pinyin &&
          !has_unassigned)) &&
        candidate_text_length >= static_cast<int>(comment_syllables.size());
    return check;
}

bool LetterBufferStrategy::Handle(
    T9RightCommitHandler& owner,
    T9RightCommitHandler::Context& ctx,
    const std::string& remaining_digits,
    const std::optional<std::string>& candidate_pinyin,
    int candidate_text_length,
    const std::optional<SyllableOption>& prev_selected_option,
    const std::optional<std::string>& prev_selection_candidate_digits,
    const std::string& prev_confirmed_pinyin,
    const T9Buffer& prev_buf) {

    T9_SCOPED_TIMER_TAG("T9RightCommit", "LetterBufferStrategy.Handle");
    const T9Buffer& buf = ctx.input_buffer;
    std::string selected_pinyin = buf.selected_pinyin();
    auto candidate_syllables = ParseSyllables(candidate_pinyin.value_or(""));
    int syllable_count = static_cast<int>(candidate_syllables.size());
    int selection_count = static_cast<int>(buf.selections.size());

    RCLOG(">> HandleLetterBufferRightCommit: ENTER");
    RCLOG(">>   buf.digitSeq='%s', buf.consumedCount=%d, buf.selCount=%d, buf.unassigned='%s'",
          buf.digit_sequence.c_str(), buf.consumed_count, selection_count,
          buf.unassigned().c_str());
    RCLOG(">>   selectedPinyin='%s', remainingDigits='%s', candidatePinyin='%s', candidateTextLen=%d, syllableCount=%d, selCount=%d",
          selected_pinyin.c_str(), remaining_digits.c_str(),
          candidate_pinyin.has_value() ? candidate_pinyin->c_str() : "(null)",
          candidate_text_length, syllable_count, selection_count);
    RCLOG(">>   prevOpt='%s', prevDigits='%s'",
          prev_selected_option.has_value() ? prev_selected_option->pinyin.c_str() : "(null)",
          prev_selection_candidate_digits.has_value() ? prev_selection_candidate_digits->c_str() : "(null)");

    // 子路径 A：候选词无音节 → 防御性无注释提交
    if (syllable_count == 0) {
        RCLOG(">>   syllableCount=0 → HandleDefensiveNoCommentCommit");
        return HandleDefensiveNoCommentCommit(
            owner, ctx, candidate_text_length,
            {prev_selected_option, prev_selection_candidate_digits, prev_confirmed_pinyin},
            prev_buf);
    }

    // 子路径 B：音节数 < selections 数，或音节数相等但字母数不足 → 部分消费
    if (syllable_count < selection_count ||
        (syllable_count == selection_count && syllable_count > 0 &&
         CountLetters(candidate_pinyin) < static_cast<int>(selected_pinyin.size()))) {
        RCLOG(">>   BRANCH: syllableCount(%d) < selectionCount(%d)"
              " || (syllableCount==selCount && candLetterCount < selPinyin.size)",
              syllable_count, selection_count);
        if (prev_selected_option.has_value()) {
            // 多音节候选词 + 音节数 < selections 数 + EndsWith → HSLBC（音节匹配路径）
            // 注意：syllable_count == selection_count 但不满足字母数时不重定向到 HSLBC，
            // 因为 HSLBC 的音节匹配块假设每个音节完整覆盖一个 selection，但最后一个音节
            // 可能是部分覆盖（如"li gu" 2音节 vs [li, gua] 2个selection，"gu" < "gua"）。
            if (syllable_count > 1 && syllable_count < selection_count &&
                EndsWith(selected_pinyin, prev_selected_option->pinyin)) {
                RCLOG(">>   → HandleSelectionLetterBufferCommit (multi-syllable, syllableCount=%d < selectionCount=%d)",
                      syllable_count, selection_count);
                int letter_count = CountLetters(candidate_pinyin);
                return HandleSelectionLetterBufferCommit(
                    owner, ctx, candidate_pinyin, candidate_text_length,
                    letter_count,
                    {prev_selected_option, prev_selection_candidate_digits, prev_confirmed_pinyin},
                    prev_buf);
            }
            // 单音节候选词 → HSPC：consumed_pinyin_len = 第一个 selection 的拼音长度。
            // 不能使用 syllable_count=1，因为 Take(selectedPinyin, 1) 只取1个字符，
            // 对于多字符候选词（如"jiu"→"j"而非"jiu"）会导致 scenario18 误触发。
            // 第一个 selection 的拼音长度已包含了该 selection 的全部字符。
            int consumed_len = buf.selections.empty() ? syllable_count :
                static_cast<int>(buf.selections[0].pinyin.size());
            RCLOG(">>   → HandleSelectionPrefixConsumed (single-syllable, consumedPinyinLen=%d)", consumed_len);
            return HandleSelectionPrefixConsumed(
                owner, ctx, buf, selected_pinyin, remaining_digits,
                consumed_len,
                {prev_selected_option, prev_selection_candidate_digits, prev_confirmed_pinyin});
        }
        // INPUT 态：候选词覆盖部分数字 buffer，转为剩余数字
        RCLOG(">>   INPUT branch: 候选词覆盖部分数字 buffer");
        auto remaining_digits_opt = T9PinyinMap::Instance().PinyinToDigitCode(
            Drop(selected_pinyin, syllable_count));
        if (remaining_digits_opt.has_value()) {
            const std::string& remaining_digits_inner = *remaining_digits_opt;
            std::string consumed_pinyin = Take(selected_pinyin, syllable_count);
            ctx.state_machine.RemoveConsumedHistoryEntries(consumed_pinyin);
            ctx.input_buffer = T9Buffer(remaining_digits_inner, {}, 0,
                static_cast<int>(remaining_digits_inner.size()));
            ctx.left_column_locked = false;
            return owner.RestorePrevState(ctx, prev_selected_option,
                                           prev_selection_candidate_digits);
        }
        // fallback：未匹配，继续后续逻辑
    }

    // 子路径 C：音节数 >= selections 数 → 尝试完全消费
    if (prev_selected_option.has_value() &&
        EndsWith(selected_pinyin, prev_selected_option->pinyin)) {
        // C1：候选词仅覆盖所有 selections，不覆盖 unassigned
        if (IsFullSelectionOnly(remaining_digits, buf, syllable_count, selection_count)) {
            RCLOG(">>   → HandleSelectionPrefixConsumed (full-selection, remainingDigits='%s')",
                  remaining_digits.c_str());
            int consumed_len = static_cast<int>(selected_pinyin.size());
            return HandleSelectionPrefixConsumed(
                owner, ctx, buf, selected_pinyin, remaining_digits,
                consumed_len,
                {prev_selected_option, prev_selection_candidate_digits, prev_confirmed_pinyin});
        }
        // C2：候选词音节数 > selections 数，额外音节部分消费了 unassigned 但未消费完
        if (IsExtraSyllablePartial(remaining_digits, buf, syllable_count, selection_count)) {
            RCLOG(">>   → HandleSelectionPrefixConsumed (syllableCount=%d > selectionCount=%d, remainingDigits='%s')",
                  syllable_count, selection_count, remaining_digits.c_str());
            int consumed_len = static_cast<int>(selected_pinyin.size());
            return HandleSelectionPrefixConsumed(
                owner, ctx, buf, selected_pinyin, remaining_digits,
                consumed_len,
                {prev_selected_option, prev_selection_candidate_digits, prev_confirmed_pinyin});
        }
        RCLOG(">>   → HandleSelectionLetterBufferCommit (syllableCount=%d >= selectionCount=%d)",
              syllable_count, selection_count);
        int letter_count = CountLetters(candidate_pinyin);
        return HandleSelectionLetterBufferCommit(
            owner, ctx, candidate_pinyin, candidate_text_length, letter_count,
            {prev_selected_option, prev_selection_candidate_digits, prev_confirmed_pinyin},
            prev_buf);
    }

    RCLOG(">>   → ClearAndEnterIdle (no EndsWith match or no prevOpt)");
    owner.ClearAndEnterIdle(ctx);
    return true;
}

bool LetterBufferStrategy::HandleSelectionPrefixConsumed(
    T9RightCommitHandler& owner,
    T9RightCommitHandler::Context& ctx,
    const T9Buffer& buf,
    const std::string& selected_pinyin,
    const std::string& remaining_digits,
    int consumed_pinyin_len,
    const SelectionContext& sel_ctx) {

    const auto& prev_selected_option = sel_ctx.prev_selected_option;
    const auto& prev_selection_candidate_digits = sel_ctx.prev_selection_candidate_digits;
    const auto& prev_confirmed_pinyin = sel_ctx.prev_confirmed_pinyin;

    std::string consumed_pinyin = Take(selected_pinyin, consumed_pinyin_len);
    RCLOG(">>   SELECTION branch: consumedPinyin='%s' (Take selectedPinyin by len=%d)",
          consumed_pinyin.c_str(), consumed_pinyin_len);

    // 场景18：候选词 pinyin 是 prevSelectedOption.pinyin 的真前缀。
    // 仅当 consumed_pinyin 实际延伸到 prev_selected_option 区域时才触发。
    // prev_opt_start_pos：prev_selected_option 在 selected_pinyin 中的起始位置
    int prev_opt_start_pos = static_cast<int>(selected_pinyin.size()) -
                             static_cast<int>(prev_selected_option->pinyin.size());
    if (consumed_pinyin.size() < prev_selected_option->pinyin.size() &&
        StartsWith(prev_selected_option->pinyin, consumed_pinyin) &&
        consumed_pinyin_len > prev_opt_start_pos) {
        RCLOG(">>   scenario18: consumedPinyin is真前缀 of prevOpt.pinyin");
        auto remaining_digits_opt = T9PinyinMap::Instance().PinyinToDigitCode(
            Drop(selected_pinyin, consumed_pinyin_len));
        if (remaining_digits_opt.has_value()) {
            const std::string& remaining_digits_inner = *remaining_digits_opt;
            ctx.input_buffer = T9Buffer(remaining_digits_inner, {}, 0,
                static_cast<int>(remaining_digits_inner.size()));
            ctx.left_column_locked = false;
            ctx.TransitionToInput(false);
            RCLOG(">>   scenario18: → pure digit buffer '%s'", remaining_digits_inner.c_str());
            return false;
        }
    }

    // SELECTION 态：检查 ComputeRightCommitConsumption 计算的剩余数字
    if (!remaining_digits.empty()) {
        RCLOG(">>   remainingDigits non-empty branch: consumedDigitCount calc");
        int consumed_digit_count = static_cast<int>(buf.digit_sequence.size()) -
            static_cast<int>(remaining_digits.size());
        // 当 consumedCount > selections_total_length 时，consumed_digit_count 包含了
        // 右选已消费的偏移量（如右选"策ce"消费了"23"但selections中无记录），
        // 需要减去偏移量，否则遍历selections时会多消费selection。
        // 例：digitSeq="23744", consumedCount=5, selections=[pi(2),h(1)],
        //     remaining_digits="4", consumed_digit_count=4, 但"23"是右选消费，
        //     selections从偏移量2开始，实际只消费了pi(2)（2位），h(1)应保留。
        {
            int sel_total = buf.selections_digit_length();
            if (sel_total > 0 && buf.consumed_count > sel_total) {
                int sel_offset = buf.consumed_count - sel_total;
                consumed_digit_count -= sel_offset;
                if (consumed_digit_count < 0) consumed_digit_count = 0;
                RCLOG(">>   adjusted consumedDigitCount=%d (selOffset=%d, selTotal=%d, consumedCount=%d)",
                      consumed_digit_count, sel_offset, sel_total, buf.consumed_count);
            }
        }
        int cumulative = 0;
        int cut_index = 0;
        for (size_t i = 0; i < buf.selections.size(); ++i) {
            if (cumulative < consumed_digit_count) {
                cumulative += buf.selections[i].digit_length;
                cut_index = static_cast<int>(i) + 1;
            } else {
                break;
            }
        }
        std::vector<SyllableOption> remaining_sels(
            buf.selections.begin() + cut_index, buf.selections.end());
        RCLOG(">>   consumedDigitCount=%d, cutIndex=%d, remainingSels.size=%zu",
              consumed_digit_count, cut_index, remaining_sels.size());

        if (!remaining_sels.empty()) {
            int new_consumed = 0;
            for (const auto& sel : remaining_sels) {
                new_consumed += sel.digit_length;
            }
            ctx.input_buffer = T9Buffer(remaining_digits, remaining_sels,
                new_consumed, static_cast<int>(remaining_digits.size()));
            ctx.left_column_locked = false;
            const auto& last_sel = remaining_sels.back();
            std::string sel_digits = remaining_digits.substr(
                new_consumed - last_sel.digit_length);
            std::vector<SyllableOption> new_history(
                ctx.state_machine.selection_history().begin() + cut_index,
                ctx.state_machine.selection_history().end());
            ctx.state_machine.RestoreFrom(
                T9StateMachine::State::kSelection,
                last_sel,
                sel_digits,
                "",
                new_history);
            RCLOG(">>   → rebuild buffer with remaining_sels: digitSeq='%s', consumedCount=%d, selCount=%zu",
                  ctx.input_buffer.digit_sequence.c_str(), ctx.input_buffer.consumed_count,
                  ctx.input_buffer.selections.size());
            ctx.SyncBufferToRime();
        } else {
            ctx.input_buffer = T9Buffer(remaining_digits, {}, 0,
                static_cast<int>(remaining_digits.size()));
            ctx.left_column_locked = false;
            RCLOG(">>   → pure digit buffer (no remaining_sels): '%s'", remaining_digits.c_str());
            ctx.TransitionToInput(false);
        }
        RCLOG(">> LetterBuffer: remaining_digits='%s' with %zu remaining selections",
              remaining_digits.c_str(), remaining_sels.size());
        return false;
    }

    // remaining_digits 为空：保留未消费部分为字母形式
    RCLOG(">>   remainingDigits empty → RemoveConsumedSelections(consumedPinyin='%s') + RestorePrevState",
          consumed_pinyin.c_str());
    ctx.input_buffer = owner.RemoveConsumedSelections(ctx, buf, consumed_pinyin);
    ctx.left_column_locked = false;
    if (ctx.input_buffer.selections.empty()) {
        // 所有 selections 已消费，只剩 unassigned → 进入 INPUT 态
        RCLOG(">>   no remaining selections → TransitionToInput");
        ctx.TransitionToInput(false);
        return false;
    }
    return owner.RestorePrevState(ctx, prev_selected_option,
                                   prev_selection_candidate_digits, prev_confirmed_pinyin);
}

bool LetterBufferStrategy::HandleSelectionLetterBufferCommit(
    T9RightCommitHandler& owner,
    T9RightCommitHandler::Context& ctx,
    const std::optional<std::string>& candidate_pinyin,
    int candidate_text_length,
    int letter_count,
    const SelectionContext& sel_ctx,
    const T9Buffer& prev_buf) {

    const auto& prev_selected_option = *sel_ctx.prev_selected_option;  // HSLBC 调用时保证非空
    const auto& prev_selection_candidate_digits = sel_ctx.prev_selection_candidate_digits;
    const auto& prev_confirmed_pinyin = sel_ctx.prev_confirmed_pinyin;

    const T9Buffer& buf = ctx.input_buffer;
    std::string selected_pinyin = buf.selected_pinyin();

    RCLOG(">> HandleSelectionLetterBufferCommit: ENTER");
    RCLOG(">>   buf.digitSeq='%s', buf.consumedCount=%d, buf.selCount=%zu, buf.unassigned='%s'",
          buf.digit_sequence.c_str(), buf.consumed_count, buf.selections.size(),
          buf.unassigned().c_str());
    RCLOG(">>   selectedPinyin='%s', candidatePinyin='%s', candidateTextLen=%d, letterCount=%d",
          selected_pinyin.c_str(),
          candidate_pinyin.has_value() ? candidate_pinyin->c_str() : "(null)",
          candidate_text_length, letter_count);
    RCLOG(">>   prevOpt='%s'(%d), prevDigits='%s', prevConf='%s'",
          prev_selected_option.pinyin.c_str(), prev_selected_option.digit_length,
          prev_selection_candidate_digits.has_value() ? prev_selection_candidate_digits->c_str() : "(null)",
          prev_confirmed_pinyin.c_str());

    auto comment_syllables = candidate_pinyin.has_value()
        ? ParseSyllables(*candidate_pinyin)
        : std::vector<std::string>{};
    bool has_syllable_boundaries = comment_syllables.size() > 1;
    RCLOG(">>   commentSylCount=%zu, hasSyllableBoundaries=%d",
          comment_syllables.size(), has_syllable_boundaries ? 1 : 0);
    for (size_t i = 0; i < comment_syllables.size(); ++i) {
        RCLOG(">>   commentSyl[%zu]='%s'", i, comment_syllables[i].c_str());
    }

    const auto& history = ctx.state_machine.selection_history();
    RCLOG(">>   history.size=%zu", history.size());
    for (size_t i = 0; i < history.size(); ++i) {
        RCLOG(">>   history[%zu]='%s'(%d)", i, history[i].pinyin.c_str(),
              history[i].digit_length);
    }

    bool is_full_commit_without_boundaries = !has_syllable_boundaries &&
        !history.empty() &&
        JoinPinyins(history) == selected_pinyin &&
        candidate_text_length > 0 &&
        candidate_text_length >= static_cast<int>(history.size());

    bool is_jianpin_aligned = has_syllable_boundaries &&
        candidate_text_length > 0 &&
        candidate_text_length >= static_cast<int>(comment_syllables.size()) &&
        IsFullCommitByJianpinAlignment(selected_pinyin, comment_syllables);

    std::string non_selected_part = T9RightCommitHandler::NonSelectedPinyin(
        selected_pinyin, prev_selected_option);
    RCLOG(">>   nonSelectedPart='%s' (NonSelectedPinyin)", non_selected_part.c_str());

    bool is_full_commit = (has_syllable_boundaries &&
        candidate_text_length > 0 &&
        candidate_text_length >= static_cast<int>(comment_syllables.size()) &&
        (non_selected_part.empty() ||
         IsAllSelectedConsumed(selected_pinyin, comment_syllables, history) ||
         is_jianpin_aligned)) ||
        is_full_commit_without_boundaries;
    RCLOG(">>   isFullCommitWithoutBoundaries=%d, isJianpinAligned=%d, isFullCommit=%d",
          is_full_commit_without_boundaries ? 1 : 0,
          is_jianpin_aligned ? 1 : 0, is_full_commit ? 1 : 0);

    // 子路径 A：full commit
    if (is_full_commit) {
        RCLOG(">> HandleSelectionLetterBufferCommit: isFullCommit → ClearAndEnterIdle");
        owner.ClearAndEnterIdle(ctx);
        return true;
    }

    // 子路径 B：nonSelectedPart 为空 → 恢复
    if (non_selected_part.empty()) {
        RCLOG(">> HandleSelectionLetterBufferCommit: nonSelectedPart empty → RestorePrevState");
        return owner.RestorePrevState(ctx, prev_selected_option,
                                       prev_selection_candidate_digits, prev_confirmed_pinyin);
    }

    // 子路径 C：有音节边界时，使用基于选择历史的消费
    if (has_syllable_boundaries && candidate_text_length > 0) {
        const std::string& last_syl = comment_syllables.back();
        std::string last_syl_initial = last_syl.empty() ? "" : std::string(1, last_syl[0]);
        std::string opt_initial = prev_selected_option.pinyin.empty()
            ? "" : std::string(1, prev_selected_option.pinyin[0]);
        auto last_syl_initial_code = last_syl_initial.empty()
            ? std::optional<std::string>{}
            : T9PinyinMap::Instance().PinyinToDigitCode(last_syl_initial);
        auto opt_initial_code = opt_initial.empty()
            ? std::optional<std::string>{}
            : T9PinyinMap::Instance().PinyinToDigitCode(opt_initial);
        bool would_trigger_shengmu = last_syl_initial_code.has_value() &&
            opt_initial_code.has_value() &&
            *last_syl_initial_code == *opt_initial_code;
        RCLOG(">>   lastSyl='%s', lastSylInitial='%s', optInitial='%s', wouldTriggerShengmu=%d",
              last_syl.c_str(), last_syl_initial.c_str(), opt_initial.c_str(),
              would_trigger_shengmu ? 1 : 0);

        if (!would_trigger_shengmu || has_syllable_boundaries) {
            std::vector<std::string> non_selected_syllables = comment_syllables;
            RCLOG(">>   nonSelectedSyllables.size=%zu (commentSyl.back='%s' vs prevOpt.pinyin='%s')",
                  non_selected_syllables.size(), comment_syllables.back().c_str(),
                  prev_selected_option.pinyin.c_str());

            if (!non_selected_syllables.empty()) {
                int consumed_selections = static_cast<int>(non_selected_syllables.size());
                std::vector<SyllableOption> non_selected_history = history;
                if (!non_selected_history.empty()) non_selected_history.pop_back();
                RCLOG(">>   consumedSelections=%d, nonSelectedHistory.size=%zu",
                      consumed_selections, non_selected_history.size());

                if (consumed_selections >= static_cast<int>(non_selected_history.size())) {
                    // 先做纯函数判定（CheckExtraSyllableCommit，无副作用）。
                    // 判定前置到 RemoveConsumedSelections 之前：selection_count 直接用
                    // buf.selections.size()（消费后 buf 的 selections 被裁剪），且不依赖
                    // 副作用后的 buffer 状态。
                    auto commit_check = CheckExtraSyllableCommit(
                        comment_syllables,
                        static_cast<int>(buf.selections.size()),
                        prev_selected_option,
                        !buf.unassigned().empty(),
                        candidate_text_length);
                    if (commit_check.is_full_commit) {
                        RCLOG(">>   → ClearAndEnterIdle (full commit, consumedSelections=%d, nonSelectedHistory.size=%zu, coveringSyl matches prevOpt='%s')",
                              consumed_selections, non_selected_history.size(),
                              prev_selected_option.pinyin.c_str());
                        owner.ClearAndEnterIdle(ctx);
                        return true;
                    }
                    ctx.input_buffer = owner.RemoveConsumedSelections(ctx, buf, non_selected_part);
                    ctx.left_column_locked = false;
                    // 首字母仅匹配路径：额外音节覆盖末选择但仅匹配首字母
                    // （如"hu"仅匹配"he"的首字母'h'），且无 unassigned。
                    // 此时需退还未消费的末选择数字到 consumed，使 RIME 只收到未消费部分。
                    // 退还位数 = digit_length - 公共前缀长度：
                    //   'hu' ⊂ 首字母匹配 'he'（overlap=1）→ 退还 2-1=1；
                    //   'gu' 真前缀 'gua'（overlap=2）→ 退还 3-2=1（仅剩末位'2'未消费）。
                    if (consumed_selections > static_cast<int>(non_selected_history.size()) &&
                        !commit_check.last_syl_covers_prev_opt && buf.unassigned().empty()) {
                        int unconsumed_from_prev_opt = prev_selected_option.digit_length -
                            commit_check.covered_prefix_len;
                        if (unconsumed_from_prev_opt < 0) unconsumed_from_prev_opt = 0;
                        int new_consumed = buf.consumed_count - unconsumed_from_prev_opt;
                        if (new_consumed < 0) new_consumed = 0;
                        ctx.input_buffer = T9Buffer(
                            buf.digit_sequence, {},
                            new_consumed,
                            buf.total_digits_entered);
                        ctx.left_column_locked = false;
                        RCLOG(">>   initials-only: prevOpt unconsumed=%d, newConsumed=%d, unassigned='%s'",
                              unconsumed_from_prev_opt, new_consumed,
                              ctx.input_buffer.unassigned().c_str());
                        ctx.TransitionToInput(false);
                        return false;
                    }
                    return owner.RestorePrevState(ctx, prev_selected_option,
                                                   prev_selection_candidate_digits, prev_confirmed_pinyin);
                }

                std::string consumed_pinyin;
                for (int i = 0; i < consumed_selections && i < static_cast<int>(non_selected_history.size()); ++i) {
                    consumed_pinyin += non_selected_history[i].pinyin;
                }
                RCLOG(">>   BRANCH B: consumedPinyin='%s' (前 %d 个 nonSelectedHistory 拼接)",
                      consumed_pinyin.c_str(), consumed_selections);
                ctx.input_buffer = owner.RemoveConsumedSelections(ctx, buf, consumed_pinyin);
                std::string remaining_pinyin_str = Drop(non_selected_part,
                    static_cast<int>(consumed_pinyin.size()));
                RCLOG(">>   remainingPinyinStr='%s' (Drop nonSelectedPart by consumedPinyin)",
                      remaining_pinyin_str.c_str());
                ctx.left_column_locked = false;
                return owner.RestorePrevState(ctx, prev_selected_option,
                                               prev_selection_candidate_digits, remaining_pinyin_str);
            }
        }
    }

    // 子路径 D：基于非选中部分数字码的消费计算
    auto non_selected_digits_opt = T9PinyinMap::Instance().PinyinToDigitCode(non_selected_part);
    if (!non_selected_digits_opt.has_value()) {
        owner.ClearAndEnterIdle(ctx);
        return true;
    }
    const std::string& non_selected_digits = *non_selected_digits_opt;

    int consumed_count = ComputeSelectionConsumedCount(
        has_syllable_boundaries, candidate_text_length, comment_syllables,
        letter_count, prev_selected_option, non_selected_digits, candidate_pinyin);

    if (consumed_count >= static_cast<int>(non_selected_digits.size())) {
        return HandleConsumedAllNonSelected(
            owner, ctx, has_syllable_boundaries, candidate_text_length, comment_syllables,
            candidate_pinyin, prev_selected_option, prev_selection_candidate_digits,
            non_selected_part, non_selected_digits, consumed_count, prev_buf);
    }
    if (consumed_count > 0) {
        return HandlePartialConsumedNonSelected(
            owner, ctx, consumed_count, non_selected_part, non_selected_digits,
            prev_selected_option, prev_selection_candidate_digits, prev_confirmed_pinyin);
    }

    owner.ClearAndEnterIdle(ctx);
    return true;
}

int LetterBufferStrategy::ComputeSelectionConsumedCount(
    bool has_syllable_boundaries,
    int candidate_text_length,
    const std::vector<std::string>& comment_syllables,
    int letter_count,
    const SyllableOption& prev_selected_option,
    const std::string& non_selected_digits,
    const std::optional<std::string>& candidate_pinyin) {

    if (has_syllable_boundaries && candidate_text_length > 0 &&
        static_cast<int>(comment_syllables.size()) > candidate_text_length) {
        int consumed = 0;
        int limit = std::min(candidate_text_length,
                             static_cast<int>(comment_syllables.size()));
        for (int i = 0; i < limit; ++i) {
            auto code = T9PinyinMap::Instance().PinyinToDigitCode(comment_syllables[i]);
            if (code.has_value()) {
                consumed += static_cast<int>(code->size());
            }
        }
        return consumed;
    }

    int candidate_non_selected_letters = letter_count -
        static_cast<int>(prev_selected_option.pinyin.size());
    if (candidate_non_selected_letters >= 1 &&
        candidate_non_selected_letters <= static_cast<int>(non_selected_digits.size())) {
        if (has_syllable_boundaries &&
            candidate_non_selected_letters >= static_cast<int>(non_selected_digits.size())) {
            // 计算非选中音节的数字码总长度。
            // 注意：当末音节 == prev_selected_option.pinyin 时，该音节是已被选中的
            // selection，不应计入"非选中"音节的数字码统计，故从 non_selected_syllables
            // 中排除。这与 Bug-2026-07-21-v6 中从 HSLBC 移除的截断模式有本质区别——
            // 此处用于数字码长度比较（排除已选中音节是正确语义），HSLBC 则用于
            // consumed_selections 计数（排除末音节导致消费遗漏）。
            std::vector<std::string> non_selected_syllables;
            if (!comment_syllables.empty() &&
                comment_syllables.back() == prev_selected_option.pinyin) {
                non_selected_syllables = std::vector<std::string>(
                    comment_syllables.begin(), comment_syllables.end() - 1);
            } else {
                non_selected_syllables = comment_syllables;
            }
            int total_syllable_digits = 0;
            for (const auto& syl : non_selected_syllables) {
                auto code = T9PinyinMap::Instance().PinyinToDigitCode(syl);
                if (code.has_value()) {
                    total_syllable_digits += static_cast<int>(code->size());
                }
            }
            if (total_syllable_digits > static_cast<int>(non_selected_digits.size())) {
                return ComputeConsumedDigitsFromPinyin(non_selected_digits, candidate_pinyin).consumed_digits;
            }
            return candidate_non_selected_letters;
        }
        return candidate_non_selected_letters;
    }
    return ComputeConsumedDigitsFromPinyin(non_selected_digits, candidate_pinyin).consumed_digits;
}

bool LetterBufferStrategy::HandleConsumedAllNonSelected(
    T9RightCommitHandler& owner,
    T9RightCommitHandler::Context& ctx,
    bool has_syllable_boundaries,
    int candidate_text_length,
    const std::vector<std::string>& comment_syllables,
    const std::optional<std::string>& candidate_pinyin,
    const SyllableOption& prev_selected_option,
    const std::optional<std::string>& prev_selection_candidate_digits,
    const std::string& non_selected_part,
    const std::string& non_selected_digits,
    int consumed_count,
    const T9Buffer& prev_buf) {

    const T9Buffer& buf = ctx.input_buffer;
    std::string selected_pinyin = buf.selected_pinyin();
    const auto& history = ctx.state_machine.selection_history();

    if (has_syllable_boundaries &&
        candidate_text_length >= static_cast<int>(comment_syllables.size()) &&
        static_cast<int>(comment_syllables.size()) >= static_cast<int>(history.size())) {

        std::string candidate_clean = candidate_pinyin.has_value()
            ? FilterLetters(*candidate_pinyin) : "";
        if (!candidate_clean.empty()) {
            auto candidate_digit_code = T9PinyinMap::Instance().PinyinToDigitCode(candidate_clean);
            auto full_buffer_digit_code = T9PinyinMap::Instance().PinyinToDigitCode(selected_pinyin);
            if (candidate_digit_code.has_value() && full_buffer_digit_code.has_value() &&
                *candidate_digit_code == *full_buffer_digit_code) {
                owner.ClearAndEnterIdle(ctx);
                return true;
            }
            if (!comment_syllables.empty()) {
                const std::string& last_syl = comment_syllables.back();
                auto last_syl_code = T9PinyinMap::Instance().PinyinToDigitCode(last_syl);
                auto sel_code = T9PinyinMap::Instance().PinyinToDigitCode(prev_selected_option.pinyin);
                bool is_exact = last_syl_code.has_value() && sel_code.has_value() &&
                    *last_syl_code == *sel_code;
                bool is_abbrev = prev_selected_option.digit_length == 1 &&
                    last_syl_code.has_value() && sel_code.has_value() &&
                    (StartsWith(*last_syl_code, *sel_code) ||
                     StartsWith(*sel_code, *last_syl_code));
                if (is_exact || is_abbrev) {
                    owner.ClearAndEnterIdle(ctx);
                    return true;
                }
            }
        }
    }

    if (TryShengmuFallback(owner, ctx, has_syllable_boundaries, comment_syllables,
                           prev_selected_option, prev_selection_candidate_digits,
                           non_selected_part, prev_buf)) {
        return false;
    }

    // 消费全部非选中部分，只保留 prevSelectedOption
    ctx.input_buffer = owner.RemoveConsumedSelections(ctx, buf, non_selected_part);
    ctx.left_column_locked = false;
    return owner.RestorePrevState(ctx, prev_selected_option, prev_selection_candidate_digits);
}

bool LetterBufferStrategy::TryShengmuFallback(
    T9RightCommitHandler& owner,
    T9RightCommitHandler::Context& ctx,
    bool has_syllable_boundaries,
    const std::vector<std::string>& comment_syllables,
    const SyllableOption& prev_selected_option,
    const std::optional<std::string>& prev_selection_candidate_digits,
    const std::string& non_selected_part,
    const T9Buffer& prev_buf) {

    if (!has_syllable_boundaries || comment_syllables.empty()) return false;
    const T9Buffer& buf = ctx.input_buffer;
    const std::string& last_syl = comment_syllables.back();
    if (!prev_selection_candidate_digits.has_value()) return false;
    const std::string& sel_digits = *prev_selection_candidate_digits;

    std::string last_initial = last_syl.empty() ? "" : std::string(1, last_syl[0]);
    std::string opt_initial = prev_selected_option.pinyin.empty()
        ? "" : std::string(1, prev_selected_option.pinyin[0]);
    auto initial_code_opt = T9PinyinMap::Instance().PinyinToDigitCode(last_initial);
    auto opt_initial_code_opt = T9PinyinMap::Instance().PinyinToDigitCode(opt_initial);
    if (!initial_code_opt.has_value() || !opt_initial_code_opt.has_value()) return false;

    const std::string& initial_code = *initial_code_opt;
    const std::string& opt_initial_code = *opt_initial_code_opt;

    if (initial_code != opt_initial_code ||
        !StartsWith(sel_digits, initial_code) ||
        sel_digits.size() <= initial_code.size()) {
        return false;
    }

    std::string remaining_from_selected = Drop(sel_digits,
        static_cast<int>(initial_code.size()));

    // 消费全部非选中部分 + 移除最后一个选择
    ctx.input_buffer = owner.RemoveConsumedSelections(ctx, buf, non_selected_part);
    if (!ctx.input_buffer.selections.empty()) {
        std::vector<SyllableOption> new_sels = ctx.input_buffer.selections;
        new_sels.pop_back();
        ctx.input_buffer = T9Buffer(
            ctx.input_buffer.digit_sequence, new_sels,
            ctx.input_buffer.consumed_count, ctx.input_buffer.total_digits_entered);
    }

    ctx.input_buffer = T9Buffer(remaining_from_selected, {}, 0,
        static_cast<int>(remaining_from_selected.size()));
    ctx.left_column_locked = false;
    ctx.TransitionToInput(false);
    return true;
}

bool LetterBufferStrategy::HandlePartialConsumedNonSelected(
    T9RightCommitHandler& owner,
    T9RightCommitHandler::Context& ctx,
    int consumed_count,
    const std::string& non_selected_part,
    const std::string& non_selected_digits,
    const SyllableOption& prev_selected_option,
    const std::optional<std::string>& prev_selection_candidate_digits,
    const std::string& prev_confirmed_pinyin) {

    std::string remaining_pinyin_str;
    if (!prev_confirmed_pinyin.empty() && non_selected_part == prev_confirmed_pinyin) {
        auto total_digits_opt = T9PinyinMap::Instance().PinyinToDigitCode(prev_confirmed_pinyin);
        int total_digits = total_digits_opt.has_value()
            ? static_cast<int>(total_digits_opt->size())
            : static_cast<int>(prev_confirmed_pinyin.size());
        int remaining_digits = total_digits - consumed_count;
        if (remaining_digits <= 0) {
            remaining_pinyin_str = "";
        } else {
            std::string sb;
            int digits_to_skip = remaining_digits;
            for (auto it = prev_confirmed_pinyin.rbegin();
                 it != prev_confirmed_pinyin.rend() && digits_to_skip > 0; ++it) {
                sb.push_back(*it);
                auto char_code = T9PinyinMap::Instance().PinyinToDigitCode(std::string(1, *it));
                digits_to_skip -= char_code.has_value() ? 1 : 1;
            }
            std::reverse(sb.begin(), sb.end());
            remaining_pinyin_str = sb;
        }
    } else {
        remaining_pinyin_str = Drop(non_selected_digits, consumed_count);
    }

    std::string consumed_pinyin = Take(non_selected_part, consumed_count);
    ctx.input_buffer = owner.RemoveConsumedSelections(ctx, ctx.input_buffer, consumed_pinyin);
    ctx.left_column_locked = false;
    return owner.RestorePrevState(ctx, prev_selected_option,
                                   prev_selection_candidate_digits, remaining_pinyin_str);
}

bool LetterBufferStrategy::HandleDefensiveNoCommentCommit(
    T9RightCommitHandler& owner,
    T9RightCommitHandler::Context& ctx,
    int candidate_text_length,
    const SelectionContext& sel_ctx,
    const T9Buffer& prev_buf) {

    const auto& prev_selected_option = sel_ctx.prev_selected_option;
    const auto& prev_selection_candidate_digits = sel_ctx.prev_selection_candidate_digits;
    const auto& prev_confirmed_pinyin = sel_ctx.prev_confirmed_pinyin;

    const T9Buffer& buf = ctx.input_buffer;
    std::string selected_pinyin = buf.selected_pinyin();

    // 子路径 A：SELECTION 态 + history 完全覆盖 → full commit
    if (prev_selected_option.has_value() &&
        EndsWith(selected_pinyin, prev_selected_option->pinyin)) {
        const auto& history = ctx.state_machine.selection_history();
        if (!history.empty() &&
            JoinPinyins(history) == selected_pinyin &&
            candidate_text_length > 0 &&
            candidate_text_length >= static_cast<int>(history.size())) {
            owner.ClearAndEnterIdle(ctx);
            return true;
        }

        // 子路径 B：SELECTION 态 + 部分消费
        std::string non_selected_part = T9RightCommitHandler::NonSelectedPinyin(
            selected_pinyin, *prev_selected_option);
        std::string selected_digits = prev_selection_candidate_digits.value_or("");

        if (!non_selected_part.empty() && !selected_digits.empty()) {
            auto non_selected_digits_opt = T9PinyinMap::Instance().PinyinToDigitCode(non_selected_part);
            if (non_selected_digits_opt.has_value()) {
                const std::string& non_selected_digits = *non_selected_digits_opt;
                auto options = T9PinyinMap::Instance().FirstSyllableOptions(non_selected_digits, 1);
                if (!options.empty()) {
                    const auto& first_syl = options[0];
                    if (first_syl.digit_length >= 1 &&
                        first_syl.digit_length <= static_cast<int>(non_selected_digits.size())) {
                        std::string consumed_pinyin = Take(non_selected_part, first_syl.digit_length);
                        ctx.input_buffer = owner.RemoveConsumedSelections(ctx, buf, consumed_pinyin);
                        std::string remaining_after_consume =
                            Drop(non_selected_digits, first_syl.digit_length) + selected_digits;

                        if (remaining_after_consume != selected_digits) {
                            ctx.input_buffer = T9Buffer(remaining_after_consume, {}, 0,
                                static_cast<int>(remaining_after_consume.size()));
                        }
                        ctx.left_column_locked = false;
                        return owner.RestorePrevState(ctx, prev_selected_option,
                                                       prev_selection_candidate_digits, prev_confirmed_pinyin);
                    }
                }
            }
        }
    }

    // 子路径 C：FirstSyllableOptions 部分消费
    auto digits_opt = T9PinyinMap::Instance().PinyinToDigitCode(selected_pinyin);
    if (digits_opt.has_value()) {
        const std::string& digits = *digits_opt;
        auto options = T9PinyinMap::Instance().FirstSyllableOptions(digits, 1);
        int consumed_digit_count = options.empty() ? 0 : options[0].digit_length;
        if (consumed_digit_count >= 1 &&
            consumed_digit_count < static_cast<int>(digits.size())) {
            std::string remaining = Drop(digits, consumed_digit_count);
            ctx.input_buffer = T9Buffer(remaining, {}, 0,
                static_cast<int>(remaining.size()));
            ctx.left_column_locked = false;
            return owner.RestorePrevState(ctx, prev_selected_option,
                                           prev_selection_candidate_digits, prev_confirmed_pinyin);
        }
    }

    // 子路径 D：默认清空
    owner.ClearAndEnterIdle(ctx);
    return true;
}

}  // namespace rime
