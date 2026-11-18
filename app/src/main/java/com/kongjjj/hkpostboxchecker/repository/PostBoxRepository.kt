package com.kongjjj.hkpostboxchecker.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.kongjjj.hkpostboxchecker.model.PostBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class PostBoxRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val userEditedFile: File get() = File(context.filesDir, USER_EDITED_FILE_NAME)

    companion object {
        private const val CSDI_FILE_API_URL =
            "https://portal.csdi.gov.hk/csdi-webpage/file-api?dataset_id=hkpo_rcd_1638773801007_13653&format=geojson&layer_name=SPB"
        private const val CSDI_DATAQUERY_API_URL =
            "https://api.csdi.gov.hk/apim/dataquery/api/?id=hkpo_rcd_1638773801007_13653&dataType=geojson"
        private const val ASSET_FILE_NAME = "hk_street_posting_boxes.geojson"
        private const val USER_EDITED_FILE_NAME = "user_post_boxes.json"
    }

    suspend fun saveUserEditedPostBoxes(list: List<PostBox>) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(list)
            userEditedFile.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearUserEdits() {
        if (userEditedFile.exists()) {
            userEditedFile.delete()
        }
    }

    suspend fun getPostBoxes(forceRefresh: Boolean = false): Result<List<PostBox>> =
        withContext(Dispatchers.IO) {
            if (forceRefresh) {
                clearUserEdits()
            } else if (userEditedFile.exists()) {
                try {
                    val json = userEditedFile.readText(Charsets.UTF_8)
                    val type = object : TypeToken<List<PostBox>>() {}.type
                    val userList: List<PostBox> = gson.fromJson(json, type)
                    if (userList.isNotEmpty()) {
                        return@withContext Result.success(userList)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            var jsonString: String? = null
            if (forceRefresh) {
                jsonString = fetchFromUrl(CSDI_FILE_API_URL) ?: fetchFromUrl(CSDI_DATAQUERY_API_URL)
            } else {
                jsonString = loadFromAssets() ?: fetchFromUrl(CSDI_FILE_API_URL) ?: fetchFromUrl(CSDI_DATAQUERY_API_URL)
            }

            if (jsonString != null) {
                try {
                    val list = parseGeoJson(jsonString)
                    if (list.isNotEmpty()) {
                        return@withContext Result.success(list)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fallback load from asset
            val assetJson = loadFromAssets()
            if (assetJson != null) {
                try {
                    val list = parseGeoJson(assetJson)
                    return@withContext Result.success(list)
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            }

            Result.failure(Exception("Failed to load post box data"))
        }

    private fun fetchFromUrl(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; HKPostBoxChecker)")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadFromAssets(): String? {
        return try {
            context.assets.open(ASSET_FILE_NAME).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGeoJson(jsonStr: String): List<PostBox> {
        val root = JsonParser.parseString(jsonStr).asJsonObject
        val features = root.getAsJsonArray("features") ?: return emptyList()
        val result = mutableListOf<PostBox>()

        for (i in 0 until features.size()) {
            val feature = features.get(i).asJsonObject
            val props = feature.getAsJsonObject("properties") ?: JsonObject()
            val geom = feature.getAsJsonObject("geometry")

            var lat = props.optDouble("Latitude", 0.0)
            var lng = props.optDouble("Longitude", 0.0)

            if ((lat == 0.0 || lng == 0.0) && geom != null && geom.has("coordinates")) {
                val coords = geom.getAsJsonArray("coordinates")
                if (coords != null && coords.size() >= 2) {
                    lng = coords.get(0).asDouble
                    lat = coords.get(1).asDouble
                }
            }

            if (lat == 0.0 || lng == 0.0) continue

            val postBox = PostBox(
                id = props.optInt("OBJECTID", i + 1),
                nameEn = props.optString("NameEN"),
                nameTc = props.optString("NameTC"),
                addressEn = props.optString("AddressEN"),
                addressTc = props.optString("AddressTC"),
                districtEn = props.optString("DistrictEN"),
                districtTc = props.optString("DistrictTC"),
                subDistrictEn = props.optString("SubDistrictEN"),
                subDistrictTc = props.optString("SubDistrictTC"),
                lastCollectionTimeEn = props.optString("LastCollectionTimeEN"),
                lastCollectionTimeTc = props.optString("LastCollectionTimeTC"),
                openingHoursEn = props.optString("OpeningHoursEN"),
                openingHoursTc = props.optString("OpeningHoursTC"),
                telephoneEn = props.optString("TelephoneEN"),
                telephoneTc = props.optString("TelephoneTC"),
                emailEn = props.optString("EmailAddressEN"),
                emailTc = props.optString("EmailAddressTC"),
                websiteEn = props.optString("WebsiteEN"),
                websiteTc = props.optString("WebsiteTC"),
                remarksEn = props.optString("RemarksEN"),
                remarksTc = props.optString("RemarksTC"),
                latitude = lat,
                longitude = lng
            )
            result.add(postBox)
        }
        return result
    }

    private fun JsonObject.optString(key: String, default: String = ""): String {
        if (!has(key) || get(key).isJsonNull) return default
        return get(key).asString ?: default
    }

    private fun JsonObject.optInt(key: String, default: Int = 0): Int {
        if (!has(key) || get(key).isJsonNull) return default
        return try { get(key).asInt } catch (e: Exception) { default }
    }

    private fun JsonObject.optDouble(key: String, default: Double = 0.0): Double {
        if (!has(key) || get(key).isJsonNull) return default
        return try { get(key).asDouble } catch (e: Exception) { default }
    }
}
