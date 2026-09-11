package com.idlemining.tycoon3d.core.save

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Forward migration chain for saves.
 *
 * A save blob is stored as a raw JSON object exactly as written by the version
 * that produced it. [migrateToCurrent] walks it step by step:
 *
 *     v0 → v1 → v2 → ... → [SaveSchema.VERSION]
 *
 * Each step is a pure `(JsonObject) -> JsonObject` transform registered in
 * [STEPS]. Unknown future versions (a save newer than this build) are rejected.
 */
object SaveMigrations {

    /** migration[fromVersion] = transform producing a version+1 object. */
    private val STEPS: Map<Int, (JsonObject) -> JsonObject> = buildMap {
        // v0 is a hypothetical pre-release shape: money was a string like "$125".
        // It exists to prove the migration machinery end-to-end in tests and
        // documents the pattern every real future migration follows.
        put(0, ::migrate0to1)
    }

    /**
     * Migrates a raw save object to the current schema version. `fromVersion` is
     * read from the object's `version` field when present, else assumed v0.
     */
    fun migrateToCurrent(raw: JsonObject): JsonObject {
        var version = raw["version"]?.jsonPrimitive?.int ?: 0
        var obj = raw
        while (version < SaveSchema.VERSION) {
            val step = STEPS[version]
                ?: throw SaveFormatException("No migration path from save version $version")
            obj = step(obj)
            version++
        }
        if (version > SaveSchema.VERSION) {
            throw SaveFormatException(
                "Save version $version is newer than this build (${SaveSchema.VERSION}) — update the app",
            )
        }
        return obj
    }

    /** v0 → v1: money as string "$125" → number 125.0; version field stamped. */
    private fun migrate0to1(old: JsonObject): JsonObject = buildJsonObject {
        old.forEach { (key, value) ->
            if (key == "money" && value is JsonPrimitive && value.isString) {
                val digits = value.content.filter { it.isDigit() }
                put("money", JsonPrimitive(digits.ifEmpty { "0" }.toDouble()))
            } else if (key != "version") {
                put(key, value)
            }
        }
        put("version", JsonPrimitive(1))
    }
}

class SaveFormatException(message: String) : Exception(message)

/** Tolerant decoder used by both the real storage and tests. */
val SaveJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Convenience: decode + migrate a raw JSON string into [SaveData]. */
fun decodeSave(raw: String): SaveData {
    val obj = SaveJson.parseToJsonElement(raw).jsonObject
    val migrated = SaveMigrations.migrateToCurrent(obj)
    return SaveJson.decodeFromJsonElement(SaveData.serializer(), migrated)
}
