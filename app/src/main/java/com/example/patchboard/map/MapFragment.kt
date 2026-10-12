package com.example.patchboard.map

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.patchboard.R
import com.example.patchboard.analytics.AnalyticsScope
import com.example.patchboard.analytics.BoardReportSource
import com.example.patchboard.analytics.ReportRecord
import com.example.patchboard.analytics.ReportSource
import com.example.patchboard.databinding.FragmentMapBinding
import com.example.patchboard.model.Stage
import com.example.patchboard.ui.BoardViewModel
import com.example.patchboard.ui.CommentsSheet
import com.example.patchboard.ui.color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * The Map tab: every report that has a pin, coloured by priority (fixed ones are faded).
 *
 * Like the Analytics page it only talks to a [ReportSource], so workspaces plug in the same way:
 * change [createSource] and pass a workspace id to [newInstance].
 */
class MapFragment : Fragment() {

    private var _b: FragmentMapBinding? = null
    private val b get() = _b!!

    private lateinit var source: ReportSource
    private var scope = AnalyticsScope.ALL
    private var map: GoogleMap? = null
    private var mapLifecycle: MapViewLifecycle? = null

    private var records: List<ReportRecord> = emptyList()
    private val markers = HashMap<Int, Marker>()
    private var selectedId = -1
    private var focusId = -1            // report to zoom to on first render (from a card)
    private var framed = false

    /** The one place that decides where reports come from. */
    private fun createSource(): ReportSource {
        val board = ViewModelProvider(requireActivity())[BoardViewModel::class.java]
        return BoardReportSource(board)
        // TODO(workspaces): return WorkspaceReportSource(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope = AnalyticsScope(arguments?.getString(ARG_WORKSPACE_ID))
        selectedId = savedInstanceState?.getInt(STATE_SELECTED, -1) ?: -1
        if (savedInstanceState == null) focusId = arguments?.getInt(ARG_FOCUS_ID, -1) ?: -1
        else framed = true              // keep the user's camera across rotation (MapView restores it)
        source = createSource()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentMapBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val headerTop = b.header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(b.header) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, headerTop + top, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(b.header)

        mapLifecycle = MapViewLifecycle(b.mapView, savedInstanceState)
            .also { viewLifecycleOwner.lifecycle.addObserver(it) }
        b.mapView.getMapAsync(::onMapReady)

        b.btnSelOpen.setOnClickListener {
            if (selectedId >= 0) {
                CommentsSheet.newInstance(selectedId).show(requireActivity().supportFragmentManager, "comments")
            }
        }

        // Re-renders whenever reports change (new report, priority/stage change...).
        source.observe(viewLifecycleOwner) {
            if (_b != null) { records = source.records(scope).filter { it.hasPin }; render() }
        }
    }

    private fun onMapReady(gm: GoogleMap) {
        if (_b == null) return
        map = gm
        gm.uiSettings.isZoomControlsEnabled = true
        gm.uiSettings.isMapToolbarEnabled = false
        gm.setOnMarkerClickListener { m ->
            (m.tag as? Int)?.let { select(it, animate = true) }
            true                                        // we handle it (no default info window)
        }
        gm.setOnMapClickListener { selectedId = -1; showSelected() }
        render()
    }

    // ---------------------------------------------------------------- rendering

    private fun render() {
        val gm = map ?: return
        val ctx = requireContext()

        gm.clear()
        markers.clear()
        records.forEach { r ->
            val m = gm.addMarker(
                MarkerOptions()
                    .position(LatLng(r.lat!!, r.lng!!))
                    .icon(PinIcons.pin(ctx, ctx.color(r.priority.dotRes)))
                    .anchor(0.5f, 0.5f)
                    .alpha(if (r.isFixed) 0.45f else 1f)       // fixed reports fade into the background
                    .zIndex(if (r.isFixed) 0f else 1f)
            )
            if (m != null) { m.tag = r.id; markers[r.id] = m }
        }

        val n = records.size
        b.tvSubtitle.text = resources.getQuantityString(R.plurals.map_pins_count, n, n)
        b.tvEmpty.isVisible = n == 0

        if (!framed) {
            framed = true
            if (focusId >= 0 && markers.containsKey(focusId)) select(focusId, animate = false, zoom = 18f)
            else frameAll(gm)
        }
        showSelected()
    }

    private fun frameAll(gm: GoogleMap) {
        val points = records.map { LatLng(it.lat!!, it.lng!!) }
        when (points.size) {
            0 -> gm.moveCamera(CameraUpdateFactory.newLatLngZoom(MapDefaults.campus, MapDefaults.ZOOM))
            1 -> gm.moveCamera(CameraUpdateFactory.newLatLngZoom(points[0], MapDefaults.ZOOM))
            else -> {
                val bounds = LatLngBounds.Builder().apply { points.forEach(::include) }.build()
                val pad = (64 * resources.displayMetrics.density).toInt()
                // newLatLngBounds needs a laid-out map; wait for the first layout.
                b.mapView.post {
                    val g = map ?: return@post
                    try {
                        g.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad))
                        if (g.cameraPosition.zoom > 18f) g.moveCamera(CameraUpdateFactory.zoomTo(18f))
                    } catch (e: IllegalStateException) {
                        g.moveCamera(CameraUpdateFactory.newLatLngZoom(points[0], MapDefaults.ZOOM))
                    }
                }
            }
        }
    }

    private fun select(id: Int, animate: Boolean, zoom: Float? = null) {
        selectedId = id
        val m = markers[id]
        if (m != null) {
            val update = if (zoom != null) CameraUpdateFactory.newLatLngZoom(m.position, zoom)
            else CameraUpdateFactory.newLatLng(m.position)
            if (animate) map?.animateCamera(update) else map?.moveCamera(update)
        }
        showSelected()
    }

    private fun showSelected() {
        val r = records.firstOrNull { it.id == selectedId }
        b.selectedCard.isVisible = r != null
        if (r == null) { selectedId = -1; return }

        pill(b.tvSelTag, r.location.bgRes, r.location.textRes)
        b.tvSelTag.text = r.location.tag
        pill(b.tvSelPriority, r.priority.bgRes, r.priority.textRes)
        b.tvSelPriority.text = r.priority.label
        b.tvSelTitle.text = r.title
        b.tvSelMeta.text = getString(R.string.map_selected_meta, getString(statusLabel(r.stage)), r.upvotes, r.commentCount)
    }

    private fun statusLabel(s: Stage): Int = when (s) {
        Stage.REPORTED -> R.string.status_reported
        Stage.IN_PROGRESS -> R.string.status_in_progress
        Stage.FIXED -> R.string.status_fixed
    }

    /** Rounded badge; the model's bgRes/textRes are colour resources. */
    private fun pill(view: TextView, bgColorRes: Int, textColorRes: Int) {
        val ctx = requireContext()
        view.background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(ctx, bgColorRes))
            cornerRadius = 100f * resources.displayMetrics.density
        }
        view.setTextColor(ContextCompat.getColor(ctx, textColorRes))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED, selectedId)
        if (_b != null) mapLifecycle?.save(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        markers.clear(); map = null; mapLifecycle = null; _b = null
    }

    companion object {
        private const val ARG_WORKSPACE_ID = "workspace_id"
        private const val ARG_FOCUS_ID = "focus_id"
        private const val STATE_SELECTED = "selected"

        /** [focusReportId] >= 0 opens the map zoomed on that report. */
        fun newInstance(workspaceId: String? = null, focusReportId: Int = -1) = MapFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_WORKSPACE_ID, workspaceId)
                putInt(ARG_FOCUS_ID, focusReportId)
            }
        }
    }
}
