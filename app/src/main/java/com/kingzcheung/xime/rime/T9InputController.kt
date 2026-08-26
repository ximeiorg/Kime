package com.kingzcheung.xime.rime

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 九键拼音输入控制器（薄包装）。
 *
 * T9 核心逻辑（数字缓冲、三态状态机、右选消费算法、撤销管理等）已整体迁移到
 * librime-t9（C++ 插件）中实现。本类仅通过 [RimeEngine] 的 JNI 接口与 C++ 层
 * 交互，并维护 UI 展示所需的 Compose 状态。
 *
 * 原 Kotlin 侧 T9Buffer / T9StateMachine / T9UndoManager / T9RightCommitHandler /
 * T9RimeBridge / T9PinyinMap 等类均已删除，由 C++ 同名组件替代。
 *
 * 异步执行模型（遗留问题 1 修复 + 方案 B 加固）：
 * - 按键处理（processKey → FlushRimeInput → 取结果）整体运行在**单线程后台队列**，
 *   引擎 compose（2-23ms，长输入可达 250ms）不阻塞 UI 线程；单线程 FIFO 保证连打
 *   按键的 C++ pending 标记顺序不乱。
 * - flush 后通过 [RimeEngine.getProcessResult] 一次 JNI 拿全量结果（input/preedit/
 *   committed/翻页），替代重复的 getComposition 调用（每键 6→5 次 JNI，全部后台）。
 * - UI 更新通过 [mainHandler] 投递到 Main 线程，**后台任务完成不依赖 Main**，
 *   避免 [awaitT9Queue]（runBlocking 等待队列）与 Main 派发之间死锁。
 * - **方案 B**：reset/clearAll 等清理入口不再在主线程 runBlocking 等待队列——
 *   本地 Compose 状态立即复位，C++ 清空排入同一队列（与后续按键保序，主线程零等待）。
 * - 唯一保留同步等待的入口是 onRightCandidateSelected（服务层在 keyProcessingDispatcher
 *   后台线程调用，需同步返回 full-commit 标志）：先 [awaitT9Queue] 等待队列排空，
 *   避免 pending 覆盖导致消费计算错乱——阻塞的是该后台线程，而非主线程。
 */
class T9InputController(
    private val rimeEngine: RimeEngine = RimeEngine.getInstance(),
    private val onCompositionRefresh: ((RimeComposition) -> Unit)? = null,
    private val onRightCommitUndone: ((Int) -> Unit)? = null,
) {
    companion object {
        private const val TAG = "T9InputController"
        const val CLEAR_COMPOSITION_ONLY = "clear_composition"
        const val CLEAR_ALL = "clear_all"
    }

    enum class LeftPanelState { IDLE, INPUT, SELECTION }

    enum class DeleteResult {
        DELETED, UNDO_CHOICE, UNDO_COMMIT, NOT_CONSUMED
    }

    /** 音节选项（原 T9PinyinMap.SyllableOption，已内联至此） */
    data class SyllableOption(
        val pinyin: String,
        val digitLength: Int
    )

    // ── 异步执行模型 ──
    private val t9Dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val t9Scope = CoroutineScope(t9Dispatcher)
    private var lastT9Job: Job? = null

    /** UI 更新投递目标（后台任务通过 post 派发，不阻塞任务完成）。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * UI 刷新代际号：每次发起刷新自增。异步 post 到达 Main 时若代际已过期则丢弃，
     * 防止「同步刷新（如右选）→ 旧的异步刷新 post 后执行」把 UI 回退到旧状态。
     */
    private var uiGeneration = 0

    /** 长按退格合并锁/状态：同一时刻至多一个退格 job，累积请求由 [pendingDeleteCount] 记录。 */
    private val deleteCoalesceLock = Any()
    private var deleteJobActive = false
    private var pendingDeleteCount = 0

    /**
     * 最近一次 flush 快照的 input 缓存（@Volatile，后台 fetchAll 写入、主线程读取）。
     * bufferString/inputBuffer 不再主线程调 [RimeEngine.getInput]（消除主线程 JNI），
     * 且与 preedit/候选/左栏同源于一次 getProcessResult → 保证 UI 元素同帧同步。
     */
    @Volatile
    private var cachedInput: String = ""

    /** 将 T9 处理任务排入单线程后台队列（FIFO 保序）。 */
    private fun enqueue(block: suspend CoroutineScope.() -> Unit) {
        val job = t9Scope.launch { block() }
        lastT9Job = job
    }

    /**
     * 数字键合帧序号：每次 onDigitPressed 在主线程递增并捕获。
     * 任务执行时若发现自己之后已有更新的数字键入队，则跳过本帧的 flush——
     * set_input 是全量语义，中间帧的 compose 结果会被后续键完全覆盖，属纯浪费
     * （长序列单次 compose 可达 400ms+，远超按键间隔，会持续堆积队列）。
     * 仅数字键合帧：左选/右选/清空等低频路径保持原语义。
     */
    @Volatile
    private var digitSeq = 0L

    /**
     * 同步等待后台队列排空（仅 onRightCandidateSelected 使用）。
     * 该方法必须且只会被**非主线程**调用（服务层 keyProcessingDispatcher），
     * 阻塞的是该后台线程而非 UI；队列空闲时立即返回。
     */
    private fun awaitT9Queue() {
        lastT9Job?.let { runBlocking { it.join() } }
    }

    /** UI 显示的缓冲区字符串 = 最近一次 flush 快照的 input（缓存，非主线程 JNI） */
    val bufferString: String get() {
        val input = cachedInput
        return when {
            input.isEmpty() && _committedText != null -> _committedText!!
            input.isEmpty() -> ""
            else -> input
        }
    }

    /** 当前输入缓冲区（供 KeyboardView 判断右选是否完整消费；缓存值，非主线程 JNI） */
    val inputBuffer: String get() = cachedInput

    var firstOptions: List<SyllableOption> by mutableStateOf(emptyList())
        private set

    var leftPanelState: LeftPanelState by mutableStateOf(LeftPanelState.IDLE)
        private set

    var selectedOption: SyllableOption? by mutableStateOf(null)
        private set

    var selectionCandidateDigits: String? by mutableStateOf(null)
        private set

    var leftColumnLocked: Boolean by mutableStateOf(false)
        private set

    val selectionHistory: List<SyllableOption> get() = _selectionHistory
    private var _selectionHistory: List<SyllableOption> = emptyList()

    private var _committedText: String? = null

    /**
     * 重置（输入会话开始/切换键盘时调用，主线程）。
     * 本地 Compose 状态立即复位；C++ 状态清空排入后台队列——与后续按键同队列保序，
     * 主线程零等待（方案 B）。
     */
    fun reset() {
        // 丢弃旧会话尚未执行的刷新 post，避免复位后被旧状态覆盖
        uiGeneration++
        _selectionHistory = emptyList()
        firstOptions = emptyList()
        leftPanelState = LeftPanelState.IDLE
        selectedOption = null
        selectionCandidateDigits = null
        leftColumnLocked = false
        _committedText = null
        cachedInput = ""
        enqueue { rimeEngine.t9ClearComposition(1) }
    }

    /**
     * 公共刷新入口（同步路径使用，低频）。
     * 一次 JNI 拿全量 composition 结果 + 左栏面板/候选，重建左栏状态。
     */
    fun updateCandidates(force: Boolean = false) {
        val data = fetchAll()
        applyCandidates(data.result, data.panel, data.options)
    }

    /**
     * 后台线程：flush 后一次取全量结果 + 左栏数据，经 [mainHandler] 投递到 Main
     * 更新 Compose 状态，并携带 composition 通知服务层刷新（避免其内部重复
     * getComposition）。post 为 fire-and-forget，任务完成不依赖 Main 线程；
     * 携带代际号，过期刷新在 Main 执行时丢弃。
     */
    private fun refreshOnBackground() {
        val data = fetchAll()
        val composition = data.result.toComposition()
        val gen = ++uiGeneration
        mainHandler.post {
            if (gen != uiGeneration) return@post
            onCompositionRefresh?.invoke(composition)
            applyCandidates(data.result, data.panel, data.options)
        }
    }

    /** 一次 flush 后取回的全部刷新数据（composition 全量 + 左栏面板 + 首音节候选）。 */
    private data class T9RefreshData(
        val result: RimeProcessResult,
        val panel: LeftPanelInfo,
        val options: List<Pair<String, Int>>,
    )

    /**
     * 后台取全量数据：一次 JNI（getProcessResult）拿 composition + T9 左栏面板 +
     * 首音节候选（C++ readCurrentState 填充），Main 线程只做解析与状态赋值。
     */
    private fun fetchAll(): T9RefreshData {
        val result = rimeEngine.getProcessResult(true)
        // 更新主线程可读的 input 快照（与 preedit/候选/左栏同源于本次 flush）
        cachedInput = result.inputText
        val panel = parseLeftPanelState(result.t9PanelState.ifEmpty { "IDLE;;;;;0" })
        val options = parseSyllableOptions(result.t9SyllableOptions)
        return T9RefreshData(result, panel, options)
    }

    /** 解析 JNI 返回的 "pinyin|digitLength,..." 首音节候选串。 */
    private fun parseSyllableOptions(raw: String): List<Pair<String, Int>> {
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size == 2) {
                Pair(parts[0], parts[1].toIntOrNull() ?: 1)
            } else null
        }
    }

    /**
     * 基于一次取回的全量结果重建左栏/面板状态（Main 线程执行）。
     * PANEL_DIGITS 已内含分词键锁定 + unassigned + selectionCandidateDigits +
     * separatorConsumedDigits 回退逻辑，确保混合输入（如 5'43）时左侧候选只显示
     * 当前数字段（5 → j/k/l）而非整段过滤结果（543 → jie/lie）。
     */
    private fun applyCandidates(
        result: RimeProcessResult,
        panel: LeftPanelInfo,
        options: List<Pair<String, Int>>,
    ) {
        val rawInput = result.inputText

        if (rawInput.isEmpty() && _committedText != result.committedText) {
            _committedText = result.committedText
        }

        // 僵尸 RC 态（如测试文档 bs6 场景：右选"策"后删完剩余数字，仅剩消费区 '23'）：
        // C++ SendToRime 有意清空 RIME input（kZombieClear，避免 preedit 拼接"策ce"），
        // 但 T9 buffer 仍处于 SELECTION（panel.state 非 IDLE，如 SELECTION/ce/23）。
        // 此时不能因 rawInput 为空误置左栏 IDLE——必须以 C++ 面板状态为准渲染。
        if (rawInput.isEmpty() && panel.state == LeftPanelState.IDLE) {
            if (leftPanelState != LeftPanelState.IDLE) {
                firstOptions = emptyList()
                leftPanelState = LeftPanelState.IDLE
                selectedOption = null
                selectionCandidateDigits = null
            }
            return
        }

        // 从数字段用 librime-t9 (C++) 计算左栏候选
        firstOptions = options.map { SyllableOption(it.first, it.second) }

        // 同步 C++ 状态机状态（分词键锁定 / 左选高亮 / 选择候选数字段）
        leftPanelState = panel.state
        selectedOption = if (panel.selectedPinyin.isNotEmpty()) {
            SyllableOption(panel.selectedPinyin, panel.selectedDigitLength)
        } else {
            null
        }
        selectionCandidateDigits = panel.selectionCandidateDigits.ifEmpty { null }
        leftColumnLocked = panel.leftLocked
    }

    /** C++ 左侧面板状态（t9GetLeftPanelState 解析结果） */
    private data class LeftPanelInfo(
        val state: LeftPanelState,
        val selectedPinyin: String,
        val selectedDigitLength: Int,
        val selectionCandidateDigits: String,
        val panelDigits: String,
        val leftLocked: Boolean,
    )

    /** 解析 "STATE;PINYIN;DIGIT_LEN;SEL_DIGITS;PANEL_DIGITS;LEFT_LOCKED" 格式 */
    private fun parseLeftPanelState(raw: String): LeftPanelInfo {
        val parts = raw.split(";")
        return LeftPanelInfo(
            state = when (parts.getOrNull(0)) {
                "INPUT" -> LeftPanelState.INPUT
                "SELECTION" -> LeftPanelState.SELECTION
                else -> LeftPanelState.IDLE
            },
            selectedPinyin = parts.getOrNull(1) ?: "",
            selectedDigitLength = parts.getOrNull(2)?.toIntOrNull() ?: 0,
            selectionCandidateDigits = parts.getOrNull(3) ?: "",
            panelDigits = parts.getOrNull(4) ?: "",
            leftLocked = parts.getOrNull(5) == "1",
        )
    }

    fun onDigitPressed(digit: String) {
        val code = digit[0].code
        val seq = ++digitSeq
        enqueue {
            rimeEngine.processKey(code, 0)
            // C++ T9Processor 采用异步 flush 模型：processKey 只标记 pending 动作，
            // 必须调用 FlushRimeInput 才能真正触发 set_input → compose。
            // 全程在后台线程执行，引擎 compose（2-23ms）不阻塞 UI 线程。
            //
            // 合帧：快速连打时，非队尾的数字键帧跳过 flush（compose 结果会被后续键的
            // 全量 set_input 完全覆盖）；队尾帧执行 flush 并刷新 UI。processKey 每键
            // 照常执行（C++ pending_input_ 追加，微秒级），不丢按键、不延迟处理。
            if (seq == digitSeq) {
                rimeEngine.t9FlushRimeInput()
                refreshOnBackground()
            }
        }
    }

    fun onChoiceSelected(option: SyllableOption) {
        enqueue {
            rimeEngine.t9SelectPinyinDirect(option.pinyin, option.digitLength)
            rimeEngine.t9FlushRimeInput()
            refreshOnBackground()
        }
    }

    /**
     * 右选候选（保持同步返回值契约，供服务层判断 full/partial commit）。
     * 由服务层在 keyProcessingDispatcher（后台线程）调用。先等待后台队列排空再执行，
     * 避免 pending 覆盖导致消费计算错乱——阻塞的是该后台线程，不冻结主线程（方案 B）。
     * 调频不在此进行——由服务层在 full commit 上屏后经 rimeEngine.t9Memorize 单独调用。
     *
     * @param candidatePinyin 候选词拼音注释（comment），null 表示无注释候选（如 emoji）
     * @param candidateText 候选词文本，供 C++ (comment, text) 双条件定位（防同注释错码）
     * @param candidateTextLength 候选词字数
     */
    fun onRightCandidateSelected(
        candidatePinyin: String? = null,
        candidateText: String? = null,
        candidateTextLength: Int = 0,
    ): Boolean {
        awaitT9Queue()
        val isFullCommit = if (candidatePinyin != null) {
            val result = rimeEngine.t9SelectCandidate(candidatePinyin, candidateText, candidateTextLength)
            rimeEngine.t9FlushRimeInput()
            result
        } else {
            false
        }
        updateFromRime()
        return isFullCommit
    }

    /**
     * 退格处理（异步，结果通过回调返回）。
     *
     * 长按退格以 ~30ms 固定频率重复派发，而每次退格含 processKey → flush → compose
     * 的 JNI 往返，耗时可能超过重复间隔。若每次都入队，t9Dispatcher 会堆积大量退格，
     * 抬手后洪水式多删。这里合并高频重复：同一时刻至多一个退格 job，累积的请求由
     * 执行中的 job 完成后顺带排空（[drainPendingDeletes]），退格速率被引擎吞吐自然限制。
     *
     * @param callback 在 Main 线程调用，参数为退格结果。
     */
    fun onDeleted(callback: (DeleteResult) -> Unit) {
        val shouldLaunch = synchronized(deleteCoalesceLock) {
            if (deleteJobActive) {
                pendingDeleteCount++
                false
            } else {
                deleteJobActive = true
                true
            }
        }
        if (!shouldLaunch) return
        enqueue {
            try {
                processDelete(callback)
            } finally {
                drainPendingDeletes(callback)
            }
        }
    }

    /** 单次退格：processKey → flush → 撤销计数 → 取全量结果 → Main 刷新 + 回调。 */
    private suspend fun processDelete(callback: (DeleteResult) -> Unit) {
        val result = rimeEngine.processKey(0xff08, 0)
        rimeEngine.t9FlushRimeInput()
        val undoneCount = rimeEngine.t9GetAndConsumeUndoneRightCommitCount()
        val data = fetchAll()
        val composition = data.result.toComposition()
        val deleteResult = if (result) DeleteResult.DELETED else DeleteResult.NOT_CONSUMED
        val gen = ++uiGeneration
        mainHandler.post {
            // 撤销计数与退格结果始终回调；仅当刷新仍是最新代际时应用，
            // 避免后续更快的按键刷新被本退格的旧状态覆盖。
            if (undoneCount > 0) {
                onRightCommitUndone?.invoke(undoneCount)
            }
            if (gen == uiGeneration) {
                onCompositionRefresh?.invoke(composition)
                applyCandidates(data.result, data.panel, data.options)
            }
            callback(deleteResult)
        }
    }

    /** 消费长按退格期间累积的额外退格请求（t9Dispatcher 上顺序执行）。 */
    private suspend fun drainPendingDeletes(callback: (DeleteResult) -> Unit) {
        while (true) {
            val shouldDrain = synchronized(deleteCoalesceLock) {
                if (pendingDeleteCount == 0) {
                    deleteJobActive = false
                    false
                } else {
                    pendingDeleteCount--
                    true
                }
            }
            if (!shouldDrain) break
            try {
                processDelete(callback)
            } catch (t: Throwable) {
                Log.e(TAG, "onDeleted drain failed", t)
            }
        }
    }

    fun forceSendToRime() {
        enqueue {
            val remaining = rimeEngine.t9GetRemainingDigits()
            if (remaining.isNotEmpty()) {
                // Directly set RIME input to remaining digits (bypass processKey/AppendDigit)
                rimeEngine.setInput(remaining)
            }
            refreshOnBackground()
        }
    }

    /**
     * 右侧候选直接提交上屏：不经过消耗算法，清空缓冲区并进入空闲状态。
     * 用于 emoji/符号等无拼音注释的候选词，RIME 引擎已匹配输入序列到候选词，
     * T9 控制器无需做音节级消费计算。
     */
    fun onRightCandidateSelectedByDirectCommit(): Boolean {
        if (inputBuffer.isEmpty()) return true
        clearAll()
        return true
    }

    fun clearRimeAndResend() {
        enqueue {
            rimeEngine.clearComposition()
            refreshOnBackground()
        }
    }

    /**
     * 清空（主线程 ResetKey/上滑手势调用）。
     * 本地 Compose 状态立即复位；C++ 状态清空排入后台队列——若队列中已有未完成的
     * 按键处理，清空在其后执行（与后续按键同队列保序），主线程零等待（方案 B）。
     */
    fun clearAll() {
        // 丢弃排队的过期刷新 post，避免清空后被旧状态覆盖
        uiGeneration++
        firstOptions = emptyList()
        leftPanelState = LeftPanelState.IDLE
        selectedOption = null
        selectionCandidateDigits = null
        leftColumnLocked = false
        _selectionHistory = emptyList()
        _committedText = null
        cachedInput = ""
        enqueue { rimeEngine.t9ClearComposition(1) }
    }

    fun onEnterCommit() { clearAll() }

    fun isSelectedOptionInCurrentCandidates(): Boolean = selectedOption in firstOptions

    private fun updateFromRime() {
        // 同步刷新（右选路径）：使后台排队的过期刷新失效，避免旧状态 post 后执行覆盖。
        uiGeneration++
        updateCandidates()
    }
}
