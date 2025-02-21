package de.mimuc.senseeverything.api.model

import kotlinx.serialization.Serializable
import org.json.JSONObject

data class EnrolmentResponse(
    val studyId: Int,
    val token: String,
    val participantId: String,
    val phases: List<ExperimentalGroupPhase>,
) {
    companion object {
        fun fromJson(json: JSONObject): EnrolmentResponse {
            val studyId = json.getInt("studyId")
            val token = json.getString("token")
            val participantId = json.getString("participantId")
            val phases = json.getJSONArray("phases").let { phasesJson ->
                (0 until phasesJson.length()).map { index ->
                    ExperimentalGroupPhase.fromJson(phasesJson.getJSONObject(index))
                }
            }
            return EnrolmentResponse(studyId, token, participantId, phases)
        }
    }
}

enum class InteractionWidgetDisplayStrategy {
    DEFAULT,
    BUCKETED
}

@Serializable
data class ExperimentalGroupPhase(
    val name: String,
    val fromDay: Int,
    val durationDays: Int,
    val interactionWidgetStrategy: InteractionWidgetDisplayStrategy
) {
    companion object {
        fun fromJson(json: JSONObject): ExperimentalGroupPhase {
            val name = json.getString("name")
            val fromDay = json.getInt("fromDay")
            val durationDays = json.getInt("durationDays")
            val interactionWidgetStrategy = InteractionWidgetDisplayStrategy.valueOf(
                json.getString("interactionWidgetStrategy").uppercase()
            )
            return ExperimentalGroupPhase(name, fromDay, durationDays, interactionWidgetStrategy)
        }
    }
}