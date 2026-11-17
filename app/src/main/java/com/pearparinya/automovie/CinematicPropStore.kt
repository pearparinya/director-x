package com.pearparinya.automovie

import android.content.Context

/**
 * Local persistence for optional per-scene cinematic prop inventories.
 * Never writes invalid data, and never replaces a saved inventory after a
 * failed decode. Scene identifiers must be stable across app restarts.
 */
internal class CinematicPropStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "cinematic_prop_inventory_v1", Context.MODE_PRIVATE
    )

    fun load(sceneId: String): List<CinematicProp> {
        val key = keyFor(sceneId)
        return CinematicPropCodec.decode(preferences.getString(key, null))
    }

    fun save(sceneId: String, props: List<CinematicProp>): Boolean {
        val key = keyFor(sceneId)
        val serialized = CinematicPropCodec.encode(props)
        return preferences.edit().putString(key, serialized).commit()
    }

    /**
     * Build a Flow prompt using the saved inventory for this scene.
     * A caller-supplied inventory takes precedence, so unsaved edits are not lost.
     */
    fun buildScenePrompt(
        sceneId: String,
        spec: FlowSceneSpec,
        camera: String = "Slow, gentle pan."
    ): String {
        val inventory = if (spec.cinematicProps.isNotEmpty()) spec.cinematicProps else load(sceneId)
        return FlowScenePrompt.build(spec.copy(cinematicProps = inventory), id = sceneId, camera = camera)
    }

    fun clear(sceneId: String): Boolean =
        preferences.edit().remove(keyFor(sceneId)).commit()

    fun hasSavedInventory(sceneId: String): Boolean =
        preferences.contains(keyFor(sceneId))

    private fun keyFor(sceneId: String): String {
        val id = sceneId.trim()
        require(id.isNotEmpty() && id.length <= 160 && id.none { it == '\n' || it == '\r' }) {
            "Invalid scene identifier"
        }
        return "scene:$id"
    }
}
