package com.example.patchboard.map

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

/**
 * A small, non-interactive "lite mode" map showing one pin. Made for lists (report cards):
 * it creates its MapView when attached to the screen and destroys it when detached, so it is
 * safe inside a RecyclerView and needs no lifecycle wiring.
 *
 * Touches are passed through to the parent, so the card keeps handling its own clicks/scrolling.
 */
class MiniMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var mapView: MapView? = null
    private var map: GoogleMap? = null
    private var lat = 0.0
    private var lng = 0.0
    private var pinColor = 0
    private var hasPin = false

    /** Shows [lat],[lng] with a pin in [color]. */
    fun setPin(lat: Double, lng: Double, color: Int) {
        this.lat = lat; this.lng = lng; this.pinColor = color; hasPin = true
        map?.let(::render)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun start() {
        if (mapView != null) return
        val options = GoogleMapOptions().liteMode(true).mapToolbarEnabled(false)
        val mv = MapView(context, options)
        addView(mv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mv.onCreate(null)
        mv.onStart()
        mv.onResume()
        mapView = mv
        mv.getMapAsync { gm ->
            if (mapView !== mv) return@getMapAsync        // detached before the map was ready
            map = gm
            gm.uiSettings.setAllGesturesEnabled(false)
            gm.uiSettings.isMapToolbarEnabled = false
            render(gm)
        }
    }

    private fun stop() {
        mapView?.let {
            it.onPause(); it.onStop(); it.onDestroy()
            removeView(it)
        }
        mapView = null
        map = null
    }

    private fun render(gm: GoogleMap) {
        if (!hasPin) return
        val p = LatLng(lat, lng)
        gm.clear()
        gm.addMarker(MarkerOptions().position(p).icon(PinIcons.pin(context, pinColor)).anchor(0.5f, 0.5f))
        gm.moveCamera(CameraUpdateFactory.newLatLngZoom(p, 17f))
    }

    // Lite-mode maps open the Google Maps app when tapped; swallow that and let the parent react.
    override fun onInterceptTouchEvent(ev: MotionEvent?) = true
    override fun onTouchEvent(event: MotionEvent?) = false
}
