package de.mimuc.senseeverything.api

class ApiResources {
    companion object {
        const val API_BASE = "https://sisensing.medien.ifi.lmu.de/v1"

        fun studyById(studyId: Int): String {
            return "$API_BASE/study/$studyId"
        }

        fun studyByEnrolmentKey(enrolmentKey: String): String {
            return "$API_BASE/study/$enrolmentKey"
        }

        fun completionStatus(): String {
            return "${API_BASE}/completion"
        }

        fun enrolment(): String {
            return "${API_BASE}/enrolment"
        }

        fun sensorReadingsBatched(): String {
            return "${API_BASE}/reading/batch"
        }

        fun questionnaires(studyId: Int) : String {
            return "$API_BASE/study/$studyId/questionnaire"
        }

        fun questionnaire(studyId: Int, questionnaireId: Int): String {
            return "$API_BASE/study/$studyId/questionnaire/$questionnaireId"
        }

        fun questionnaireAnswer(studyId: Int, questionnaireId: Int): String {
            return "$API_BASE/study/$studyId/questionnaire/$questionnaireId/answer"
        }
    }
}