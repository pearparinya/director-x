package com.pearparinya.automovie

/** Fictional age in completed years; 0 represents less than one year. */
internal object CharacterAge {
    fun valid(years: Int): Boolean = years in 0..1000
}
