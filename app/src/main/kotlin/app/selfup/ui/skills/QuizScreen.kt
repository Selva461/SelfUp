package app.selfup.ui.skills

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.selfup.core.Concept
import app.selfup.core.Question
import app.selfup.core.Performance
import app.selfup.core.SkillRules
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.PrimaryButton
import app.selfup.ui.components.SystemDial
import app.selfup.ui.components.SystemGroup
import app.selfup.ui.system.Glow
import app.selfup.ui.system.PanelShape
import app.selfup.ui.system.hairline
import app.selfup.ui.system.systemPanel
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.Surfaces
import app.selfup.ui.theme.SystemColors

private val Right = SystemColors.Success
private val Wrong = SystemColors.Danger

/** A question as shown: options shuffled, with the correct one's new position. */
private data class Shown(val question: Question, val options: List<String>, val answer: Int)

private fun deal(concept: Concept): List<Shown> = concept.questions.shuffled().take(SkillRules.QUIZ_LENGTH).map { q ->
    val order = q.options.indices.shuffled()
    Shown(q, order.map { q.options[it] }, order.indexOf(q.answer))
}

private sealed interface Step {
    data class Card(val concept: Concept) : Step
    data class Ask(val concept: Concept, val index: Int) : Step
    data class Score(val concept: Concept, val correct: Int) : Step
    data object Done : Step
}

/**
 * Runs a study session: for each concept, its card first if it's new, then a 5-question
 * quiz with instant feedback. Each concept's score is saved as soon as its quiz ends, so
 * leaving halfway keeps what was finished.
 */
@Composable
fun QuizScreen(
    concepts: List<Concept>,
    learnFirst: Set<String>,
    accent: Color,
    onRead: (Concept) -> Unit,
    onScored: (Concept, correct: Int, total: Int) -> Unit,
    onClose: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val decks = remember(concepts) { concepts.associate { it.id to deal(it) } }
    val results = remember { mutableStateListOf<Pair<Concept, Int>>() }
    var conceptIndex by remember { mutableIntStateOf(0) }
    var step by remember {
        mutableStateOf<Step>(concepts.firstOrNull()?.let { if (it.id in learnFirst) Step.Card(it) else Step.Ask(it, 0) } ?: Step.Done)
    }
    var correctSoFar by remember { mutableIntStateOf(0) }

    fun startConcept(i: Int) {
        conceptIndex = i
        correctSoFar = 0
        step = concepts.getOrNull(i)?.let { if (it.id in learnFirst) Step.Card(it) else Step.Ask(it, 0) } ?: Step.Done
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close session") }
            SessionProgress(concepts.size, conceptIndex, step, accent, Modifier.weight(1f))
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(160)))
            },
            label = "quiz",
            modifier = Modifier.weight(1f),
        ) { current ->
            when (current) {
                is Step.Card -> CardStep(current.concept, accent) {
                    onRead(current.concept)
                    step = Step.Ask(current.concept, 0)
                }
                is Step.Ask -> AskStep(
                    shown = decks.getValue(current.concept.id)[current.index],
                    number = current.index + 1,
                    accent = accent,
                    onAnswered = { right ->
                        haptics.performHapticFeedback(if (right) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
                        if (right) correctSoFar++
                    },
                    onNext = {
                        val next = current.index + 1
                        if (next < SkillRules.QUIZ_LENGTH) {
                            step = Step.Ask(current.concept, next)
                        } else {
                            onScored(current.concept, correctSoFar, SkillRules.QUIZ_LENGTH)
                            results += current.concept to correctSoFar
                            step = Step.Score(current.concept, correctSoFar)
                        }
                    },
                )
                is Step.Score -> ScoreStep(current.concept, current.correct, accent, isLast = conceptIndex == concepts.lastIndex) {
                    startConcept(conceptIndex + 1)
                }
                Step.Done -> DoneStep(results, accent, onClose)
            }
        }
    }
}

@Composable
private fun SessionProgress(concepts: Int, index: Int, step: Step, accent: Color, modifier: Modifier) {
    val within = when (step) {
        is Step.Card -> 0f
        is Step.Ask -> step.index.toFloat() / SkillRules.QUIZ_LENGTH
        is Step.Score, Step.Done -> 1f
    }
    val total = ((index + within) / concepts.coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(
        modifier
            .padding(end = 12.dp)
            .height(4.dp)
            .background(accent.copy(alpha = 0.14f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(if (step == Step.Done) 1f else total)
                .height(4.dp)
                .background(accent),
        )
    }
}

@Composable
private fun CardStep(concept: Concept, accent: Color, onContinue: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("NEW CONCEPT", style = SectionLabel, color = accent)
        Text(concept.title, style = MaterialTheme.typography.headlineLarge)
        SystemGroup {
            concept.card.forEachIndexed { i, line ->
                if (i > 0) GroupSeparator(start = 16.dp)
                Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
        }
        PrimaryButton("I've read it, quiz me", accent, onContinue)
    }
}

@Composable
private fun AskStep(shown: Shown, number: Int, accent: Color, onAnswered: (Boolean) -> Unit, onNext: () -> Unit) {
    var picked by remember(shown) { mutableStateOf<Int?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("QUESTION $number OF ${SkillRules.QUIZ_LENGTH}", style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(shown.question.prompt, style = MaterialTheme.typography.titleLarge)
        shown.question.code?.let { CodeBlock(it) }
        shown.options.forEachIndexed { i, option ->
            OptionRow(
                text = option,
                state = when {
                    picked == null -> OptionState.Open
                    i == shown.answer -> OptionState.Right
                    i == picked -> OptionState.Wrong
                    else -> OptionState.Dim
                },
                onClick = {
                    if (picked == null) {
                        picked = i
                        onAnswered(i == shown.answer)
                    }
                },
            )
        }
        picked?.let { p ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .systemPanel(accent = if (p == shown.answer) Right else Wrong, glow = Glow.Soft)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (p == shown.answer) "Correct" else "Not quite", style = MaterialTheme.typography.titleSmall, color = if (p == shown.answer) Right else Wrong)
                Text(shown.question.explanation, style = MaterialTheme.typography.bodyMedium)
            }
            PrimaryButton(if (number == SkillRules.QUIZ_LENGTH) "See score" else "Next", accent, onNext)
        }
    }
}

private enum class OptionState { Open, Right, Wrong, Dim }

@Composable
private fun OptionRow(text: String, state: OptionState, onClick: () -> Unit) {
    val shape = PanelShape
    val fill by animateColorAsState(
        when (state) {
            OptionState.Right -> Right.copy(alpha = 0.16f)
            OptionState.Wrong -> Wrong.copy(alpha = 0.16f)
            else -> Surfaces.Fill
        },
        label = "fill",
    )
    val edge = when (state) {
        OptionState.Right -> Modifier.border(1.5.dp, Right, shape)
        OptionState.Wrong -> Modifier.border(1.5.dp, Wrong, shape)
        else -> Modifier.hairline(shape)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (state == OptionState.Dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .background(fill, shape)
            .then(edge)
            .clickable(enabled = state == OptionState.Open, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun CodeBlock(code: String) {
    Text(
        code,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = Color(0xFFDCE3F2),
        softWrap = false,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF030A14), PanelShape)
            .hairline(PanelShape)
            .horizontalScroll(rememberScrollState())
            .padding(14.dp),
    )
}

@Composable
private fun ScoreStep(concept: Concept, correct: Int, accent: Color, isLast: Boolean, onContinue: () -> Unit) {
    val fraction = correct.toDouble() / SkillRules.QUIZ_LENGTH
    val verdict = when {
        fraction >= SkillRules.STRONG -> "Strong. Do it again in a few days to make it Solid."
        fraction >= SkillRules.PASS -> "Passed. It comes back for review soon."
        else -> "Not yet. It comes back tomorrow; reread the card first."
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 40.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(concept.title.uppercase(), style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(contentAlignment = Alignment.Center) {
            SystemDial(fraction.toFloat(), if (fraction >= SkillRules.PASS) accent else Wrong, size = 160.dp, stroke = 14.dp)
            Text("$correct/${SkillRules.QUIZ_LENGTH}", style = MaterialTheme.typography.displayMedium)
        }
        Text(verdict, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${Performance.label(fraction)}. Full XP only for ${SkillRules.QUIZ_LENGTH}/${SkillRules.QUIZ_LENGTH}; the first try of the day is the one that counts.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f))
        PrimaryButton(if (isLast) "Finish" else "Next concept", accent, onContinue)
    }
}

@Composable
private fun DoneStep(results: List<Pair<Concept, Int>>, accent: Color, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("SESSION COMPLETE", style = MaterialTheme.typography.headlineLarge)
        if (results.isNotEmpty()) SystemGroup {
            results.forEachIndexed { i, (concept, correct) ->
                if (i > 0) GroupSeparator(start = 16.dp)
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(concept.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        "$correct/${SkillRules.QUIZ_LENGTH}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (correct.toDouble() / SkillRules.QUIZ_LENGTH >= SkillRules.PASS) accent else Wrong,
                    )
                }
            }
        }
        Box(Modifier.weight(1f))
        PrimaryButton("Done", accent, onClose)
    }
}
