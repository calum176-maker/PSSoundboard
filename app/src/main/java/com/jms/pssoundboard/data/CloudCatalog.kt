package com.jms.pssoundboard.data

/**
 * Curated open sound library. Remote entries come from the Google Sound Library
 * (https://developers.google.com/assistant/tools/sound-library) licensed CC BY 4.0.
 * Built-in entries are the locally synthesized clips (always available offline).
 */
data class LibraryItem(
    val id: String,
    val title: String,
    val category: String,
    val url: String? = null,
    val builtinId: String? = null,
    val localUri: String? = null,
    val source: String = "Built-in",
    val durationMs: Long? = null,
    val iconUrl: String? = null,
) {
    val isRemote: Boolean get() = url != null
}

object CloudCatalog {

    const val ALL = "All"
    const val BUILT_IN = "Built-in Synth"
    const val ATTRIBUTION = "Remote clips: Google Sound Library, CC BY 4.0. Built-in clips are synthesized on-device."

    private const val BASE = "https://actions.google.com/sounds/v1"

    private fun remote(category: String, path: String, title: String) =
        LibraryItem(id = "gsl:$path", title = title, category = category, url = "$BASE/$path.ogg", source = "Google Actions")

    val items: List<LibraryItem> by lazy {
        SynthTestClipsGenerator.CLIPS.map {
            LibraryItem(id = "builtin:${it.id}", title = it.title, category = BUILT_IN, builtinId = it.id, source = "Built-in")
        } + listOf(
            remote("Alarms", "alarms/beep_short", "Short Beep"),
            remote("Alarms", "alarms/spaceship_alarm", "Spaceship Alarm"),
            remote("Alarms", "alarms/dosimeter_alarm", "Dosimeter Alarm"),
            remote("Alarms", "alarms/bugle_tune", "Bugle Tune"),
            remote("Alarms", "alarms/digital_watch_alarm_long", "Digital Watch Alarm"),
            remote("Alarms", "alarms/dinner_bell_triangle", "Dinner Bell Triangle"),
            remote("Alarms", "alarms/radiation_meter", "Radiation Meter"),
            remote("Cartoon", "cartoon/cartoon_boing", "Cartoon Boing"),
            remote("Cartoon", "cartoon/clown_horn", "Clown Horn"),
            remote("Cartoon", "cartoon/clang_and_wobble", "Clang & Wobble"),
            remote("Cartoon", "cartoon/cartoon_cowbell", "Cowbell"),
            remote("Cartoon", "cartoon/drum_roll", "Drum Roll"),
            remote("Cartoon", "cartoon/falling_whistle", "Falling Whistle"),
            remote("Cartoon", "cartoon/fart_toot", "Toot"),
            remote("Cartoon", "cartoon/deflate", "Deflate"),
            remote("Cartoon", "cartoon/continuous_slide_whistle", "Slide Whistle"),
            remote("Cartoon", "cartoon/cymbal_kick", "Cymbal Kick"),
            remote("Crowds", "crowds/team_cheer", "Team Cheer"),
            remote("Crowds", "crowds/battle_crowd_celebration", "Crowd Celebration"),
            remote("Crowds", "crowds/battle_cry_high_pitch", "Battle Cry"),
            remote("Crowds", "crowds/human_group_cry_out", "Group Cry Out"),
            remote("Crowds", "crowds/voices_angry", "Angry Voices"),
            remote("Impacts", "impacts/crash", "Crash"),
            remote("Impacts", "impacts/body_punch_series", "Punch Series"),
            remote("Impacts", "impacts/broken_plates", "Broken Plates"),
            remote("Impacts", "impacts/crash_metal_plate_big_room", "Metal Crash"),
            remote("Sci-Fi", "science_fiction/alien_beam", "Alien Beam"),
            remote("Sci-Fi", "science_fiction/high_to_low_sweep", "High-to-Low Sweep"),
            remote("Sci-Fi", "science_fiction/robot_code", "Robot Code"),
            remote("Sci-Fi", "science_fiction/sci_fi_vortex", "Sci-Fi Vortex"),
            remote("Sci-Fi", "science_fiction/electric_ring_long", "Electric Ring"),
            remote("Sci-Fi", "science_fiction/suspense_builder", "Suspense Builder"),
        )
    }

    val categories: List<String> by lazy { listOf(ALL) + items.map { it.category }.distinct() }
}
