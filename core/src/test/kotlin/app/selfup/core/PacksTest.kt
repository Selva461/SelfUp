package app.selfup.core

import app.selfup.core.packs.Packs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PacksTest {
    @Test
    fun `concept ids are unique across all packs`() {
        val ids = Packs.all.flatMap { pack -> pack.concepts.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every question is complete`() {
        Packs.all.flatMap { it.concepts }.forEach { concept ->
            assertTrue(concept.questions.size >= SkillRules.QUIZ_LENGTH, concept.id)
            concept.questions.forEach { q ->
                assertTrue(q.explanation.isNotBlank(), "${concept.id}: ${q.prompt}")
                assertTrue(q.options.all { it.isNotBlank() }, "${concept.id}: ${q.prompt}")
            }
        }
    }

    @Test
    fun `java core covers the planned sections`() {
        val java = assertNotNull(Packs.pack("java_core"))
        assertEquals(30, java.concepts.size)
        assertEquals(
            listOf("Basics", "Object-oriented", "Core APIs", "Collections", "Functional", "Concurrency", "JVM"),
            java.sections,
        )
        assertEquals(java, Packs.packOf("java.hashmap"))
        assertNotNull(Packs.concept("java.streams"))
    }
}
