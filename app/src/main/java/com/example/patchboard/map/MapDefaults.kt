package com.example.patchboard.map

import com.google.android.gms.maps.model.LatLng

/** Where maps open when there is nothing to show yet. TODO: set this to your school's location. */
object MapDefaults {
    const val CAMPUS_LAT = -6.2088
    const val CAMPUS_LNG = 106.8456
    const val ZOOM = 17f

    val campus: LatLng get() = LatLng(CAMPUS_LAT, CAMPUS_LNG)
}
