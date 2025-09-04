package de.mimuc.senseeverything.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EnrolmentRequest(
    val enrolmentKey: String
)

@Serializable
data class EnrolmentResponse(
    val studyId: Int,
    val token: String,
    val participantId: String,
    val phases: List<ExperimentalGroupPhase>,
)

@Serializable
enum class InteractionWidgetDisplayStrategy {
    @SerialName("Default") DEFAULT,
    @SerialName("Bucketed") BUCKETED,
    @SerialName("Hidden") HIDDEN
}

@Serializable
data class ExperimentalGroupPhase(
    val experimentalGroupId: Int,
    val name: String,
    val fromDay: Int,
    val durationDays: Int,
    val interactionWidgetStrategy: InteractionWidgetDisplayStrategy
)
