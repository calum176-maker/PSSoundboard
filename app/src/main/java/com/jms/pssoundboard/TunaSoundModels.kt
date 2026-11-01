package com.jms.pssoundboard

import com.google.gson.annotations.SerializedName
import com.jms.pssoundboard.data.LibraryItem

data class TunaProperties(
    @SerializedName("duration") val duration: Double? = null,
    @SerializedName("loudness") val loudness: Double? = null,
    @SerializedName("maximumAmplitude") val maximumAmplitude: Double? = null
)

data class TunaSound(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("tags") val tags: List<String>? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("soundPath") val soundPath: String? = null,
    @SerializedName("imagePath") val imagePath: String? = null,
    @SerializedName("thumbnailS") val thumbnailS: String? = null,
    @SerializedName("thumbnailM") val thumbnailM: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("properties") val properties: TunaProperties? = null,
    @SerializedName("duration") val duration: Double? = null
) {
    val resolvedTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "Meme Sound"

    val resolvedAudioUrl: String?
        get() = path?.takeIf { it.isNotBlank() } ?: soundPath?.takeIf { it.isNotBlank() }

    val resolvedImageUrl: String?
        get() = thumbnailM ?: thumbnailS ?: imagePath ?: image

    val resolvedDurationMs: Long?
        get() = (properties?.duration ?: duration)?.toLong()

    fun toLibraryItem(): LibraryItem {
        val cat = category?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "Meme"
        return LibraryItem(
            id = "tuna:$id",
            title = resolvedTitle,
            category = cat,
            url = resolvedAudioUrl,
            source = "Voicemod Tuna",
            durationMs = resolvedDurationMs,
            iconUrl = resolvedImageUrl
        )
    }
}

data class TunaSearchResponse(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("size") val size: Int = 10,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("lastPage") val lastPage: Int = 1,
    @SerializedName("items") val items: List<TunaSound>? = emptyList()
)
