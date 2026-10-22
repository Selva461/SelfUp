package app.selfup.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class GroqPlannerTest {
    private lateinit var settings: AiSettings

    private class FakeHttp(private val replies: MutableList<String>, private val models: List<String>) : Http() {
        val sent = mutableListOf<JSONObject>()
        var failWith: Int? = null

        override fun get(url: String, bearer: String): String {
            failWith?.let { throw HttpError(it) }
            return JSONObject().put("data", org.json.JSONArray(models.map { JSONObject().put("id", it) })).toString()
        }

        override fun post(url: String, bearer: String, json: String): String {
            sent += JSONObject(json)
            val content = replies.removeAt(0)
            val message = JSONObject().put("role", "assistant").put("content", content)
            return JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", message))).toString()
        }
    }

    @Before
    fun setUp() {
        settings = AiSettings(ApplicationProvider.getApplicationContext())
        settings.groqKey = "gsk_test"
    }

    @Test
    fun aBadFirstAnswerGetsOneRepairRound() = runTest {
        val http = FakeHttp(
            mutableListOf(
                "Here you go!\nSELFUP PLAN 1\nGOAL record:bench:nope 80 12w",
                "SELFUP PLAN 1\nGOAL record:bench:EST_1RM 80 12w\nMON Bench 5x5 at 60 kg",
            ),
            models = listOf("whisper-large-v3", "llama-3.3-70b-versatile", "openai/gpt-oss-120b"),
        )
        val reply = GroqPlanner(settings, http).plan("bench 80", listOf("Bench press est. 1RM: 70 kg"))
        reply as PlanReply.Ok
        assertEquals("openai/gpt-oss-120b", reply.model)
        assertEquals(1, reply.result.goals.size)
        // The repair round quotes the parser's error back to the model.
        val second = http.sent[1].getJSONArray("messages")
        assertTrue(second.getJSONObject(second.length() - 1).getString("content").contains("Line 2"))
    }

    @Test
    fun aRejectedKeySaysSo() = runTest {
        val http = FakeHttp(mutableListOf(), emptyList()).apply { failWith = 401 }
        val reply = GroqPlanner(settings, http).plan("bench 80", emptyList())
        assertTrue((reply as PlanReply.Failed).message.contains("key"))
    }

    @Test
    fun noKeyNoRequest() = runTest {
        settings.groqKey = null
        val http = FakeHttp(mutableListOf(), emptyList())
        assertTrue(GroqPlanner(settings, http).plan("x", emptyList()) is PlanReply.Failed)
        assertTrue(http.sent.isEmpty())
    }

    @Test
    fun theJudgeReturnsAWorkCodeAfterChatter() = runTest {
        val http = FakeHttp(
            mutableListOf(
                "Sure.\nSELFUP WORK 1\nTITLE App\nDIFFICULTY 9\nQUALITY 3\nCOMPLETE 3\nOWNERSHIP 2",
                "SELFUP WORK 1\nTITLE App\nDIFFICULTY 3\nQUALITY 3\nCOMPLETE 3\nOWNERSHIP 2\nJUDGE groq",
            ),
            models = listOf("llama-3.3-70b-versatile"),
        )
        val reply = GroqPlanner(settings, http).judge("I built an app")
        reply as JudgeReply.Ok
        assertTrue(reply.code.startsWith("SELFUP WORK 1"))
        assertEquals(2, http.sent.size)
    }
}
