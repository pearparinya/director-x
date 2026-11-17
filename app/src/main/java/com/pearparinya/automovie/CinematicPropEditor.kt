package com.pearparinya.automovie

/**
 * Validated inventory operations shared by scene-editor integrations.
 * Operations are immutable and reject invalid changes before persistence.
 */
internal object CinematicPropEditor {
    fun add(current: List<CinematicProp>, prop: CinematicProp): List<CinematicProp> =
        validated(current + prop)

    fun replace(current: List<CinematicProp>, originalId: String, prop: CinematicProp): List<CinematicProp> {
        val index = current.indexOfFirst { it.id.trim().equals(originalId.trim(), ignoreCase = true) }
        require(index >= 0) { "Prop not found" }
        return validated(current.toMutableList().apply { set(index, prop) })
    }

    fun remove(current: List<CinematicProp>, id: String): List<CinematicProp> {
        val index = current.indexOfFirst { it.id.trim().equals(id.trim(), ignoreCase = true) }
        require(index >= 0) { "Prop not found" }
        return validated(current.toMutableList().apply { removeAt(index) })
    }

    private fun validated(props: List<CinematicProp>): List<CinematicProp> {
        require(CinematicScenePreflight.issues(props).isEmpty()) { "Invalid cinematic prop inventory" }
        return props
    }
}
