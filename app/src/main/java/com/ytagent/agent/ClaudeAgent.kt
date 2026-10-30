package com.ytagent.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaMessageParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive
import com.anthropic.models.beta.messages.MessageCreateParams

class AgentException(message: String) : Exception(message)

/**
 * YouTube strategist backed by Claude. Keeps the conversation in memory so follow-up
 * questions see the channel data and earlier answers. Calls block - run them off the main thread.
 */
class ClaudeAgent(apiKey: String) {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    // Full assistant turns (including thinking blocks) are appended unchanged via toParam().
    private val history = mutableListOf<BetaMessageParam>()
    private var channelContext: String? = null

    /** Starts a fresh conversation about the given channel dossier. */
    fun setChannelContext(context: String) {
        channelContext = context
        history.clear()
    }

    fun ask(question: String): String {
        val text = if (history.isEmpty() && channelContext != null) {
            "Данные моего канала:\n\n$channelContext\n\n---\n\n$question"
        } else {
            question
        }
        history += BetaMessageParam.ofUser(text)

        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .thinking(BetaThinkingConfigAdaptive.builder().build())
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
            // Server-side fallback: if this model declines, the API retries on a suitable model.
            .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
            .fallbacksDefault()
            .messages(history)
            .build()

        val response = try {
            client.beta().messages().create(params)
        } catch (e: UnauthorizedException) {
            history.removeAt(history.lastIndex)
            throw AgentException("Неверный ключ Anthropic API. Проверьте его в настройках.")
        } catch (e: RateLimitException) {
            history.removeAt(history.lastIndex)
            throw AgentException("Слишком много запросов к Claude. Подождите минуту и повторите.")
        } catch (e: AnthropicServiceException) {
            history.removeAt(history.lastIndex)
            throw AgentException("Ошибка Claude API (${e.statusCode()}): ${e.message}")
        } catch (e: AnthropicIoException) {
            history.removeAt(history.lastIndex)
            throw AgentException("Нет связи с Claude API. Проверьте интернет.")
        }

        if (response.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
            history.removeAt(history.lastIndex)
            val why = response.stopDetails().flatMap { it.explanation() }.orElse("")
            throw AgentException("Claude отклонил запрос. $why".trim())
        }

        history += response.toParam()
        return response.content()
            .mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
            .joinToString("\n")
            .trim()
    }

    companion object {
        const val MODEL = "claude-opus-5-5"

        private val SYSTEM_PROMPT = """
            Ты — персональный продюсер и стратег YouTube-канала пользователя. Ты ведёшь проект вместе с ним:
            анализируешь статистику, предлагаешь идеи контента, планируешь публикации и продвижение.

            Как работать:
            - Опирайся на реальные цифры канала из присланных данных: называй конкретные видео, просмотры,
              закономерности. Если данных мало для вывода, прямо скажи об этом.
            - Время всегда указывай в локальном часовом поясе пользователя, конкретными слотами
              (день недели + время), и объясняй, почему именно так. Учитывай, что ролик лучше выпускать
              за 1–2 часа до пика активности зрителей, а данные по слотам — это прошлые публикации канала.
            - Идеи давай готовыми к производству: рабочее название, крючок первых 5–15 секунд, формат
              (Shorts или длинное видео), почему это сработает именно на этом канале.
            - Советы по продвижению — практичные и бесплатные в первую очередь: названия и превью, первые
              строки описания, теги, Shorts как воронка, плейлисты, конечные заставки, комьюнити, коллаборации,
              кросс-постинг. Не советуй накрутки и нарушения правил YouTube.
            - Отвечай на русском, структурированно и без воды: короткие заголовки и списки.
              Не используй таблицы — ответ читают с экрана телефона.
        """.trimIndent()
    }
}
