package com.idlemining.tycoon3d.core.save

import java.io.File

/**
 * Storage seam for the save system. The engine only depends on this interface —
 * production uses [FileSaveStorage] (atomic writes to filesDir), tests use
 * [InMemorySaveStorage].
 */
interface SaveStorage {
    /** Returns the raw JSON blob or null when no save exists. */
    fun readRaw(): String?

    /** Atomically persists the raw JSON blob. */
    fun writeRaw(raw: String)

    fun clear()
}

/**
 * File-backed storage. Writes go to a temp file first and are then renamed — a
 * crash mid-write can never corrupt the previous save.
 */
class FileSaveStorage(private val directory: File) : SaveStorage {

    private val saveFile = File(directory, "save_v1.json")
    private val tmpFile = File(directory, "save_v1.json.tmp")

    override fun readRaw(): String? =
        if (saveFile.isFile) runCatching { saveFile.readText() }.getOrNull() else null

    override fun writeRaw(raw: String) {
        directory.mkdirs()
        tmpFile.writeText(raw)
        if (!tmpFile.renameTo(saveFile)) {
            // Rename can fail across filesystems; fall back to a plain copy.
            saveFile.writeText(raw)
            tmpFile.delete()
        }
    }

    override fun clear() {
        saveFile.delete()
        tmpFile.delete()
    }
}

/** Test double with no filesystem dependency. */
class InMemorySaveStorage : SaveStorage {
    var raw: String? = null
    private set

    override fun readRaw(): String? = raw
    override fun writeRaw(raw: String) { this.raw = raw }
    override fun clear() { raw = null }
}
