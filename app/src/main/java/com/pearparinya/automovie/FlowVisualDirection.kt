package com.pearparinya.automovie

/** Shared creative direction for exported commands, never a claim of generated-image fidelity. */
internal object FlowVisualDirection {
    const val identity = "Match each adult character only to their own attached MASTER. Preserve facial geometry, eyes, nose, lips, jawline, hairstyle and natural skin tone. Preserve shoulder width, chest volume, waist and hip widths, upper-arm thickness, chest-to-waist and waist-to-hip ratios. Do not slim, enlarge, blend identities or reshape the body to fit clothes. Tailor the stated new wardrobe to the reference body with realistic seams and fabric drape. References supply identity and body characteristics, not copied poses, clothes or backgrounds."
    const val referencePriority = "FINAL REFERENCE PRIORITY: The original attached reference for each CHARACTER label is the visual authority for that person's face and body, above conflicting generic body adjectives or scene descriptions. Preserve the visible chest/bust shape and volume relative to shoulders, torso, waist and hips; do not reduce, enhance, lift or reshape it. Preserve each person's distinct silhouette instead of averaging the cast. Clothing must accommodate that reference silhouette naturally; do not infer numerical measurements or cup sizes from a photograph. Perspective, pose and fabric can change apparent size; do not compensate by changing anatomy. Maintain the same identity and proportions from the first frame through the last, with no morphing during movement. Do not use a drifted generated image as a replacement identity reference. If a body region is hidden or cropped, do not invent exact measurements."
    const val referenceReview = "REFERENCE REVIEW REQUIRED: Compare the actual result with each person's original reference at comparable framing and pose: face, shoulders, chest/bust-to-waist ratio, waist-to-hip ratio and upper arms. Check the beginning, middle and end of video for drift. Reject and regenerate a mismatched result; a prompt is not a verified likeness score or a guarantee of 100% fidelity."
    const val color = "FINAL COLOR DIRECTION (overrides conflicting lighting/color instructions): bright neutral-white photographic look, clean white and light-gray palette, soft neutral key light, neutral white balance, accurate natural skin tones and visible skin texture. White objects remain neutral white. No yellow/orange cast, amber wash, golden-hour grading or sepia. Keep dimensional soft shadows and controlled highlights; no bleached skin or blown-out whites. Preserve the story's time and location; at night use neutral-white interior lighting rather than changing night to daytime."
    const val poster = "Premium photorealistic Thai drama key art with a clear visual story, not a lineup or pasted reference collage. Choose a meaningful unresolved incident or evidence prop without revealing the ending. Direct each person's distinct expression, gaze, gesture, body angle and distance to communicate the episode relationship. Create foreground/midground/background depth and a clear focal face; keep faces unobscured in the upper area. Design fresh poses, setting and episode-appropriate wardrobe; no automatic bedroom, intimate pose or reference-outfit copying. Use realistic fabric, editorial lighting, restrained rim separation and clean background depth. Keep only incidental writing on evidence props unreadable; the required cover titles must remain fully readable."
    fun layout(ratio: String): String = when(ratio) {
        "9x16" -> "Vertical 9:16, 1080 x 1920 pixels. Compose native portrait key art with balanced left/center/right staging and vertical headroom and lower-middle text space. Keep one compact lower-center text group within x=15-85% and y=52-78%, leaving at least 10% side margins and 20% bottom clearance. Keep faces clear."
        "16x9" -> "Horizontal 16:9, 1920 x 1080 pixels. Native widescreen cover; recompose the same cast and locked outfits. Keep text lower-middle within x=15-85%, y=52-78%, away from faces and edges."
        else -> error("Unsupported cover aspect ratio")
    }
    fun storyCover(ratio: String, references: String, channel: String, story: String, tagline: String, incident: String): String =
        cover(ratio,references,channel,story,0,"",tagline,incident).replace("Episode story cue:","Overall story cue:")
    fun cover(ratio: String, references: String, channel: String, story: String, ep: Int, episode: String, tagline: String, incident: String): String = """
$references

POSTER PROMPT
${layout(ratio)}
$identity
${BodyReferencePolicy.preciseIdentity}
$color
$poster
${PerformanceDirection.guide}
Episode story cue: $incident
Use the listed reference map and episode outfits; never substitute a different person's body or face.
TYPOGRAPHY: Thai display typography with balanced spacing, strong legibility at phone-thumbnail size and subtle local contrast support. Keep all text together as one compact group; never scatter the three required entries across the canvas. Main story title is largest, channel smaller, and the required third entry secondary. No text over faces, no clipped letters. Restrained burgundy/charcoal type with selective metallic accents is allowed without tinting the skin or the entire image yellow.
${CoverTypography.prompt(channel,story,ep.takeIf { it>0 },episode,tagline)}
Check reference likeness, original proportions, exact Thai spelling, text grouping and neutral whites before accepting. This command does not certify generated output; review the actual result.
$referencePriority
$referenceReview
    """.trimIndent()
}
