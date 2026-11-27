package nl.petrichor.app.data.affiliate

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Navigates a nested JSON object path; null when any segment is missing or not an object. */
internal fun JsonElement.objectAt(vararg path: String): JsonObject? {
    var current: JsonElement = this
    for (segment in path) {
        current = (current as? JsonObject)?.get(segment) ?: return null
    }
    return current as? JsonObject
}

/** Navigates to a nested JSON array; null when any segment is missing or not an array. */
internal fun JsonElement.arrayAt(vararg path: String): JsonArray? {
    var current: JsonElement = this
    for (segment in path) {
        current = (current as? JsonObject)?.get(segment) ?: return null
    }
    return current as? JsonArray
}

/** Reads a string at a nested path; null when missing or not a primitive. */
internal fun JsonElement.stringAt(vararg path: String): String? {
    var current: JsonElement = this
    for (segment in path) {
        current = (current as? JsonObject)?.get(segment) ?: return null
    }
    return (current as? JsonPrimitive)?.contentOrNull()
}

/** Reads a number at a nested path as Double; null when missing or not numeric. */
internal fun JsonElement.doubleAt(vararg path: String): Double? {
    var current: JsonElement = this
    for (segment in path) {
        current = (current as? JsonObject)?.get(segment) ?: return null
    }
    return (current as? JsonPrimitive)?.content?.toDoubleOrNull()
}

private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else null
