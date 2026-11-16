package com.kongjjj.hkpostboxchecker.repository

import android.content.Context
import com.google.gson.Gson
import com.kongjjj.hkpostboxchecker.model.TrackRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TrackRepository(private val context: Context) {

    private val gson = Gson()
    private val tracksDir: File
        get() = File(context.filesDir, "tracks").apply {
            if (!exists()) mkdirs()
        }

    suspend fun saveTrack(track: TrackRecord): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(tracksDir, "${track.id}.json")
            val json = gson.toJson(track)
            file.writeText(json)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllTracks(): List<TrackRecord> = withContext(Dispatchers.IO) {
        val files = tracksDir.listFiles { _, name -> name.endsWith(".json") } ?: return@withContext emptyList()
        val result = mutableListOf<TrackRecord>()
        for (file in files) {
            try {
                val json = file.readText()
                val track = gson.fromJson(json, TrackRecord::class.java)
                if (track != null) {
                    result.add(track)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        result.sortByDescending { it.startTime }
        result
    }

    suspend fun deleteTrack(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(tracksDir, "$trackId.json")
        if (file.exists()) file.delete() else false
    }
}
