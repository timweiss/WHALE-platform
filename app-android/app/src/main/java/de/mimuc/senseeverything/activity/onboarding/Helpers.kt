package de.mimuc.senseeverything.activity.onboarding

import android.net.Uri
import de.mimuc.senseeverything.logging.WHALELog

/**
 * Parses onboarding URL to extract studyKey and source parameters.
 *
 * Expected formats:
 * - https://whale-app.de/onboarding/start?studyKey=ABC123&source=qr_code
 * - whale-app://onboarding/start?studyKey=ABC123&source=qr_code
 *
 * @param url The URL string from the QR code
 * @return Pair of (studyKey, source) if valid, null otherwise
 */
fun parseOnboardingUrl(uri: Uri): Pair<String, String>? {
    return try {
        // Check if it's an onboarding URL
        val isValidPath =
            (uri.host == "whale-app.de" && uri.path?.startsWith("/onboarding/start") == true) ||
                    (uri.host == "onboarding" && uri.path?.startsWith("/start") == true)

        if (!isValidPath) {
            WHALELog.w("QRCodeAnalyzer", "Invalid onboarding URL path: $uri")
            return null
        }

        // Extract studyKey parameter
        val studyKey = uri.getQueryParameter("studyKey")
        if (studyKey.isNullOrBlank()) {
            WHALELog.w("QRCodeAnalyzer", "No studyKey found in URL: $uri")
            return null
        }

        // Extract optional source parameter (default to "")
        val source = uri.getQueryParameter("source") ?: ""

        WHALELog.i("QRCodeAnalyzer", "Parsed URL successfully: studyKey=$studyKey, source=$source")
        Pair(studyKey, source)
    } catch (e: Exception) {
        WHALELog.e("QRCodeAnalyzer", "Error parsing URL: ${e.message}")
        null
    }
}