package com.pearparinya.automovie.stand

enum class StandMovement { HIDDEN, BESIDE_OWNER, INDEPENDENT, COMBAT, PROTECTING }

data class StandPosition(val x: Double, val y: Double, val z: Double = 0.0) {
    init { require(x.isFinite() && y.isFinite() && z.isFinite()) }
    operator fun plus(other: StandPosition) = StandPosition(x + other.x, y + other.y, z + other.z)
}

data class StandMovementState(
    val presence: StandPresence,
    val ownerPosition: StandPosition,
    val position: StandPosition,
    val movement: StandMovement = StandMovement.HIDDEN
) {
    init { require(presence.summoned == (movement != StandMovement.HIDDEN)) }
}

object StandMovementEngine {
    private val besideOffset = StandPosition(1.0, 0.0)
    fun initial(ownership: StandOwnership, standId: String, ownerId: String,
                ownerPosition: StandPosition): StandMovementState {
        ownership.requireOwner(standId, ownerId)
        return StandMovementState(StandPresence(standId, ownerId), ownerPosition, ownerPosition)
    }

    fun summon(ownership: StandOwnership, state: StandMovementState): StandMovementState {
        verify(ownership, state)
        if (state.presence.summoned) return state
        return state.copy(presence = state.presence.summon(),
            position = state.ownerPosition + besideOffset, movement = StandMovement.BESIDE_OWNER)
    }

    fun dismiss(ownership: StandOwnership, state: StandMovementState): StandMovementState {
        verify(ownership, state)
        return state.copy(presence = state.presence.dismiss(), position = state.ownerPosition,
            movement = StandMovement.HIDDEN)
    }

    fun moveOwner(ownership: StandOwnership, state: StandMovementState,
                  destination: StandPosition): StandMovementState {
        verify(ownership, state)
        val position = when (state.movement) {
            StandMovement.HIDDEN -> destination
            StandMovement.BESIDE_OWNER, StandMovement.PROTECTING -> destination + besideOffset
            else -> state.position
        }
        return state.copy(ownerPosition = destination, position = position)
    }

    fun moveStand(ownership: StandOwnership, state: StandMovementState,
                  destination: StandPosition, combat: Boolean = false): StandMovementState {
        verify(ownership, state)
        require(state.presence.summoned)
        return state.copy(position = destination,
            movement = if (combat) StandMovement.COMBAT else StandMovement.INDEPENDENT)
    }

    fun followOwner(ownership: StandOwnership, state: StandMovementState): StandMovementState {
        verify(ownership, state)
        require(state.presence.summoned)
        return state.copy(position = state.ownerPosition + besideOffset, movement = StandMovement.BESIDE_OWNER)
    }

    fun protectIfThreatened(ownership: StandOwnership, state: StandMovementState,
                            ownerThreatened: Boolean): StandMovementState {
        verify(ownership, state)
        if (!ownerThreatened || state.presence.standId != StandCatalog.SUPREME_ID) return state
        return summon(ownership, state).copy(position = state.ownerPosition + besideOffset,
            movement = StandMovement.PROTECTING)
    }

    fun visibleTo(ownership: StandOwnership, state: StandMovementState, observerId: String): Boolean {
        verify(ownership, state)
        return state.presence.visibleTo(ownership.standsOf(observerId).isNotEmpty())
    }

    private fun verify(ownership: StandOwnership, state: StandMovementState) {
        ownership.requireOwner(state.presence.standId, state.presence.ownerId)
    }
}
