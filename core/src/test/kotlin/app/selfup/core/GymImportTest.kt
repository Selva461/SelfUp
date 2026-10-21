package app.selfup.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GymImportTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val t0 = Instant.parse("2026-10-21T13:00:00Z")

    private fun set(
        exercise: String,
        kg: Double,
        reps: Int,
        session: Long = 1,
        minute: Long = 0,
        variation: String = "",
        assisted: Boolean = false,
    ) = GymSet(setId = session * 100 + minute, sessionId = session, exercise = exercise, weightKg = kg, reps = reps,
        variation = variation, assisted = assisted, loggedAt = t0.plusSeconds(minute * 60))

    private fun records(vararg sets: GymSet) = GymImport.records(sets.toList(), emptyList(), emptyList(), zone)

    @Test
    fun `only the benchmark movement counts, by Gym's name or an obvious custom one`() {
        assertEquals(RecordKind.BENCH, GymImport.kindFor("Barbell bench press"))
        assertEquals(RecordKind.BENCH, GymImport.kindFor("  bench   PRESS "))
        assertEquals(RecordKind.SQUAT, GymImport.kindFor("Low-bar back squat"))
        assertEquals(RecordKind.PULL_UPS, GymImport.kindFor("Pull-up"))
        assertNull(GymImport.kindFor("Incline barbell bench press"))
        assertNull(GymImport.kindFor("Close-grip barbell bench press"))
        assertNull(GymImport.kindFor("Front squat"))
        assertNull(GymImport.kindFor("Assisted pull-up machine"))
        assertNull(GymImport.kindFor("Side plank"))
    }

    @Test
    fun `variations that change the lift are left out, harder ones kept`() {
        assertTrue(GymImport.isComparable(""))
        assertTrue(GymImport.isComparable("Tempo=Paused"))
        assertTrue(GymImport.isComparable("Bench angle=Flat|Grip width=Standard"))
        assertTrue(GymImport.isComparable("Paused"))
        assertFalse(GymImport.isComparable("Grip width=Close|Tempo=Paused"))
        assertFalse(GymImport.isComparable("Bench angle=Incline 30°"))
        assertFalse(GymImport.isComparable("Close"))
    }

    @Test
    fun `one entry per lift per workout, from the best set`() {
        val imported = records(
            set("Barbell bench press", 60.0, 8, minute = 0),
            set("Barbell bench press", 70.0, 5, minute = 3),
            set("Barbell bench press", 70.0, 4, minute = 6),
            set("Barbell bench press", 90.0, 3, minute = 9, variation = "Grip width=Close"),
            set("Deadlift", 120.0, 5, minute = 20),
            set("Lat pulldown", 50.0, 10, minute = 30),
            set("Barbell bench press", 72.5, 5, session = 2, minute = 2_000),
        )
        assertEquals(3, imported.size)
        val first = imported.first { it.source == "gym:session:1:bench" }.entry
        assertEquals(70.0, first.weightKg)
        assertEquals(5, first.reps)
        assertEquals(t0.plusSeconds(180), first.at)
        assertEquals(120.0, imported.first { it.source == "gym:session:1:deadlift" }.entry.weightKg)
        assertEquals(72.5, imported.first { it.source == "gym:session:2:bench" }.entry.weightKg)
        assertEquals(imported.sortedBy { it.entry.at }, imported)
    }

    @Test
    fun `bodyweight lifts count reps, never assisted ones, and plank reps are seconds`() {
        val imported = records(
            set("Pull-up", 0.0, 8, minute = 0),
            set("Weighted pull-up", 10.0, 6, minute = 2),
            set("Pull-up", 0.0, 12, minute = 4, assisted = true),
            set("Pull-up", -20.0, 15, minute = 6),
            set("Plank", 0.0, 75, minute = 8),
        )
        assertEquals(8, imported.single { it.entry.kind == RecordKind.PULL_UPS }.entry.reps)
        assertEquals(75, imported.single { it.entry.kind == RecordKind.PLANK }.entry.seconds)
    }

    @Test
    fun `runs with a distance and daily weigh-ins come across`() {
        val imported = GymImport.records(
            sets = emptyList(),
            cardio = listOf(
                GymCardio(7, "Running", 30, 5.0, t0),
                GymCardio(8, "Running", 20, 0.0, t0),
                GymCardio(9, "Cycling", 40, 15.0, t0),
            ),
            weighIns = listOf(GymWeighIn(LocalDate.of(2026, 10, 21), 78.4)),
            zone = zone,
        )
        assertEquals(listOf("gym:cardio:7", "gym:weight:${LocalDate.of(2026, 10, 21).toEpochDay()}").sorted(), imported.map { it.source }.sorted())
        val run = imported.single { it.entry.kind == RecordKind.RUN }.entry
        assertEquals(5.0, run.distanceKm)
        assertEquals(1800, run.seconds)
        val weight = imported.single { it.entry.kind == RecordKind.BODY_WEIGHT }.entry
        assertEquals(LocalDate.of(2026, 10, 21).atTime(12, 0).atZone(zone).toInstant(), weight.at)
    }

    @Test
    fun `reps past what an estimate can use are skipped rather than failing`() {
        assertTrue(records(set("Barbell bench press", 20.0, 40)).isEmpty())
    }
}
