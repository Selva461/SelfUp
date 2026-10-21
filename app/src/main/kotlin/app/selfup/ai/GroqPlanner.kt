package app.selfup.ai

import android.content.Context
import app.selfup.core.PlanCode
import app.selfup.core.PlanCodeResult
import app.selfup.core.PlanPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Where the Groq key lives: app-private preferences, left out of backups (see
 * res/xml/backup_rules.xml) so it never leaves the phone except in requests to Groq.
 */
class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var groqKey: String?
        get() = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().apply { if (value.isNullOrBlank()) remove(KEY) else putString(KEY, value.trim()) }.apply()
        }

    companion object {
        const val FILE = "ai"
        private const val KEY = "groq_key"
    }
}

sealed interface PlanReply {
    data class Ok(val code: String, val result: PlanCodeResult.Ok, val model: String) : PlanReply
    data class Failed(val message: String) : PlanReply
}

/**
 * Asks a model on Groq (OpenAI-compatible API, free tier) for a plan code. If the first
 * answer doesn't parse, it gets one chance to fix it, with the parser's error quoted.
 */
class GroqPlanner(private val settings: AiSettings, private val http: Http = Http()) {
    suspend fun plan(request: String, currentNumbers: List<String>): PlanReply = withContext(Dispatchers.IO) {
        val key = settings.groqKey ?: return@withContext PlanReply.Failed("Add your Groq key first.")
        try {
            val model = pickModel(key)
            val messages = JSONArray()
                .put(message("system", PlanPrompt.system()))
                .put(message("user", PlanPrompt.user(request, currentNumbers)))
            repeat(ATTEMPTS) {
                val reply = complete(key, model, messages)
                val code = PlanPrompt.extract(reply)
                when (val result = PlanCode.parse(code)) {
                    is PlanCodeResult.Ok -> return@withContext PlanReply.Ok(code, result, model)
                    is PlanCodeResult.Error -> {
                        messages.put(message("assistant", reply)).put(message("user", PlanPrompt.repair(result)))
                    }
                }
            }
            PlanReply.Failed("The model's plan didn't follow the format. Try again, or reword what you want.")
        } catch (e: HttpError) {
            PlanReply.Failed(
                when (e.code) {
                    401, 403 -> "Groq didn't accept that key. Check it at console.groq.com/keys."
                    429 -> "Groq's free limit is used up for now. Try again in a minute."
                    in 500..599 -> "Groq is having trouble right now. Try again shortly."
                    else -> "Groq returned an error (${e.code})."
                },
            )
        } catch (e: IOException) {
            PlanReply.Failed("Couldn't reach Groq. Check your connection.")
        } catch (e: JSONException) {
            PlanReply.Failed("Groq sent back something unexpected. Try again.")
        }
    }

    /** The best chat model the key can use; Groq retires models, so the list is read live. */
    private fun pickModel(key: String): String {
        val body = JSONObject(http.get("$BASE/models", key))
        val ids = body.getJSONArray("data").let { data -> List(data.length()) { data.getJSONObject(it).getString("id") } }
        return PREFERRED.firstOrNull { it in ids }
            ?: ids.firstOrNull { id -> SKIP.none { it in id.lowercase() } }
            ?: throw IOException("No chat models available")
    }

    private fun complete(key: String, model: String, messages: JSONArray): String {
        val request = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.3)
            .put("max_tokens", 1_500)
        val body = JSONObject(http.post("$BASE/chat/completions", key, request.toString()))
        return body.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun message(role: String, content: String) = JSONObject().put("role", role).put("content", content)

    companion object {
        private const val BASE = "https://api.groq.com/openai/v1"
        private const val ATTEMPTS = 2

        /** Strongest first. Unverified against Groq's current list; anything missing is skipped. */
        private val PREFERRED = listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile", "openai/gpt-oss-20b", "llama-3.1-8b-instant")
        private val SKIP = listOf("whisper", "guard", "tts", "playai", "embed", "vision")
    }
}

class HttpError(val code: Int) : IOException("HTTP $code")

/** A tiny blocking HTTP client on the platform's own HttpURLConnection, so no new dependency. */
open class Http {
    open fun get(url: String, bearer: String): String = send(url, bearer, "GET", null)

    open fun post(url: String, bearer: String, json: String): String = send(url, bearer, "POST", json)

    private fun send(url: String, bearer: String, method: String, json: String?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.setRequestProperty("Accept", "application/json")
            if (json != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(json.toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw HttpError(code)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
