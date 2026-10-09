package com.packwork.tracker.data

import java.io.File

/** Stores everything in one JSON file inside the app's private storage. */
class Repository(private val file: File) {

    fun load(): StoreData {
        if (!file.exists()) return StoreData()
        return try {
            StoreData.parseOrNull(file.readText()) ?: throw IllegalStateException("Unreadable data file")
        } catch (e: Exception) {
            // Never overwrite something we couldn't read: keep it aside.
            runCatching { file.renameTo(File(file.parentFile, file.name + ".corrupt-" + System.currentTimeMillis())) }
            StoreData()
        }
    }

    fun save(json: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
