package com.jms.pssoundboard

data class SuggestionCategory(
    val title: String,
    val emoji: String,
    val queries: List<String>
)

object SearchSuggestions {

    // Top quick-filter chips shown immediately when opening the Search page
    val quickCategoryChips = listOf(
        "🔥 Trending Now",
        "🏆 All-Time Funny",
        "📸 Instagram & TikTok",
        "🎬 Movies & TV",
        "📺 YouTube Classics",
        "🎵 Viral Lyrics",
        "🎮 Gaming SFX"
    )

    // Categorised popular searches & most-used soundboard clips
    val categories = listOf(
        SuggestionCategory(
            title = "All-Time Most Used",
            emoji = "🏆",
            queries = listOf(
                "Vine Boom",
                "GET OUT!",
                "HUH?",
                "Bruh",
                "Record Scratch",
                "Fart Reverb",
                "Emotional Damage",
                "Airhorn",
                "Sad Violin",
                "Crickets"
            )
        ),
        SuggestionCategory(
            title = "Viral on Instagram & Reels",
            emoji = "📸",
            queries = listOf(
                "Wait a minute",
                "Oh no",
                "Side eye",
                "Bombastic side eye",
                "SpongeBob Fail",
                "Boo-womp",
                "Prowler",
                "Mr Krabs walking",
                "Slap Oh",
                "Suspense Piano"
            )
        ),
        SuggestionCategory(
            title = "YouTube, Movies & TV Quotes",
            emoji = "🎬",
            queries = listOf(
                "Why are you running",
                "Directed by Robert B Weide",
                "To Be Continued",
                "FBI Open Up",
                "Law and Order",
                "2000 Years Later",
                "Surprise Motherfucker",
                "Wow Owen Wilson"
            )
        ),
        SuggestionCategory(
            title = "Viral Lyrics & Music Drops",
            emoji = "🎵",
            queries = listOf(
                "Bass Drop",
                "Careless Whisper",
                "Phonk",
                "Run Vine",
                "Curb Your Enthusiasm",
                "X Files Theme",
                "Evil Morty",
                "Kevin MacLeod"
            )
        )
    )

    // Flattened list of default suggestions when the search bar is empty or focused
    val defaultSearchTags: List<String> = categories.flatMap { it.queries }

    // Filter live autocomplete suggestions as the user types
    fun getMatchingSuggestions(input: String): List<String> {
        if (input.isBlank()) return defaultSearchTags.take(12)
        return defaultSearchTags.filter { it.contains(input.trim(), ignoreCase = true) }
    }
}
