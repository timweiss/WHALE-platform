package de.mimuc.senseeverything.api.model

import kotlinx.serialization.Serializable
import org.json.JSONObject

data class EnrolmentResponse(
    val studyId: Int,
    val token: String,
    val participantId: String,
    val configuration: StudyConfiguration
) {
    companion object {
        fun fromJson(json: JSONObject): EnrolmentResponse {
            val studyId = json.getInt("studyId")
            val token = json.getString("token")
            val participantId = json.getString("participantId")
            val configuration = StudyConfiguration.fromJson(json.getJSONObject("configuration"))
            return EnrolmentResponse(studyId, token, participantId, configuration)
        }
    }
}

@Serializable
data class StudyConfiguration(
    val interactionWidgetStrategy: InteractionWidgetDisplayStrategy
) {
    companion object {
        fun fromJson(json: JSONObject): StudyConfiguration {
            val interactionWidgetStrategy = InteractionWidgetDisplayStrategy.valueOf(
                json.getString("interactionWidgetStrategy").uppercase()
            )
            return StudyConfiguration(interactionWidgetStrategy)
        }
    }
}

enum class InteractionWidgetDisplayStrategy {
    DEFAULT,
    BUCKETED,
    RANDOM
}