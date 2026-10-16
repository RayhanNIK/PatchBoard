package com.example.patchboard.map

import com.google.android.gms.maps.model.LatLng

object MapDefaults {
    const val CAMPUS_LAT = -7.7706121
    const val CAMPUS_LNG = 110.4451939
    const val ZOOM = 17f

    val campus: LatLng get() = LatLng(CAMPUS_LAT, CAMPUS_LNG)
}
