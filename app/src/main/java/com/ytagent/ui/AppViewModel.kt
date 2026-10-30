package com.ytagent.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytagent.agent.ClaudeAgent
import com.ytagent.data.ChannelInfo
import com.ytagent.data.ChannelInsights
import com.ytagent.data.Insights
import com.ytagent.data.VideoInfo
import com.ytagent.data.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(val fromUser: Boolean, val text: String, val isError: Boolean = false)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var youtubeKey by mutableStateOf(prefs.getString(KEY_YT, "").orEmpty())
    var anthropicKey by mutableStateOf(prefs.getString(KEY_ANTHROPIC, "").orEmpty())
    var channelInput by mutableStateOf(prefs.getString(KEY_CHANNEL, "").orEmpty())

    var channel by mutableStateOf<ChannelInfo?>(null)
        private set
    var videos by mutableStateOf<List<VideoInfo>>(emptyList())
        private set
    var insights by mutableStateOf<ChannelInsights?>(null)
        private set
    var loadingChannel by mutableStateOf(false)
        private set
    var channelError by mutableStateOf<String?>(null)
        private set

    val chat = mutableStateListOf<ChatMessage>()
    var agentBusy by mutableStateOf(false)
        private set

    private var agent: ClaudeAgent? = null

    val isConfigured: Boolean
        get() = youtubeKey.isNotBlank() && anthropicKey.isNotBlank() && channelInput.isNotBlank()

    init {
        if (isConfigured) refreshChannel()
    }

    fun saveSettings() {
        prefs.edit()
            .putString(KEY_YT, youtubeKey.trim())
            .putString(KEY_ANTHROPIC, anthropicKey.trim())
            .putString(KEY_CHANNEL, channelInput.trim())
            .apply()
        agent = null
        refreshChannel()
    }

    fun refreshChannel() {
        if (loadingChannel) return
        loadingChannel = true
        channelError = null
        viewModelScope.launch {
            try {
                val (ch, vids) = withContext(Dispatchers.IO) {
                    val yt = YouTubeClient(youtubeKey.trim())
                    val ch = yt.fetchChannel(channelInput)
                    ch to yt.fetchRecentVideos(ch.uploadsPlaylistId)
                }
                channel = ch
                videos = vids
                val ins = Insights.compute(vids)
                insights = ins
                // New data means a new conversation: the agent must not reason over stale numbers.
                agentOrNull()?.setChannelContext(Insights.buildContext(ch, vids, ins))
                chat.clear()
            } catch (e: Exception) {
                channelError = e.message ?: e.toString()
            } finally {
                loadingChannel = false
            }
        }
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || agentBusy) return
        val a = agentOrNull()
        if (a == null) {
            chat += ChatMessage(false, "Укажите ключ Anthropic API в настройках.", isError = true)
            return
        }
        chat += ChatMessage(true, q)
        agentBusy = true
        viewModelScope.launch {
            try {
                val answer = withContext(Dispatchers.IO) { a.ask(q) }
                chat += ChatMessage(false, answer)
            } catch (e: Exception) {
                chat += ChatMessage(false, e.message ?: e.toString(), isError = true)
            } finally {
                agentBusy = false
            }
        }
    }

    fun newConversation() {
        val ch = channel
        val ins = insights
        if (ch != null && ins != null) agentOrNull()?.setChannelContext(Insights.buildContext(ch, videos, ins))
        chat.clear()
    }

    private fun agentOrNull(): ClaudeAgent? {
        if (anthropicKey.isBlank()) return null
        return agent ?: ClaudeAgent(anthropicKey.trim()).also { created ->
            agent = created
            val ch = channel
            val ins = insights
            if (ch != null && ins != null) created.setChannelContext(Insights.buildContext(ch, videos, ins))
        }
    }

    private companion object {
        const val KEY_YT = "youtube_api_key"
        const val KEY_ANTHROPIC = "anthropic_api_key"
        const val KEY_CHANNEL = "channel"
    }
}
