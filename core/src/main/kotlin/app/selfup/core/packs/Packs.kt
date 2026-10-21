package app.selfup.core.packs

import app.selfup.core.Concept
import app.selfup.core.SkillPack

/** Every skill pack that ships with the app, in the order the Skills tab lists them. */
public object Packs {
    public val all: List<SkillPack> = listOf(JavaCore)

    private val conceptsById: Map<String, Concept> = all.flatMap { it.concepts }.associateBy { it.id }

    public fun pack(id: String): SkillPack? = all.firstOrNull { it.id == id }

    public fun concept(id: String): Concept? = conceptsById[id]

    public fun packOf(conceptId: String): SkillPack? = all.firstOrNull { pack -> pack.concepts.any { it.id == conceptId } }
}
