package com.pearparinya.automovie

/**
 * Additional first-frame constraints for Google Flow text prompts.
 * These are directing instructions, not guarantees about model output.
 */
internal object CinematicFlowGuard {
    const val physicalContinuity = "FIRST-FRAME OBJECT LOCK: Keep each visible prop's identity, count, shape, relative size, material and color unchanged. Do not duplicate, shrink, enlarge, morph, teleport or spontaneously remove objects. Maintain hand-to-prop contact during handling. Objects can leave the frame only through continuous visible movement. Preserve left/right orientation, readable surfaces, labels and spatial relationships; never mirror the scene or flip an object."

    const val characterContinuity = "CHARACTER LOCK: Preserve each person's identity, facial features, hairstyle, skin tone, body proportions and established wardrobe throughout the take. No face swapping, clothing changes, extra limbs, merged hands, duplicate characters or sudden position jumps."

    const val motionAndCamera = "PHYSICAL MOTION: Use natural weight, gravity, momentum, balance and believable hand grips. Make all transitions continuous. CAMERA: One coherent cinematic shot, with stable perspective, plausible lens behavior and restrained motivated camera motion; no abrupt cuts, impossible camera moves, unrequested zooms or focal-length jumps."

    const val lightingAndAudio = "LIGHTING AND COLOR: Match the attached first frame's direction of light, exposure, white balance, shadow placement and skin tones throughout. AUDIO: Maintain continuous location ambience; sync any authorized dialogue to the correct speaker, with natural pacing and no added voices, music or sound effects not specified by the scene."

    fun direction(): String = listOf(physicalContinuity, characterContinuity, motionAndCamera, lightingAndAudio).joinToString(" ")
}
