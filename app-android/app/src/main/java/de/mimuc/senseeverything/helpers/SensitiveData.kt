package de.mimuc.senseeverything.helpers

import de.mimuc.senseeverything.logging.WHALELog
import java.security.MessageDigest

fun generateSensitiveDataSalt() : String {
    return getRandomString(16)
}

fun getSensitiveDataHash(data: String, salt: String): String {
    if (salt.isEmpty() || salt == "changemepleeease") {
        WHALELog.w("SensitiveData", "Salt is empty or null")
    }
    return (data + salt).sha256()
}

internal fun getRandomString(length: Int) : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

internal fun String.sha256(): String {
    return hashString(this, "SHA-256")
}

private fun hashString(input: String, algorithm: String): String {
    return MessageDigest
        .getInstance(algorithm)
        .digest(input.toByteArray())
        .fold("", { str, it -> str + "%02x".format(it) })
}
