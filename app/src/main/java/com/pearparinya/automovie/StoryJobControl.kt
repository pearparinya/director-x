package com.pearparinya.automovie
internal object StoryJobControl {
    fun canCancel(requested: String?, active: String?, running: Boolean): Boolean =
        running && !requested.isNullOrBlank() && requested == active
}
