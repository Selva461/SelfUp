package app.selfup.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selfup.core.Metric
import app.selfup.core.PersonalBest
import app.selfup.core.RecordEntry
import app.selfup.core.RecordKind
import app.selfup.core.StatChange
import app.selfup.core.TestDay
import app.selfup.data.RecordSummary
import app.selfup.data.RecordsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

data class RecordsState(
    val summaries: List<RecordSummary> = emptyList(),
    val nextTestDay: LocalDate? = null,
)

sealed interface RecordsEvent {
    data class Logged(val kind: RecordKind, val entryId: Long, val personalBests: List<PersonalBest>) : RecordsEvent
    data class LeveledUp(val changes: List<StatChange>) : RecordsEvent
}

class RecordsViewModel(private val records: RecordsRepository, today: () -> LocalDate) : ViewModel() {
    val state: StateFlow<RecordsState> = records.observeSummaries()
        .map { RecordsState(it, TestDay.next(today())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsState())

    private val _events = Channel<RecordsEvent>(Channel.BUFFERED)
    val events: Flow<RecordsEvent> = _events.receiveAsFlow()

    suspend fun latest(kind: RecordKind): RecordEntry? = records.latest(kind)

    fun log(kind: RecordKind, weightKg: Double? = null, reps: Int? = null, seconds: Int? = null, distanceKm: Double? = null) {
        viewModelScope.launch {
            val result = records.log(kind, weightKg, reps, seconds, distanceKm)
            _events.send(RecordsEvent.Logged(kind, result.entryId, result.personalBests))
            val leveled = result.changes.filter { it.leveledUp }
            if (leveled.isNotEmpty()) _events.send(RecordsEvent.LeveledUp(leveled))
        }
    }

    fun delete(entryId: Long) {
        viewModelScope.launch { records.delete(entryId) }
    }
}

/** Display formatting for record values, shared by the list, the sheet and the celebration. */
object RecordFormat {
    fun number(value: Double): String {
        val tenths = (value * 10).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }

    fun duration(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Value with its unit, such as "71.5 kg", "1:15" or "5:24 /km". */
    fun metric(metric: Metric, value: Double): String = when (metric) {
        Metric.EST_1RM, Metric.BODY_WEIGHT -> "${number(value)} kg"
        Metric.REPS -> "${value.roundToInt()} reps"
        Metric.HOLD -> duration(value.roundToInt())
        Metric.DISTANCE -> "${number(value)} km"
        Metric.PACE -> "${duration((value * 60).roundToInt())} /km"
    }

    /** What was actually done, such as "65 kg × 5", "5 km in 28:30" or "1:15". */
    fun entry(entry: RecordEntry): String = when (entry.kind.metrics.first()) {
        Metric.EST_1RM -> "${number(entry.weightKg!!)} kg × ${entry.reps}"
        Metric.REPS -> "${entry.reps} reps"
        Metric.HOLD -> duration(entry.seconds!!)
        Metric.DISTANCE, Metric.PACE -> "${number(entry.distanceKm!!)} km in ${duration(entry.seconds!!)}"
        Metric.BODY_WEIGHT -> "${number(entry.weightKg!!)} kg"
    }
}
