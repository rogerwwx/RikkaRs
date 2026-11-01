package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.UIMessage

/**
 * 后台任务专用的流式生成：标题、建议、压缩摘要、语义记忆总结、翻译、Hook 判定都改走这里。
 *
 * 这些调用的输出可能长达数分钟。非流式只发一次请求、等一次完整响应，任何一个中转的
 * 120 秒上限、或任意一段没有任何字节的静默读，都会把整次调用判成失败（语义记忆总结
 * 就是这么丢掉 2 分钟结果的）。流式收集时数据边到边拼，只要上游持续出字就不会超时；
 * 返回值形状与 [Provider.generateText] 一致，调用点可以原样替换。
 */
suspend fun <T : ProviderSetting> Provider<T>.generateStreaming(
    providerSetting: T,
    messages: List<UIMessage>,
    params: TextGenerationParams,
): TextGenerationResult {
    val model = params.model
    val handler = StreamChunkHandler(model)
    var accumulated = listOf(
        UIMessage(
            role = MessageRole.ASSISTANT,
            parts = emptyList(),
            modelId = model.id,
        ),
    )
    streamText(providerSetting, messages, params).collect { chunk ->
        accumulated = handler.handle(accumulated, chunk)
    }
    val message = accumulated.last()
    return TextGenerationResult(
        id = message.id.toString(),
        model = model.modelId,
        message = message,
        usage = message.usage,
    )
}
