package de.mimuc.senseeverything.api.model

import kotlinx.serialization.Serializable

/** When an element was shown and answered, in epoch milliseconds; null if it never happened. */
@Serializable
data class ElementAnswerTime(
    val firstDisplayed: Long? = null,
    val lastDisplayed: Long? = null,
    /** the first time the value became answered */
    val answered: Long? = null,
    /** the last change that left the value answered */
    val updated: Long? = null
) {
    fun markDisplayed(now: Long) = copy(firstDisplayed = firstDisplayed ?: now, lastDisplayed = now)

    fun markAnswered(now: Long) = copy(answered = answered ?: now, updated = now)
}
