package com.kongjjj.hkpostboxchecker.model

import java.io.Serializable

data class PostBox(
    val id: Int,
    val nameEn: String,
    val nameTc: String,
    val addressEn: String,
    val addressTc: String,
    val districtEn: String,
    val districtTc: String,
    val subDistrictEn: String,
    val subDistrictTc: String,
    val lastCollectionTimeEn: String,
    val lastCollectionTimeTc: String,
    val openingHoursEn: String,
    val openingHoursTc: String,
    val telephoneEn: String,
    val telephoneTc: String,
    val emailEn: String,
    val emailTc: String,
    val websiteEn: String,
    val websiteTc: String,
    val remarksEn: String,
    val remarksTc: String,
    val latitude: Double,
    val longitude: Double
) : Serializable {

    fun getDisplayName(isTc: Boolean): String {
        return if (isTc && nameTc.isNotBlank() && nameTc != "N.A.") nameTc else nameEn
    }

    fun getDisplayAddress(isTc: Boolean): String {
        return if (isTc && addressTc.isNotBlank() && addressTc != "N.A.") addressTc else addressEn
    }

    fun getDisplayDistrict(isTc: Boolean): String {
        return if (isTc && districtTc.isNotBlank() && districtTc != "N.A.") districtTc else districtEn
    }

    fun getDisplaySubDistrict(isTc: Boolean): String {
        return if (isTc && subDistrictTc.isNotBlank() && subDistrictTc != "N.A.") subDistrictTc else subDistrictEn
    }

    fun getDisplayCollectionTime(isTc: Boolean): String {
        return if (isTc && lastCollectionTimeTc.isNotBlank() && lastCollectionTimeTc != "N.A.") lastCollectionTimeTc else lastCollectionTimeEn
    }

    val boxNumber: String
        get() {
            val numRegex = Regex("""(\d+[A-Z]?)""")
            val match = numRegex.find(nameEn) ?: numRegex.find(nameTc)
            return match?.value ?: id.toString()
        }
}
