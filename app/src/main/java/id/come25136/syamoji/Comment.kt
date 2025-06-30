package id.come25136.syamoji

import java.time.ZonedDateTime

data class Comment(
    val id: String,
    val timestamp: ZonedDateTime,
    val isPast: Boolean,
    val content: String,
)