package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * 引号与括号内文字染色（移植自华灯的「文本染色」）。
 *
 * 与华灯一致：引号认半角、全角、«»、＂＂；括号认全角圆括号与各类书名号；
 * 不做跨行匹配；行内代码不参与染色。
 *
 * 与华灯不同：华灯往 markdown 源码里注入 span 标签，因为它的渲染器走的是
 * com.github.rikkahub:markdown 的主题管线；RikkaRs 的聊天渲染是手写的
 * MarkdownBlock/Paragraph，所以在 AnnotatedString 上补 SpanStyle 更直接，
 * 也不需要为代码块做分段扫描。
 */

/** 括号类符号的首字符；命中即用括号色，其余用引号色。半角左括号也算括号。 */
internal const val TEXT_COLOR_BRACKET_OPENERS = "（【「『《‹〔｛{("

internal val TEXT_COLOR_REGEX = Regex(
    "（(.+?)）|【(.+?)】|「(.+?)」|『(.+?)』|《(.+?)》|‹(.+?)›|〔(.+?)〕|｛(.+?)｝|\\{(.+?)\\}" +
        "|\\((.+?)\\)|\"(.+?)\"|\u201C(.+?)\u201D|\u00AB(.+?)\u00BB|\uFF02(.+?)\uFF02"
)

/** 支持 #RRGGBB 与 #AARRGGBB；空串或格式不对时返回 fallback。 */
internal fun parseTextColor(value: String, fallback: Color): Color {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return fallback
    val parsed = hex.toLongOrNull(16) ?: return fallback
    return if (hex.length == 6) Color(0xFF000000L or parsed) else Color(parsed)
}

/**
 * 给引号内与括号内的文字补颜色。行内代码用等宽字体标记，其覆盖区间整段跳过，
 * 否则代码片段里的引号也会被染色。
 */
internal fun AnnotatedString.withQuoteBracketColors(quote: Color, bracket: Color): AnnotatedString {
    if (length == 0) return this
    val codeRanges = spanStyles.filter { it.item.fontFamily != null }.map { it.start until it.end }
    val targets = TEXT_COLOR_REGEX.findAll(text).filter { match ->
        codeRanges.none { range ->
            match.range.first < range.last + 1 && match.range.last + 1 > range.first
        }
    }.toList()
    if (targets.isEmpty()) return this
    return buildAnnotatedString {
        append(this@withQuoteBracketColors)
        targets.forEach { match ->
            val color = if (match.value.first() in TEXT_COLOR_BRACKET_OPENERS) bracket else quote
            addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
        }
    }
}
