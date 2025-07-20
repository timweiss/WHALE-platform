package de.mimuc.senseeverything.api.model

import kotlinx.serialization.Serializable

@Serializable
data class Study(
    val name: String,
    val id: Int,
    val enrolmentKey: String,
    val description: String,
    val contactEmail: String,
    val durationDays: Int
)