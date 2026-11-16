package com.kongjjj.hkpostboxchecker.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.kongjjj.hkpostboxchecker.model.TrackRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {

    fun exportTrackToGpx(context: Context, track: TrackRecord): Result<String> {
        return try {
            val gpxXml = buildGpxXml(track)
            val safeTitle = track.title.replace(Regex("""[\\/:*?"<>|]"""), "_")
            val fileName = "${safeTitle}.gpx"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HKPostBoxMap")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.failure(Exception("Failed to insert MediaStore entry"))

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(gpxXml.toByteArray(Charsets.UTF_8))
                }
                Result.success("Downloads/HKPostBoxMap/$fileName")
            } else {
                @Suppress("DEPRECATION")
                val downloadsFolder = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "HKPostBoxMap"
                )
                if (!downloadsFolder.exists()) downloadsFolder.mkdirs()
                val targetFile = File(downloadsFolder, fileName)
                targetFile.writeText(gpxXml, Charsets.UTF_8)
                Result.success(targetFile.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildGpxXml(track: TrackRecord): String {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"HK Post Box Map\"\n")
        sb.append("  xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
        sb.append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
        sb.append("  xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(track.title)}</name>\n")
        sb.append("    <time>${isoFormat.format(Date(track.startTime))}</time>\n")
        sb.append("  </metadata>\n")
        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(track.title)}</name>\n")
        sb.append("    <trkseg>\n")

        for (pt in track.points) {
            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            if (pt.altitude != 0.0) {
                sb.append("        <ele>${pt.altitude}</ele>\n")
            }
            sb.append("        <time>${isoFormat.format(Date(pt.timestamp))}</time>\n")
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
