// [SemanticMemory Plugin]
package me.rerere.rikkahub.data.memory.semantic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Semantic memory plugin config.
 *
 * Embedding uses a provider [ModelType.EMBEDDING] selected via [embeddingModelId]
 * (same ModelSelector pattern as [summarizeModelId]), not independent API credentials.
 */
/** Default max core memories injected into the system prompt. */
const val DEFAULT_SEMANTIC_MAX_CORE_INJECT = 20

/** Default max characters for the injected memory body (excluding wrapper tags). */
const val DEFAULT_SEMANTIC_MAX_INJECT_CHARS = 6_000

/** Default max characters per single memory line after sanitize. */
const val DEFAULT_SEMANTIC_MAX_MEMORY_CONTENT_LEN = 500

/** 语义记忆的注入位置。 */
@Serializable
enum class SemanticInjectPosition {
    /** 并入 system prompt（旧行为）。 */
    @kotlinx.serialization.SerialName("system")
    SYSTEM,

    /** 作为独立消息插进对话历史，按 [SemanticMemoryConfig.injectDepth] 从末尾数深度。 */
    @kotlinx.serialization.SerialName("in_chat")
    IN_CHAT,
}

/** IN_CHAT 注入时那条消息的角色，和世界书/模式注入那套一样可以自由选。 */
@Serializable
enum class SemanticInjectRole {
    @kotlinx.serialization.SerialName("system")
    SYSTEM,

    @kotlinx.serialization.SerialName("user")
    USER,

    @kotlinx.serialization.SerialName("assistant")
    ASSISTANT,
}

@Serializable
data class SemanticMemoryConfig(
    val enabled: Boolean = false,
    /**
     * 注入位置。SYSTEM 会把记忆并进 system prompt，改一次记忆就让整个前缀缓存失效；
     * IN_CHAT 只影响插入点之后的 token，与华灯的「In-chat depth」思路一致。
     */
    val injectPosition: SemanticInjectPosition = SemanticInjectPosition.SYSTEM,
    /**
     * IN_CHAT 时的深度：1 = 插在最后一条消息之前，2 = 插在倒数第二条之前，依此类推。
     * 0 表示放在末尾。默认对齐华灯常用的 System + Depth 1~2。
     */
    val injectDepth: Int = 1,
    /**
     * IN_CHAT 时的消息角色。默认 SYSTEM：ChatCompletions 会原样透传中段 system。
     * 换 Claude / Google / OpenAI ResponseAPI 时它们只认第一条 system、其余会被丢掉，
     * 那种情况把这里改成 USER 或 ASSISTANT。
     */
    val injectRole: SemanticInjectRole = SemanticInjectRole.SYSTEM,
    /** Provider model UUID with [me.rerere.ai.provider.ModelType.EMBEDDING]; null = not configured. */
    val embeddingModelId: Uuid? = null,
    val topK: Int = 10,
    val similarityThreshold: Float = 0.3f,
    val enableFallbackKeyword: Boolean = true,
    val summarizeInterval: Int = 50,
    val autoSummarizeEnabled: Boolean = true,
    val autoSummarizeMessageCount: Int = 20,
    val maxMemoriesPerSummary: Int = 5,
    val summarizeModelId: Uuid? = null,
    val summarizePrompt: String? = null,
    val maxMemoriesPerAssistant: Int = 2000,
    val autoEvictionEnabled: Boolean = true,
    /**
     * Max core memories injected into system prompt.
     * `null` = no cap (all cores from recall).
     */
    val maxCoreInject: Int? = DEFAULT_SEMANTIC_MAX_CORE_INJECT,
    /**
     * Max characters for injected memory lines (body budget).
     * `null` = no char budget.
     */
    val maxInjectChars: Int? = DEFAULT_SEMANTIC_MAX_INJECT_CHARS,
    /**
     * Max characters per memory content line after sanitization.
     * `null` = no per-item truncate.
     */
    val maxMemoryContentLen: Int? = DEFAULT_SEMANTIC_MAX_MEMORY_CONTENT_LEN,
)

enum class SummarizeStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
}

/**
 * Runtime debug counters. StateFlow-backed so Settings UI recomposes (AC14).
 */
data class SemanticMemoryStatsSnapshot(
    val embeddingCallCount: Int = 0,
    val embeddingSuccessCount: Int = 0,
    val embeddingFailCount: Int = 0,
    val lastEmbeddingError: String? = null,
    val lastEmbeddingDim: Int? = null,
    val lastRecallCount: Int = 0,
    val lastRecallQuery: String? = null,
    val totalMemories: Int = 0,
    val lastAutoSummarizeAt: Long = 0,
    val lastAutoSummarizeNew: Int = 0,
    val lastAutoSummarizeUpdated: Int = 0,
    val lastAutoSummarizeError: String? = null,
    val autoSummarizeCount: Int = 0,
    val summarizeStatus: SummarizeStatus = SummarizeStatus.IDLE,
    val lastRecallAt: Long = 0,
    val lastRecallFallback: Boolean = false,
    val recallCount: Int = 0,
)

object SemanticMemoryStats {
    private val _state = MutableStateFlow(SemanticMemoryStatsSnapshot())
    val state: StateFlow<SemanticMemoryStatsSnapshot> = _state.asStateFlow()

    val snapshot: SemanticMemoryStatsSnapshot get() = _state.value

    fun update(block: (SemanticMemoryStatsSnapshot) -> SemanticMemoryStatsSnapshot) {
        _state.update(block)
    }

    fun reset() {
        _state.value = SemanticMemoryStatsSnapshot()
    }
}
