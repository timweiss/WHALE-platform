package de.mimuc.senseeverything.api.model

import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.ApiResources
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateEnrolmentRequest(
    val enrolmentKey: String,
    val source: String?
)

@Serializable
data class CreateEnrolmentResponse(
    val studyId: Int,
    val token: String,
    val participantId: String,
    val phases: List<ExperimentalGroupPhase>,
)

@Serializable
data class EnrolmentResponse(
    val enrolmentId: Int,
    val debugEnabled: Boolean,
    val additionalInformation: String?
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

suspend fun getEnrolmentInfo(
    client: ApiClient,
    token: String
): EnrolmentResponse {
    return client.getSerialized<EnrolmentResponse>(
        endpoint = ApiResources.enrolment(),
        token = token
    )
}