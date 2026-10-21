package app.selfup.ui.mind

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.selfup.core.DigitSpan
import app.selfup.core.GridRecall
import app.selfup.core.NBack
import app.selfup.core.Reaction
import app.selfup.core.SpeedMath
import app.selfup.ui.components.pressScale
import app.selfup.ui.glass.frosted
import app.selfup.ui.glass.hairline
import app.selfup.ui.skills.PrimaryButton
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.Surfaces
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val Right = Color(0xFF5EE29A)
internal val Wrong = Color(0xFFFF6A5E)

private val BigNumber = androidx.compose.ui.text.TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)

/** A phone-style keypad: 1–9, then delete and 0. */
@Composable
private fun NumberPad(onDigit: (Int) -> Unit, onDelete: () -> Unit, enabled: Boolean = true) {
    val haptics = LocalHapticFeedback.current
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(null, 0, -1))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    if (key == null) {
                        Box(Modifier.weight(1f))
                    } else {
                        val interaction = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(60.dp)
                                .pressScale(interaction, 0.94f)
                                .background(Surfaces.Fill, RoundedCornerShape(18.dp))
                                .hairline(RoundedCornerShape(18.dp))
                                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (key == -1) onDelete() else onDigit(key)
                                }
                                .semantics { contentDescription = if (key == -1) "Delete" else "$key" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (key == -1) "⌫" else "$key", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

/** Small "best so far · misses" line above each game. */
@Composable
private fun GameStatus(left: String, right: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(left, style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(right, style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MissDots(misses: Int, max: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(max) { i ->
            Box(Modifier.size(8.dp).background(if (i < misses) Wrong else Color.White.copy(alpha = 0.14f), RoundedCornerShape(percent = 50)))
        }
    }
}

// ─── Digit span ───────────────────────────────────────────────────────────────

private enum class SpanPhase { Show, Input, Feedback }

@Composable
fun DigitSpanGame(accent: Color, onDone: (Double) -> Unit) {
    val done by rememberUpdatedState(onDone)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val run = remember { DigitSpan() }
    var round by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf(emptyList<Int>()) }
    var showing by remember { mutableIntStateOf(-1) }
    var phase by remember { mutableStateOf(SpanPhase.Show) }
    var lastRight by remember { mutableStateOf(false) }
    var misses by remember { mutableIntStateOf(0) }
    val typed = remember { mutableStateListOf<Int>() }

    LaunchedEffect(round) {
        digits = run.next()
        typed.clear()
        phase = SpanPhase.Show
        delay(700)
        digits.indices.forEach { i ->
            showing = i
            delay(750)
            showing = -1
            delay(250)
        }
        phase = SpanPhase.Input
    }

    fun submit() {
        lastRight = run.answer(digits, typed.toList())
        misses = if (lastRight) 0 else misses + 1
        haptics.performHapticFeedback(if (lastRight) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        phase = SpanPhase.Feedback
        scope.launch {
            delay(1_100)
            if (run.isOver) done(run.best.toDouble()) else round++
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        GameStatus("${digits.size} DIGITS", "BEST ${run.best}")
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when (phase) {
                SpanPhase.Show -> AnimatedContent(
                    targetState = showing,
                    transitionSpec = { (fadeIn(tween(90)) + scaleIn(tween(160), initialScale = 0.9f)) togetherWith fadeOut(tween(90)) },
                    label = "digit",
                ) { i ->
                    Text(if (i >= 0) "${digits[i]}" else " ", style = BigNumber, color = accent)
                }
                SpanPhase.Input, SpanPhase.Feedback -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        digits.indices.forEach { i ->
                            val color = when {
                                phase == SpanPhase.Feedback && lastRight -> Right
                                phase == SpanPhase.Feedback -> if (typed.getOrNull(i) == digits[i]) MaterialTheme.colorScheme.onSurface else Wrong
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Box(
                                Modifier
                                    .width(28.dp)
                                    .height(44.dp)
                                    .background(if (i == typed.size && phase == SpanPhase.Input) accent.copy(alpha = 0.16f) else Surfaces.Fill, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(typed.getOrNull(i)?.toString() ?: "", style = MaterialTheme.typography.titleLarge, color = color)
                            }
                        }
                    }
                    if (phase == SpanPhase.Feedback) {
                        Text(
                            if (lastRight) "Correct" else "It was ${digits.joinToString("")}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (lastRight) Right else Wrong,
                        )
                    } else {
                        MissDots(misses, DigitSpan.MAX_MISSES)
                    }
                }
            }
        }
        NumberPad(
            enabled = phase == SpanPhase.Input,
            onDigit = {
                if (typed.size < digits.size) {
                    typed += it
                    if (typed.size == digits.size) submit()
                }
            },
            onDelete = { if (typed.isNotEmpty()) typed.removeAt(typed.lastIndex) },
        )
    }
}

// ─── N-back ───────────────────────────────────────────────────────────────────

private const val N_BACK_BLOCKS = 3

private sealed interface BackPhase {
    data class Intro(val n: Int, val block: Int) : BackPhase
    data class Trials(val n: Int, val block: Int) : BackPhase
    data class Scored(val n: Int, val accuracy: Double) : BackPhase
}

@Composable
fun NBackGame(startN: Int, accent: Color, onDone: (Double) -> Unit) {
    val done by rememberUpdatedState(onDone)
    var phase by remember { mutableStateOf<BackPhase>(BackPhase.Intro(startN.coerceAtLeast(1), 0)) }
    var blocks by remember { mutableIntStateOf(0) }
    var bestPassed by remember { mutableIntStateOf(0) }

    when (val p = phase) {
        is BackPhase.Intro -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GameStatus("BLOCK ${p.block + 1} OF $N_BACK_BLOCKS", if (bestPassed > 0) "PASSED $bestPassed-BACK" else "")
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${p.n}-back", style = BigNumber.copy(fontSize = 72.sp), color = accent)
                    Text(
                        if (p.n == 1) "Tap Match when the letter is the same as the one just before it."
                        else "Tap Match when the letter is the same as the one ${p.n} letters ago.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            PrimaryButton("Start block", accent, { phase = BackPhase.Trials(p.n, p.block) })
        }
        is BackPhase.Trials -> NBackBlock(p.n, accent) { accuracy ->
            if (accuracy >= NBack.PASS) bestPassed = maxOf(bestPassed, p.n)
            blocks++
            phase = BackPhase.Scored(p.n, accuracy)
        }
        is BackPhase.Scored -> {
            val passed = p.accuracy >= NBack.PASS
            val last = blocks >= N_BACK_BLOCKS
            val next = NBack.nextN(p.n, p.accuracy)
            LaunchedEffect(p) {
                delay(1_800)
                if (last) done(bestPassed.toDouble()) else phase = BackPhase.Intro(next, blocks)
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${(p.accuracy * 100).toInt()}%", style = BigNumber.copy(fontSize = 72.sp), color = if (passed) Right else MaterialTheme.colorScheme.onSurface)
                    Text(
                        when {
                            last -> if (passed) "Passed ${p.n}-back." else "Block done."
                            next > p.n -> "Passed. Next block: $next-back."
                            next < p.n -> "Tough one. Next block: $next-back."
                            else -> "Close. Same N again."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NBackBlock(n: Int, accent: Color, onFinished: (Double) -> Unit) {
    val finished by rememberUpdatedState(onFinished)
    val haptics = LocalHapticFeedback.current
    val seq = remember(n) { NBack.block(n) }
    val pressed = remember(n) { mutableStateListOf(*Array(seq.size) { false }) }
    var trial by remember(n) { mutableIntStateOf(-1) }
    var visible by remember(n) { mutableStateOf(false) }

    LaunchedEffect(seq) {
        delay(600)
        seq.indices.forEach { i ->
            trial = i
            visible = true
            delay(500)
            visible = false
            delay(2_000)
        }
        finished(NBack.accuracy(seq, n, pressed.toList()))
    }

    val current = trial.coerceAtLeast(0)
    val canPress = trial >= n && !pressed[current]
    val feedback = when {
        trial < 0 || !pressed[current] -> null
        NBack.isTarget(seq, n, current) -> Right
        else -> Wrong
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GameStatus("${n}-BACK", "${(trial + 1).coerceAtLeast(0)} / ${seq.size}")
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(180.dp)
                    .frosted(RoundedCornerShape(40.dp), tint = (feedback ?: accent).copy(alpha = if (feedback != null) 0.16f else 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (visible && trial >= 0) "${seq[trial]}" else "", style = BigNumber, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        PrimaryButton(
            "Match",
            accent,
            onClick = {
                pressed[current] = true
                haptics.performHapticFeedback(if (NBack.isTarget(seq, n, current)) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
            },
            enabled = canPress,
        )
    }
}

// ─── Speed math ───────────────────────────────────────────────────────────────

@Composable
fun SpeedMathGame(accent: Color, onDone: (Double) -> Unit) {
    val done by rememberUpdatedState(onDone)
    val haptics = LocalHapticFeedback.current
    var streak by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(SpeedMath.problem(0)) }
    var typed by remember { mutableStateOf("") }
    var flash by remember { mutableStateOf<Color?>(null) }
    var remainingMs by remember { mutableLongStateOf(SpeedMath.SECONDS * 1_000L) }
    var running by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val total = SpeedMath.SECONDS * 1_000_000_000L
        val start = withFrameNanos { it }
        while (true) {
            val elapsed = withFrameNanos { it } - start
            remainingMs = ((total - elapsed) / 1_000_000).coerceAtLeast(0)
            if (elapsed >= total) break
        }
        running = false
        delay(500)
        done(correct.toDouble())
    }
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(220)
            flash = null
        }
    }

    fun type(digit: Int) {
        if (!running) return
        val next = typed + digit
        val target = problem.answer.toString()
        when {
            next == target -> {
                correct++
                streak++
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                flash = Right
                problem = SpeedMath.problem(SpeedMath.levelFor(streak))
                typed = ""
            }
            next.length >= target.length -> {
                streak = 0
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                flash = Wrong
                problem = SpeedMath.problem(0)
                typed = ""
            }
            else -> typed = next
        }
    }

    val ringColor by animateColorAsState(flash ?: Color.Transparent, tween(120), label = "flash")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GameStatus("${correct} CORRECT", "${(remainingMs + 999) / 1000}s")
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(percent = 50)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(remainingMs / (SpeedMath.SECONDS * 1_000f))
                    .height(4.dp)
                    .background(accent, RoundedCornerShape(percent = 50)),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(2.dp, ringColor, RoundedCornerShape(28.dp))
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (running) problem.text else "Time", style = MaterialTheme.typography.displayMedium)
                Text(
                    typed.ifEmpty { if (running) "?" else "$correct correct" },
                    style = MaterialTheme.typography.displaySmall,
                    color = if (typed.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                )
            }
        }
        NumberPad(enabled = running, onDigit = ::type, onDelete = { typed = typed.dropLast(1) })
    }
}

// ─── Grid recall ──────────────────────────────────────────────────────────────

private enum class GridPhase { Show, Tap, Feedback }

@Composable
fun GridRecallGame(accent: Color, onDone: (Double) -> Unit) {
    val done by rememberUpdatedState(onDone)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var cells by remember { mutableIntStateOf(GridRecall.START_CELLS) }
    var best by remember { mutableIntStateOf(0) }
    var misses by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(0) }
    var pattern by remember { mutableStateOf(emptySet<Int>()) }
    var tapped by remember { mutableStateOf(emptySet<Int>()) }
    var wrongCell by remember { mutableStateOf<Int?>(null) }
    var phase by remember { mutableStateOf(GridPhase.Show) }

    LaunchedEffect(round) {
        pattern = GridRecall.pattern(cells)
        tapped = emptySet()
        wrongCell = null
        phase = GridPhase.Show
        delay(900 + 120L * cells)
        phase = GridPhase.Tap
    }

    fun finishRound(success: Boolean) {
        phase = GridPhase.Feedback
        if (success) {
            best = maxOf(best, cells)
            misses = 0
        } else {
            misses++
        }
        haptics.performHapticFeedback(if (success) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        scope.launch {
            delay(1_000)
            if (misses >= GridRecall.MAX_MISSES) {
                done(best.toDouble())
            } else {
                if (success) cells++
                round++
            }
        }
    }

    val size = GridRecall.sizeFor(cells)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GameStatus("$cells SQUARES", "BEST $best")
        Text(
            when (phase) {
                GridPhase.Show -> "Remember these"
                GridPhase.Tap -> "Tap them back · ${tapped.size} of $cells"
                GridPhase.Feedback -> if (wrongCell == null) "Correct" else "Missed one"
            },
            style = MaterialTheme.typography.titleMedium,
            color = when {
                phase != GridPhase.Feedback -> MaterialTheme.colorScheme.onSurface
                wrongCell == null -> Right
                else -> Wrong
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.aspectRatio(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(size) { r ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(size) { c ->
                            val i = r * size + c
                            val target = when {
                                i == wrongCell -> Wrong
                                phase == GridPhase.Show && i in pattern -> accent
                                i in tapped -> accent.copy(alpha = 0.75f)
                                phase == GridPhase.Feedback && wrongCell != null && i in pattern -> accent.copy(alpha = 0.35f)
                                else -> Surfaces.Fill
                            }
                            val fill by animateColorAsState(target, tween(140), label = "cell")
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(fill, RoundedCornerShape(14.dp))
                                    .hairline(RoundedCornerShape(14.dp))
                                    .clickable(enabled = phase == GridPhase.Tap && i !in tapped, role = Role.Button) {
                                        if (i in pattern) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            tapped = tapped + i
                                            if (tapped.size == pattern.size) finishRound(success = true)
                                        } else {
                                            wrongCell = i
                                            finishRound(success = false)
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }
        MissDots(misses, GridRecall.MAX_MISSES)
    }
}

// ─── Reaction ─────────────────────────────────────────────────────────────────

private sealed interface ReactPhase {
    data object Wait : ReactPhase
    data class Go(val atNanos: Long) : ReactPhase
    data object TooSoon : ReactPhase
    data class Shown(val ms: Long) : ReactPhase
}

@Composable
fun ReactionGame(accent: Color, onDone: (Double) -> Unit) {
    val done by rememberUpdatedState(onDone)
    val haptics = LocalHapticFeedback.current
    val times = remember { mutableStateListOf<Long>() }
    var attempt by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf<ReactPhase>(ReactPhase.Wait) }

    LaunchedEffect(attempt) {
        phase = ReactPhase.Wait
        delay(Reaction.delay())
        // Stamp the frame that first draws green, not the moment the timer fired.
        phase = ReactPhase.Go(Long.MAX_VALUE)
        val drawn = withFrameNanos { it }
        if (phase is ReactPhase.Go) phase = ReactPhase.Go(drawn)
    }
    LaunchedEffect(phase) {
        when (phase) {
            is ReactPhase.Shown -> {
                delay(900)
                if (times.size >= Reaction.TAPS) done(Reaction.median(times.toList()).toDouble()) else attempt++
            }
            ReactPhase.TooSoon -> {
                delay(1_100)
                attempt++
            }
            else -> Unit
        }
    }

    val tap by rememberUpdatedState<() -> Unit> {
        when (val p = phase) {
            ReactPhase.Wait -> {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                phase = ReactPhase.TooSoon
            }
            is ReactPhase.Go -> {
                val ms = if (p.atNanos == Long.MAX_VALUE) 0 else (System.nanoTime() - p.atNanos) / 1_000_000
                if (Reaction.isFalseStart(ms)) {
                    phase = ReactPhase.TooSoon
                } else {
                    times += ms
                    phase = ReactPhase.Shown(ms)
                }
            }
            else -> Unit
        }
    }

    val background by animateColorAsState(
        when (phase) {
            ReactPhase.Wait -> Color(0xFF3A1418)
            is ReactPhase.Go -> Right
            ReactPhase.TooSoon -> Color(0xFF2A2A30)
            is ReactPhase.Shown -> accent.copy(alpha = 0.22f)
        },
        tween(if (phase is ReactPhase.Go) 0 else 160),
        label = "react",
    )
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GameStatus("TAP ${(times.size + 1).coerceAtMost(Reaction.TAPS)} OF ${Reaction.TAPS}", times.lastOrNull()?.let { "LAST $it ms" } ?: "")
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp)
                .background(background, RoundedCornerShape(32.dp))
                .pointerInput(Unit) { detectTapGestures(onPress = { tap() }) }
                .semantics { contentDescription = "Reaction area" },
            contentAlignment = Alignment.Center,
        ) {
            val (headline, sub) = when (val p = phase) {
                ReactPhase.Wait -> "Wait for green" to "Don't tap yet"
                is ReactPhase.Go -> "Tap!" to ""
                ReactPhase.TooSoon -> "Too soon" to "That one doesn't count"
                is ReactPhase.Shown -> "${p.ms} ms" to ""
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    headline,
                    style = MaterialTheme.typography.displaySmall,
                    color = if (phase is ReactPhase.Go) Color.Black else MaterialTheme.colorScheme.onSurface,
                )
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
