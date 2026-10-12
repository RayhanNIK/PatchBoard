package com.example.patchboard.map

import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.maps.MapView

/** Forwards a fragment's view lifecycle to a [MapView], which the Maps SDK requires. */
class MapViewLifecycle(
    private val mapView: MapView,
    savedState: Bundle?,
) : DefaultLifecycleObserver {

    private val restored = savedState?.getBundle(KEY)

    override fun onCreate(owner: LifecycleOwner) = mapView.onCreate(restored)
    override fun onStart(owner: LifecycleOwner) = mapView.onStart()
    override fun onResume(owner: LifecycleOwner) = mapView.onResume()
    override fun onPause(owner: LifecycleOwner) = mapView.onPause()
    override fun onStop(owner: LifecycleOwner) = mapView.onStop()
    override fun onDestroy(owner: LifecycleOwner) = mapView.onDestroy()

    fun save(outState: Bundle) {
        val b = Bundle()
        mapView.onSaveInstanceState(b)
        outState.putBundle(KEY, b)
    }

    private companion object { const val KEY = "mapview_state" }
}
