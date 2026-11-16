package com.kongjjj.hkpostboxchecker.model

import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val altitude: Double = 0.0
) : Serializable

data class TrackRecord(
    val id: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Double,
    val points: List<TrackPoint>
) : Serializable {

    val durationSeconds: Long
        get() = if (endTime > startTime) (endTime - startTime) / 1000 else 0

    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(startTime))
    }

    fun getFormattedDistance(): String {
        return if (distanceMeters >= 1000) {
            String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1000.0)
        } else {
            String.format(Locale.getDefault(), "%.0f m", distanceMeters)
        }
    }

    fun getFormattedDuration(): String {
        val seconds = durationSeconds
        val mins = seconds / 60
        val hrs = mins / 60
        val remMins = mins % 60
        val remSecs = seconds % 60
        return if (hrs > 0) {
            String.format(Locale.getDefault(), "%d小時 %d分 %d秒", hrs, remMins, remSecs)
        } else {
            String.format(Locale.getDefault(), "%d分 %d秒", remMins, remSecs)
        }
    }
}
