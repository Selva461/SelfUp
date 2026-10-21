@file:OptIn(ExperimentalMaterial3Api::class)

package app.selfup.ui.skills

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.selfup.core.Concept
import app.selfup.core.ConceptState
import app.selfup.core.Mastery
import app.selfup.core.SkillPack
import app.selfup.core.StudyPlanner
import app.selfup.ui.components.GlassGroup
import app.selfup.ui.components.GroupHeader
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.LargeTitle
import app.selfup.ui.components.SpringCheck
import app.selfup.ui.components.pressScale
import app.selfup.ui.glass.frosted
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.color
import java.time.format.DateTimeFormatter

private val reviewFormat = DateTimeFormatter.ofPattern("EEE d MMM")

/** The Skills tab: today's session per pack, then each pack's progress. */
@Composable
fun SkillsScreen(
    state: SkillsState,
    contentPadding: PaddingValues,
    onStart: (pack: SkillPack) -> Unit,
    onOpenPack: (SkillPack) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") { LargeTitle("Skills") }
        state.packs.forEach { pack ->
            item(key = "session-${pack.id}") { SessionCard(pack, state, onStart = { onStart(pack) }) }
            item(key = "pack-${pack.id}") { PackCard(pack, state, onClick = { onOpenPack(pack) }) }
        }
        item(key = "note") {
            Text(
                "Mastery is earned by quizzes spaced over days, then a coding task, then explaining it aloud. " +
                    "Reviews come back at 1, 3, 7, 14 and 30 days.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun SessionCard(pack: SkillPack, state: SkillsState, onStart: () -> Unit) {
    val session = state.session(pack)
    val accent = pack.stat.color
    Column(
        Modifier
            .fillMaxWidth()
            .frosted(tint = accent.copy(alpha = 0.07f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("TODAY · ${pack.name.uppercase()}", style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (session.isEmpty) {
            val next = pack.concepts.mapNotNull { state.state(it).nextReview() }.minOrNull()
            Text("All caught up", style = MaterialTheme.typography.titleLarge)
            Text(
                next?.let { "Next review ${it.format(reviewFormat)}" } ?: "Every concept has been started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val parts = buildList {
                if (session.reviews.isNotEmpty()) add("${session.reviews.size} review${if (session.reviews.size > 1) "s" else ""}")
                if (session.fresh.isNotEmpty()) add("${session.fresh.size} new")
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.titleLarge)
            Text(
                session.all.joinToString(", ") { it.title },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            PrimaryButton("Start · about ${session.all.size * 2} min", accent, onStart)
        }
    }
}

@Composable
private fun PackCard(pack: SkillPack, state: SkillsState, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val progress = StudyPlanner.progress(pack, state.states)
    val breakdown = StudyPlanner.breakdown(pack, state.states)
    val solidPlus = breakdown.filterKeys { it.level >= Mastery.SOLID.level }.values.sum()
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .frosted()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(progress.toFloat(), pack.stat.color, size = 64.dp, stroke = 7.dp)
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pack.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "$solidPlus of ${pack.concepts.size} solid · ${pack.sections.size} sections",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MasteryBar(breakdown, pack.concepts.size, pack.stat.color)
        }
    }
}

/** A stacked bar of how many concepts sit at each mastery level, dimmest for the lowest. */
@Composable
private fun MasteryBar(breakdown: Map<Mastery, Int>, total: Int, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(percent = 50)),
    ) {
        Mastery.entries.filter { it != Mastery.UNSEEN }.reversed().forEach { level ->
            val count = breakdown[level] ?: 0
            if (count > 0) {
                Box(
                    Modifier
                        .weight(count.toFloat() / total)
                        .height(6.dp)
                        .background(color.copy(alpha = 0.25f + 0.15f * level.level), RoundedCornerShape(percent = 50)),
                )
            }
        }
        val unseen = breakdown[Mastery.UNSEEN] ?: 0
        if (unseen > 0) Box(Modifier.weight(unseen.toFloat() / total))
    }
}

@Composable
fun ProgressRing(fraction: Float, color: Color, size: Dp, stroke: Dp) {
    val animated by animateFloatAsState(fraction, spring(dampingRatio = 0.85f, stiffness = 60f), label = "ring")
    Canvas(Modifier.size(size)) {
        val s = stroke.toPx()
        drawArc(color.copy(alpha = 0.16f), 0f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(s / 2, s / 2),
            size = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s), style = Stroke(s))
        if (animated > 0f) {
            drawArc(color, -90f, 360f * animated, false, topLeft = androidx.compose.ui.geometry.Offset(s / 2, s / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s), style = Stroke(s, cap = StrokeCap.Round))
        }
    }
}

/** Five capsules, filled up to the concept's mastery. */
@Composable
fun MasteryPips(mastery: Mastery, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Mastery ${mastery.level} of 5, ${mastery.displayName}" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(Mastery.TEACHABLE.level) { i ->
            Box(
                Modifier
                    .width(12.dp)
                    .height(5.dp)
                    .background(if (i < mastery.level) color else color.copy(alpha = 0.16f), RoundedCornerShape(percent = 50)),
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, accent: Color, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(interaction)
            .background(if (enabled) accent else accent.copy(alpha = 0.25f), RoundedCornerShape(percent = 50))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (enabled) Color.Black else Color.Black.copy(alpha = 0.5f))
    }
}

@Composable
private fun BackScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        content = content,
    )
}

/** A pack's concepts by section, each with its mastery and whether it's due. */
@Composable
fun PackScreen(pack: SkillPack, state: SkillsState, onConcept: (Concept) -> Unit, onBack: () -> Unit) {
    BackScaffold(pack.name, onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pack.sections.forEach { section ->
                val concepts = pack.concepts.filter { it.section == section }
                item(key = "h-$section") { GroupHeader(section) }
                item(key = "g-$section") {
                    GlassGroup {
                        concepts.forEachIndexed { i, concept ->
                            if (i > 0) GroupSeparator(start = 16.dp)
                            ConceptRow(concept, state.state(concept), state, pack, onClick = { onConcept(concept) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConceptRow(concept: Concept, cs: ConceptState, state: SkillsState, pack: SkillPack, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val badge = when {
        cs.isFading(state.today) -> "Fading"
        cs.isDue(state.today) -> "Due"
        else -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .pressScale(interaction, 0.985f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(concept.title, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MasteryPips(cs.mastery, pack.stat.color)
                Text(cs.mastery.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        badge?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(percent = 50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** One concept: its card, where it stands, and the next way to level it. */
@Composable
fun ConceptScreen(
    concept: Concept,
    pack: SkillPack,
    state: SkillsState,
    onQuiz: () -> Unit,
    onRead: () -> Unit,
    onApplied: () -> Unit,
    onTaught: () -> Unit,
    onBack: () -> Unit,
) {
    val cs = state.state(concept)
    val accent = pack.stat.color
    BackScaffold(concept.section, onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head") {
                Column(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(concept.title, style = MaterialTheme.typography.headlineLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MasteryPips(cs.mastery, accent)
                        Text(cs.mastery.displayName, style = MaterialTheme.typography.labelLarge, color = accent)
                    }
                    val next = Mastery.entries.getOrNull(cs.mastery.level + 1)
                    Text(
                        next?.let { "Next: ${it.displayName}. ${it.howToEarn}." } ?: "Fully mastered. Keep it fresh with reviews.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    cs.nextReview()?.let {
                        Text("Next review ${it.format(reviewFormat)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "card-h") { GroupHeader("The idea") }
            item(key = "card") {
                GlassGroup {
                    concept.card.forEachIndexed { i, line ->
                        if (i > 0) GroupSeparator(start = 16.dp)
                        Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            item(key = "quiz") {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Take the quiz", accent, onQuiz)
                    if (cs.mastery == Mastery.UNSEEN) {
                        Text(
                            "I've read it",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable(role = Role.Button, onClick = onRead)
                                .padding(10.dp),
                        )
                    }
                }
            }
            item(key = "task-h") { GroupHeader("Apply it") }
            item(key = "task") {
                val unlocked = cs.mastery.level >= Mastery.SOLID.level
                val done = cs.mastery.level >= Mastery.APPLIED.level
                GlassGroup {
                    Text(concept.task, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    GroupSeparator(start = 16.dp)
                    ActionRow(
                        text = when {
                            done -> "Done"
                            unlocked -> "I built it"
                            else -> "Unlocks at Solid"
                        },
                        enabled = unlocked && !done,
                        onClick = onApplied,
                    )
                }
            }
            item(key = "teach-h") { GroupHeader("Teach it") }
            item(key = "teach") { TeachCard(concept, cs, accent, onTaught) }
        }
    }
}

@Composable
private fun TeachCard(concept: Concept, cs: ConceptState, accent: Color, onTaught: () -> Unit) {
    val unlocked = cs.mastery.level >= Mastery.APPLIED.level
    val done = cs.mastery == Mastery.TEACHABLE
    var ticked by remember(concept.id) { mutableStateOf(emptySet<Int>()) }
    GlassGroup {
        Text(
            "Explain it out loud for 2 minutes as if to a junior. Then tick each point you covered.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        concept.keyPoints.forEachIndexed { i, point ->
            GroupSeparator(start = 16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = unlocked && !done, role = Role.Checkbox) {
                        ticked = if (i in ticked) ticked - i else ticked + i
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SpringCheck(done || i in ticked, accent, size = 22.dp)
                Text(point, style = MaterialTheme.typography.bodyMedium)
            }
        }
        GroupSeparator(start = 16.dp)
        ActionRow(
            text = when {
                done -> "Done"
                !unlocked -> "Unlocks at Applied"
                ticked.size < concept.keyPoints.size -> "Tick every point to finish"
                else -> "I explained it"
            },
            enabled = unlocked && !done && ticked.size == concept.keyPoints.size,
            onClick = onTaught,
        )
    }
}

@Composable
private fun ActionRow(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(16.dp),
    )
}
