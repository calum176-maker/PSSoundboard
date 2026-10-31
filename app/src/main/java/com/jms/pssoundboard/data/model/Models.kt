package com.jms.pssoundboard.data.model

/** Number of tactile pads per board (3 columns x 4 rows). */
const val PADS_PER_BOARD = 12
const val GRID_COLUMNS = 3

/** Number of pads exposed in the floating Mini-Deck (3 x 2). */
const val MINI_DECK_SIZE = 6

enum class PlaybackMode(val label: String, val description: String) {
    /** Tap once to play to the end (overlapping taps layer). */
    ONE_SHOT("One-Shot", "Tap once, plays to the end"),

    /** Loops while the finger is held down, stops on release. */
    HOLD_TO_PLAY("Hold", "Loops while held, stops on release"),

    /** Stops the previous stream of this pad before firing a fresh instance. */
    RESTART_ON_SPAM("Restart", "Every tap restarts the clip"),
}

/*
 * NOTE: every property has a default value so that Kotlin generates a no-arg constructor.
 * Gson then uses it, guaranteeing non-null defaults for fields missing from older JSON files.
 */

data class Pad(
    val slot: Int = 0,
    val title: String = "Empty",
    val colorHex: String = "#334155",
    val mode: PlaybackMode = PlaybackMode.ONE_SHOT,
    val filePath: String? = null,
)

data class Board(
    val id: String = "",
    val name: String = "",
    val pads: List<Pad> = emptyList(),
)

data class PadRef(
    val boardId: String = "",
    val slot: Int = 0,
) {
    val key: String get() = "$boardId:$slot"
}

data class DeckState(
    val version: Int = 1,
    val boards: List<Board> = emptyList(),
    val activeBoardId: String = "",
    val gain: Float = 0.8f,
    val favorites: List<PadRef> = emptyList(),
) {
    val activeBoard: Board? get() = boards.firstOrNull { it.id == activeBoardId } ?: boards.firstOrNull()

    fun board(id: String): Board? = boards.firstOrNull { it.id == id }

    fun pad(ref: PadRef): Pad? = board(ref.boardId)?.pads?.getOrNull(ref.slot)

    fun allFilePaths(): Set<String> =
        boards.flatMap { b -> b.pads.mapNotNull { it.filePath } }.toSet()

    /** Favorites first, then filled up with the active board's pads, max [MINI_DECK_SIZE]. */
    fun miniDeckPads(): List<Pair<PadRef, Pad>> {
        val result = LinkedHashMap<String, Pair<PadRef, Pad>>()
        favorites.forEach { ref -> pad(ref)?.let { result[ref.key] = ref to it } }
        activeBoard?.let { board ->
            board.pads.forEach { pad ->
                if (result.size < MINI_DECK_SIZE && pad.filePath != null) {
                    val ref = PadRef(board.id, pad.slot)
                    result.putIfAbsent(ref.key, ref to pad)
                }
            }
        }
        return result.values.take(MINI_DECK_SIZE)
    }
}

/**
 * A persistent entry in the "Spew Vault" directory.
 * Remembers every sound ever fired, previewed, or imported into the party loopback.
 */
data class SpewEntry(
    val id: String = "",
    val title: String = "",
    val sourceUrl: String? = null,
    val localFilePath: String? = null,
    val category: String = "Meme",
    val spewCount: Int = 1,
    val lastSpewedEpochMs: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
) {
    /** Generates a human-readable relative time string. */
    fun formattedLastSpewed(): String {
        val diff = (System.currentTimeMillis() - lastSpewedEpochMs).coerceAtLeast(0L)
        val sec = diff / 1000L
        val min = sec / 60L
        val hr = min / 60L
        val days = hr / 24L
        return when {
            sec < 10 -> "Just now"
            sec < 60 -> "${sec}s ago"
            min < 60 -> "${min}m ago"
            hr < 24 -> "${hr}h ago"
            else -> "${days}d ago"
        }
    }
}

enum class VaultSortOrder(val label: String) {
    MOST_SPEWED("Most Spewed"),
    RECENT("Recently Spewed"),
    PINNED_FIRST("Pinned / Favorites"),
    ALPHABETICAL("A - Z"),
}
