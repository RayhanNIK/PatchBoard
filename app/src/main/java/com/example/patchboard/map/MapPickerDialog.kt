package com.example.patchboard.map

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import com.example.patchboard.R
import com.example.patchboard.databinding.DialogMapPickerBinding
import com.example.patchboard.model.Priority
import com.example.patchboard.ui.color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Full-screen map where the reporter taps to drop (and drag to adjust) a pin.
 * Sends the result with the Fragment Result API: see [RESULT_KEY].
 */
class MapPickerDialog : DialogFragment() {

    private var _b: DialogMapPickerBinding? = null
    private val b get() = _b!!

    private var pin: LatLng? = null
    private var map: GoogleMap? = null
    private var marker: Marker? = null
    private var mapLifecycle: MapViewLifecycle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.PatchBoard_FullscreenDialog)
        val src = savedInstanceState ?: arguments
        if (src != null && src.containsKey(KEY_LAT)) pin = LatLng(src.getDouble(KEY_LAT), src.getDouble(KEY_LNG))
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).also { d ->
            d.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = DialogMapPickerBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val topBase = b.topBar.paddingTop
        val bottomBase = b.bottomWrap.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.topBar.updatePadding(top = topBase + bars.top)
            b.bottomWrap.updatePadding(bottom = bottomBase + bars.bottom)
            insets
        }

        mapLifecycle = MapViewLifecycle(b.mapView, savedInstanceState)
            .also { viewLifecycleOwner.lifecycle.addObserver(it) }
        b.mapView.getMapAsync(::onMapReady)

        b.btnClose.setOnClickListener { dismiss() }
        b.btnConfirm.setOnClickListener {
            val p = pin ?: return@setOnClickListener
            parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf(KEY_LAT to p.latitude, KEY_LNG to p.longitude))
            dismiss()
        }
        renderState()
    }

    private fun onMapReady(gm: GoogleMap) {
        if (_b == null) return
        map = gm
        gm.uiSettings.isZoomControlsEnabled = true
        gm.uiSettings.isMapToolbarEnabled = false

        val start = pin
        gm.moveCamera(CameraUpdateFactory.newLatLngZoom(start ?: MapDefaults.campus, MapDefaults.ZOOM))
        if (start != null) place(start)

        gm.setOnMapClickListener { place(it) }
        gm.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(m: Marker) {}
            override fun onMarkerDrag(m: Marker) {}
            override fun onMarkerDragEnd(m: Marker) { pin = m.position; renderState() }
        })
    }

    private fun place(latLng: LatLng) {
        val gm = map ?: return
        pin = latLng
        val existing = marker
        if (existing == null) {
            val priority = Priority.values().getOrElse(arguments?.getInt(ARG_PRIORITY) ?: 0) { Priority.MEDIUM }
            marker = gm.addMarker(
                MarkerOptions().position(latLng).draggable(true).anchor(0.5f, 0.5f)
                    .icon(PinIcons.pin(requireContext(), requireContext().color(priority.dotRes)))
            )
        } else {
            existing.position = latLng
        }
        renderState()
    }

    private fun renderState() {
        val p = pin
        b.tvCoords.text = if (p == null) getString(R.string.map_pick_hint)
        else getString(R.string.map_pin_coords, p.latitude, p.longitude)
        b.btnConfirm.isEnabled = p != null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pin?.let { outState.putDouble(KEY_LAT, it.latitude); outState.putDouble(KEY_LNG, it.longitude) }
        if (_b != null) mapLifecycle?.save(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        marker = null; map = null; mapLifecycle = null; _b = null
    }

    companion object {
        const val RESULT_KEY = "map_pin_result"
        const val KEY_LAT = "lat"
        const val KEY_LNG = "lng"
        private const val ARG_PRIORITY = "priority"

        /** [lat]/[lng] null = no pin yet. [priorityOrdinal] only decides the pin colour. */
        fun newInstance(lat: Double?, lng: Double?, priorityOrdinal: Int) = MapPickerDialog().apply {
            arguments = Bundle().apply {
                if (lat != null && lng != null) { putDouble(KEY_LAT, lat); putDouble(KEY_LNG, lng) }
                putInt(ARG_PRIORITY, priorityOrdinal)
            }
        }
    }
}
