@file:OptIn(ExperimentalFoundationApi::class)

package app.selfup.ui.hunter

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.selfup.core.Achievement
import app.selfup.core.AchievementGroup
import app.selfup.core.Achievements
import app.selfup.core.DungeonObjective
import app.selfup.core.DungeonRank
import app.selfup.core.Dungeons
import app.selfup.core.ObjectiveProgress
import app.selfup.core.RestDays
import app.selfup.core.Reward
import app.selfup.core.RewardKind
import app.selfup.core.Tier
import app.selfup.data.HunterOverview
import app.selfup.data.rankLabel
import app.selfup.ui.components.BackScaffold
import app.selfup.ui.components.GroupHeader
import app.selfup.ui.components.GroupSeparator
import app.selfup.ui.components.MutedText
import app.selfup.ui.components.SystemBar
import app.selfup.ui.components.SystemButton
import app.selfup.ui.components.SystemGroup
import app.selfup.ui.components.SystemLine
import app.selfup.ui.components.SystemSegmented
import app.selfup.ui.components.SystemTextButton
import app.selfup.ui.components.SystemWindow
import app.selfup.ui.components.Tag
import app.selfup.ui.components.WindowHeading
import app.selfup.ui.components.pressScale
import app.selfup.ui.theme.SectionLabel
import app.selfup.ui.theme.SystemColors
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class HunterTab(val label: String) { DUNGEON("Dungeon"), SHOP("Shop"), TITLES("Titles"), REST("Rest") }

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

fun gold(amount: Int): String = "${NumberFormat.getIntegerInstance(Locale.getDefault()).format(amount)} G"

private fun Tier.color(): Color = when (this) {
    Tier.BRONZE -> Color(0xFFD9925B)
    Tier.SILVER -> Color(0xFFC9D6E3)
    Tier.GOLD -> SystemColors.Gold
    Tier.LEGEND -> Color(0xFFB98CFF)
}

/** "clear=4/5 train=3/4" as "Clear days 4/5 · Training days 3/4". */
private fun runSummary(detail: String): String = detail.split(' ').mapNotNull { code ->
    val key = code.substringBefore('=')
    DungeonObjective.fromKey(key)?.let { "${it.title} ${code.substringAfter('=')}" }
}.joinToString(" · ")

private fun rankColor(rank: DungeonRank): Color = when (rank) {
    DungeonRank.S -> SystemColors.Gold
    DungeonRank.A, DungeonRank.B -> SystemColors.Success
    DungeonRank.C -> SystemColors.Blue
    DungeonRank.D -> SystemColors.TextMuted
    DungeonRank.E -> SystemColors.Danger
}

/** The weekly dungeon on the Today tab, with gold, titles and rest one tap away. */
fun LazyListScope.dungeonSection(overview: HunterOverview?, onOpen: (HunterTab) -> Unit) {
    if (overview == null) return
    item(key = "dungeon") {
        val week = overview.dungeon
        val rank = week.rank
        SystemWindow(title = "Weekly Dungeon", spacing = 12.dp) {
            SystemLine(
                when {
                    overview.restedToday -> "[Rest day. Misses today cost nothing.]"
                    !overview.dungeonScored -> "[Warm-up week. Scoring starts Monday.]"
                    else -> "[Rank ${rank.label} so far · clears ${week.end.format(DateTimeFormatter.ofPattern("EEE"))} night]"
                },
                color = if (overview.restedToday) SystemColors.Success else SystemColors.Text,
            )
            Column(
                Modifier.clickable(role = Role.Button) { onOpen(HunterTab.DUNGEON) },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                week.objectives.forEach { ObjectiveLine(it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HubCell("Gold", gold(overview.gold), SystemColors.Gold, Modifier.weight(1f)) { onOpen(HunterTab.SHOP) }
                HubCell("Titles", "${overview.unlocked.size}/${Achievements.all.size}", SystemColors.BlueBright, Modifier.weight(1f)) { onOpen(HunterTab.TITLES) }
                HubCell(
                    "Rest",
                    "${RestDays.leftIn(YearMonth.from(overview.today), overview.restDays)} left",
                    SystemColors.Success,
                    Modifier.weight(1f),
                ) { onOpen(HunterTab.REST) }
            }
        }
    }
}

@Composable
private fun ObjectiveLine(progress: ObjectiveProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(progress.objective.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "[${progress.count}/${progress.target}]",
                style = MaterialTheme.typography.labelLarge,
                color = if (progress.met) SystemColors.Success else SystemColors.TextMuted,
            )
        }
        SystemBar(progress.fraction.toFloat(), if (progress.met) SystemColors.Success else SystemColors.Blue)
    }
}

@Composable
private fun HubCell(label: String, value: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.uppercase(), style = SectionLabel, color = SystemColors.TextMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = accent, maxLines = 1)
    }
}

/** Dungeon, shop, titles and rest days, one tab each. */
@Composable
fun HunterScreen(
    overview: HunterOverview?,
    tab: HunterTab,
    onTab: (HunterTab) -> Unit,
    onBuy: (Reward) -> Unit,
    onEditReward: (Reward?) -> Unit,
    onEquip: (String?) -> Unit,
    onDeclareRest: (LocalDate) -> Unit,
    onCancelRest: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    BackScaffold("Hunter", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "tabs") { SystemSegmented(HunterTab.entries, tab, label = { it.label }, onSelect = onTab) }
            if (overview == null) return@LazyColumn
            when (tab) {
                HunterTab.DUNGEON -> dungeonTab(overview)
                HunterTab.SHOP -> shopTab(overview, onBuy, onEditReward)
                HunterTab.TITLES -> titlesTab(overview, onEquip)
                HunterTab.REST -> restTab(overview, onDeclareRest, onCancelRest)
            }
        }
    }
}

private fun LazyListScope.dungeonTab(overview: HunterOverview) {
    val week = overview.dungeon
    item(key = "dungeon-now") {
        SystemWindow(title = "This week", accent = rankColor(week.rank)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${week.start.format(dayFormat)} – ${week.end.format(dayFormat)}", style = MaterialTheme.typography.labelMedium, color = SystemColors.TextMuted)
                    Text(
                        if (overview.dungeonScored) "Rank ${week.rank.label} so far" else "Warm-up week",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Text("${(week.performance * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = rankColor(week.rank))
            }
            week.objectives.forEach { progress ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ObjectiveLine(progress)
                    Text("A day counts with ${progress.objective.rule}.", style = MaterialTheme.typography.labelSmall, color = SystemColors.TextMuted)
                }
            }
        }
    }
    item(key = "dungeon-rules") {
        SystemGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WindowHeading("Reward")
                Text(
                    "Every objective met: +${Dungeons.FULL_XP} XP and ${gold(Dungeons.FULL_GOLD)}. Less falls away fast, " +
                        "and a week under a third of the way costs XP. Each rest day lowers the targets by one, except low scrolling.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SystemColors.TextMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("S" to "100%", "A" to "90%", "B" to "75%", "C" to "60%", "D" to "35%").forEach { (label, at) ->
                        Tag("$label $at", rankColor(DungeonRank.valueOf(label)))
                    }
                }
            }
        }
    }
    overview.lastRun?.let { run ->
        item(key = "dungeon-last") {
            SystemGroup {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Last week · rank ${run.rankLabel}", style = MaterialTheme.typography.bodyLarge)
                        Text(runSummary(run.detail), style = MaterialTheme.typography.labelSmall, color = SystemColors.TextMuted)
                    }
                    Text("${if (run.xp >= 0) "+" else "−"}${kotlin.math.abs(run.xp)} XP", style = MaterialTheme.typography.labelLarge, color = if (run.xp >= 0) SystemColors.Success else SystemColors.Danger)
                }
            }
        }
    }
}

private fun LazyListScope.shopTab(overview: HunterOverview, onBuy: (Reward) -> Unit, onEditReward: (Reward?) -> Unit) {
    item(key = "gold") {
        SystemWindow(title = "Gold", accent = SystemColors.Gold) {
            Text(gold(overview.gold), style = MaterialTheme.typography.displaySmall, color = SystemColors.Gold, modifier = Modifier.align(Alignment.CenterHorizontally))
            MutedText(
                "Paid by each finished day (its net XP, plus 10 for a clear day), the weekly dungeon and achievements. Spend it on rewards you price yourself.",
            )
        }
    }
    item(key = "rewards-header") { GroupHeader("Rewards") }
    item(key = "rewards") {
        SystemGroup {
            if (overview.rewards.isEmpty()) {
                MutedText("No rewards yet. Add the treats you want to earn.", Modifier.padding(16.dp))
            }
            overview.rewards.forEachIndexed { i, reward ->
                if (i > 0) GroupSeparator(start = 16.dp)
                RewardRow(reward, affordable = overview.gold >= reward.price, onBuy = { onBuy(reward) }, onEdit = { onEditReward(reward) })
            }
        }
    }
    item(key = "add-reward") { SystemButton("Add a reward", { onEditReward(null) }, filled = false, accent = SystemColors.Gold) }
    if (overview.purchases.isNotEmpty()) {
        item(key = "purchases-header") { GroupHeader("Bought") }
        item(key = "purchases") {
            SystemGroup {
                overview.purchases.take(10).forEachIndexed { i, p ->
                    if (i > 0) GroupSeparator(start = 16.dp)
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(LocalDate.ofEpochDay(p.epochDay).format(dayFormat), style = MaterialTheme.typography.labelSmall, color = SystemColors.TextMuted)
                        }
                        Text("−${gold(p.price)}", style = MaterialTheme.typography.labelLarge, color = SystemColors.TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardRow(reward: Reward, affordable: Boolean, onBuy: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onEdit, role = Role.Button)
            .heightIn(min = 60.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(reward.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (reward.kind == RewardKind.SCREEN_MINUTES) {
                Text("Adds ${reward.minutes} min to today's app-lock budget", style = MaterialTheme.typography.labelSmall, color = SystemColors.TextMuted)
            }
        }
        Spacer(Modifier.width(8.dp))
        SystemButton(
            gold(reward.price),
            onBuy,
            Modifier.width(112.dp),
            accent = if (affordable) SystemColors.Gold else SystemColors.TextMuted,
            filled = affordable,
        )
    }
}

private fun LazyListScope.titlesTab(overview: HunterOverview, onEquip: (String?) -> Unit) {
    item(key = "worn") {
        SystemWindow(title = "Title") {
            Text(overview.title?.title ?: "Rank title", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            MutedText("Earn an achievement to unlock its title, then tap it to wear it on your status window.")
            if (overview.title != null) {
                SystemTextButton("Wear rank title instead", { onEquip(null) }, Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
    AchievementGroup.entries.forEach { group ->
        val items = Achievements.all.filter { it.group == group }
        item(key = "group-${group.name}") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupHeader(group.displayName) {
                    Text("${items.count { it.id in overview.unlocked }}/${items.size}", style = MaterialTheme.typography.labelMedium, color = SystemColors.TextMuted)
                }
                SystemGroup {
                    items.forEachIndexed { i, a ->
                        if (i > 0) GroupSeparator(start = 16.dp)
                        AchievementRow(a, overview, onEquip)
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(a: Achievement, overview: HunterOverview, onEquip: (String?) -> Unit) {
    val unlocked = a.id in overview.unlocked
    val worn = overview.title?.id == a.id
    val secret = a.hidden && !unlocked
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = unlocked && !worn, role = Role.Button) { onEquip(a.id) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (secret) "???" else a.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (unlocked) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (unlocked) SystemColors.Text else SystemColors.TextMuted,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Tag(a.tier.displayName, a.tier.color())
            }
            Text(if (secret) "Hidden until earned" else a.description, style = MaterialTheme.typography.labelSmall, color = SystemColors.TextMuted)
            if (!unlocked && !secret) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemBar(a.fraction(overview.facts), a.tier.color(), Modifier.weight(1f), height = 4.dp)
                    Text("${a.current(overview.facts)}/${a.target}", style = MaterialTheme.typography.labelSmall, color = SystemColors.TextMuted)
                }
            }
        }
        when {
            worn -> Tag("Worn", SystemColors.Blue)
            unlocked -> Text("+${a.tier.gold} G", style = MaterialTheme.typography.labelMedium, color = SystemColors.Gold)
        }
    }
}

private fun LazyListScope.restTab(overview: HunterOverview, onDeclare: (LocalDate) -> Unit, onCancel: (LocalDate) -> Unit) {
    val month = YearMonth.from(overview.today)
    val left = RestDays.leftIn(month, overview.restDays)
    item(key = "rest") {
        SystemWindow(title = "Rest days", accent = SystemColors.Success) {
            SystemLine("[$left of ${RestDays.PER_MONTH} left in ${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}]")
            MutedText(
                "For illness, travel or a planned day off. Missed dailies on a rest day cost no XP, use no shield and leave no make-up quest. " +
                    "The streak holds but doesn't grow. Declare it ahead: today only until noon, so it can't dodge a penalty already coming.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SystemButton("Rest today", { onDeclare(overview.today) }, Modifier.weight(1f), accent = SystemColors.Success, filled = false, enabled = !overview.restedToday)
                SystemButton("Rest tomorrow", { onDeclare(overview.today.plusDays(1)) }, Modifier.weight(1f), accent = SystemColors.Success, filled = false, enabled = overview.today.plusDays(1) !in overview.restDays)
            }
        }
    }
    val upcoming = overview.upcomingRest
    if (upcoming.isNotEmpty()) {
        item(key = "rest-upcoming-header") { GroupHeader("Planned") }
        item(key = "rest-upcoming") {
            SystemGroup {
                upcoming.forEachIndexed { i, day ->
                    if (i > 0) GroupSeparator(start = 16.dp)
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (day == overview.today) "Today" else day.format(dayFormat), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        SystemTextButton("Call off", { onCancel(day) }, color = SystemColors.TextMuted)
                    }
                }
            }
        }
    }
    item(key = "rest-gap") { Spacer(Modifier.height(4.dp)) }
}
