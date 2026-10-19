package com.example.patchboard

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.patchboard.analytics.AnalyticsFragment
import com.example.patchboard.auth.AccountUser
import com.example.patchboard.auth.AuthFragment
import com.example.patchboard.auth.AuthProvider
import com.example.patchboard.databinding.ActivityMainBinding
import com.example.patchboard.map.MapFragment
import com.example.patchboard.model.LocationTag
import com.example.patchboard.model.Priority
import com.example.patchboard.model.SortMode
import com.example.patchboard.profile.ProfileFragment
import com.example.patchboard.ui.AddReportSheet
import com.example.patchboard.ui.BoardViewModel
import com.example.patchboard.ui.CardActions
import com.example.patchboard.ui.ColumnAdapter
import com.example.patchboard.ui.CommentsSheet
import com.example.patchboard.ui.ImageViewer
import com.example.patchboard.ui.color
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity(), CardActions {

    private lateinit var binding: ActivityMainBinding
    private lateinit var vm: BoardViewModel
    private val columnAdapter = ColumnAdapter(this)
    private var lastSearchOpen = false
    private var lastFocusToken = 0
    private val isNight: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private val closeSearchOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = vm.setSearchOpen(false)
    }

    private var pendingMapFocus = -1   // report to zoom to when the Map tab opens

    private val closePageOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showPage(null)
            binding.bottomNav.selectedItemId = R.id.nav_board
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[BoardViewModel::class.java]

        // Edge-to-edge: the purple top bar draws under the status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.topSection.updatePadding(top = bars.top)
            binding.bottomNav.updatePadding(bottom = bars.bottom)
            insets
        }
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = false              // top bar is dark in both themes
            isAppearanceLightNavigationBars = !isNight
        }

        onBackPressedDispatcher.addCallback(this, closeSearchOnBack)
        onBackPressedDispatcher.addCallback(this, closePageOnBack) // added last = checked first

        setupBoard()
        setupTopBar()
        setupFilters()
        setupBottomNav()
        binding.fab.setOnClickListener { AddReportSheet().show(supportFragmentManager, "add") }

        vm.observe(this) { render() }

        // Accounts: nobody gets past the sign-in screen until they have signed in or signed up.
        showAuth(AuthProvider.get().currentUser == null)        // no flash of the board for signed-out users
        AuthProvider.get().addListener(this) { onAccountChanged(it) }
    }

    // ── Accounts ─────────────────────────────────────────────────────────────
    private fun onAccountChanged(user: AccountUser?) {
        if (user == null) {
            vm.clearAccount()                                    // forget the poster and drop admin mode
            showPage(null)
            binding.bottomNav.selectedItemId = R.id.nav_board
            showAuth(true)
        } else {
            vm.setAccount(user.id, user.displayName)             // reports / comments are stamped with the account; renames propagate
            showAuth(false)
        }
    }

    /** Full-screen sign-in layer above everything (including the bottom nav). */
    private fun showAuth(show: Boolean) {
        val fm = supportFragmentManager
        val current = fm.findFragmentById(R.id.authContainer)
        if (show && current == null) {
            fm.beginTransaction().replace(R.id.authContainer, AuthFragment(), "auth").commit()
        } else if (!show && current != null) {
            fm.beginTransaction().remove(current).commit()
        }
        binding.authContainer.isVisible = show
        if (!show) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }

    // ── Setup ────────────────────────────────────────────────────────────────
    private fun setupBoard() = with(binding.boardRecycler) {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        adapter = columnAdapter
        itemAnimator = null // keep column holders (and their scroll positions) stable
    }

    private fun setupTopBar() {
        binding.btnTheme.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(
                if (isNight) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            )
        }
        binding.btnSearch.setOnClickListener { vm.setSearchOpen(!vm.searchOpen) }
        binding.btnClearSearch.setOnClickListener {
            vm.setSearchQuery("")
            binding.etSearch.requestFocus()
        }
        binding.etSearch.doAfterTextChanged { vm.setSearchQuery(it?.toString().orEmpty()) }
    }

    private fun setupFilters() {
        mapOf(
            binding.chipAll to null,
            binding.chipHigh to Priority.HIGH,
            binding.chipMedium to Priority.MEDIUM,
            binding.chipLow to Priority.LOW,
        ).forEach { (chip, priority) -> chip.setOnClickListener { vm.setPriorityFilter(priority) } }
        binding.btnSort.setOnClickListener { vm.cycleSort() }
        binding.btnLocation.setOnClickListener { showLocationPicker() }
    }

    /** Single-choice dialog built in code, so no new layout is needed. */
    private fun showLocationPicker() {
        val locations = vm.locationTags
        val items = arrayOf(getString(R.string.filter_location_all)) + locations.map { it.tag }
        val checked = vm.locationFilter?.let { locations.indexOf(it) + 1 } ?: 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.filter_location_title)
            .setSingleChoiceItems(items, checked) { dialog, which ->
                vm.setLocationFilter(if (which == 0) null else locations[which - 1])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_board -> { showPage(null); true }
                R.id.nav_reports -> { showPage(PAGE_REPORTS); true }
                R.id.nav_map -> { showPage(PAGE_MAP); true }
                R.id.nav_profile -> { showPage(PAGE_PROFILE); true }
                else -> {
                    // Unknown tab: placeholder.
                    Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }
    }

    // ── Render (called on every state change) ────────────────────────────────
    private fun render() {
        columnAdapter.submit(vm.visibleColumns(), vm.isAdmin, vm.viewKey, vm.focusToken, vm.focusColumn)
        if (vm.focusToken != lastFocusToken) {
            lastFocusToken = vm.focusToken
            binding.boardRecycler.smoothScrollToPosition(vm.focusColumn)
        }
        // Theme icon
        binding.btnTheme.setImageResource(if (isNight) R.drawable.ic_light_mode else R.drawable.ic_dark_mode)

        // Admin button
        binding.btnAdminSignIn.visibility = View.GONE
        binding.btnAdminActive.visibility = if (vm.isAdmin) View.VISIBLE else View.GONE

        // Search row
        binding.searchRow.visibility = if (vm.searchOpen) View.VISIBLE else View.GONE
        closeSearchOnBack.isEnabled = vm.searchOpen
        if (binding.etSearch.text.toString() != vm.searchQuery) {
            binding.etSearch.setText(vm.searchQuery)
            binding.etSearch.setSelection(binding.etSearch.text.length)
        }
        binding.btnClearSearch.visibility =
            if (vm.searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
        val searching = vm.searchQuery.isNotBlank()
        binding.tvResultCount.visibility = if (searching) View.VISIBLE else View.GONE
        if (searching) {
            val n = vm.searchResultCount()
            binding.tvResultCount.text = "$n ${if (n == 1) "result" else "results"}"
        }
        if (vm.searchOpen != lastSearchOpen) {
            lastSearchOpen = vm.searchOpen
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            if (vm.searchOpen) {
                binding.etSearch.requestFocus()
                imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
            } else {
                imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
            }
        }

        // Priority filter chips
        binding.chipAll.isChecked = vm.priorityFilter == null
        binding.chipHigh.isChecked = vm.priorityFilter == Priority.HIGH
        binding.chipMedium.isChecked = vm.priorityFilter == Priority.MEDIUM
        binding.chipLow.isChecked = vm.priorityFilter == Priority.LOW

        // Location button: highlighted while a location is selected
        val locFiltered = vm.locationFilter != null
        binding.btnLocation.text = vm.locationFilter?.tag ?: getString(R.string.filter_location_all)
        binding.btnLocation.backgroundTintList =
            ColorStateList.valueOf(if (locFiltered) color(R.color.pb_primary) else android.graphics.Color.TRANSPARENT)
        binding.btnLocation.strokeColor =
            ColorStateList.valueOf(color(if (locFiltered) R.color.pb_primary else R.color.pb_outline))
        binding.btnLocation.setTextColor(color(if (locFiltered) R.color.pb_on_primary else R.color.pb_on_surface_variant))

        // Sort button: ⇅ (off) → ↑ most upvotes → ↓ fewest upvotes
        val sorted = vm.sortMode != SortMode.OFF
        binding.btnSort.text = when (vm.sortMode) {
            SortMode.OFF -> "⇅ Sort"
            SortMode.DESC -> "↑ Sort"
            SortMode.ASC -> "↓ Sort"
        }
        binding.btnSort.backgroundTintList =
            ColorStateList.valueOf(if (sorted) color(R.color.pb_primary) else android.graphics.Color.TRANSPARENT)
        binding.btnSort.strokeColor =
            ColorStateList.valueOf(color(if (sorted) R.color.pb_primary else R.color.pb_outline))
        binding.btnSort.setTextColor(color(if (sorted) R.color.pb_on_primary else R.color.pb_on_surface_variant))
        vm.pendingError?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            vm.consumeError()
        }
    }

    /**
     * Shows a full-screen page (Reports, Map) over the board, or the board itself when [tag] is null.
     * Leaving a page removes its fragment, so the map is not kept alive in the background.
     */
    private fun showPage(tag: String?) {
        val fm = supportFragmentManager
        val current = fm.findFragmentById(R.id.pageContainer)
        if (tag == null) {
            if (current != null) fm.beginTransaction().remove(current).commit()
        } else if (current?.tag != tag) {
            val page = when (tag) {
                PAGE_MAP -> MapFragment.newInstance(focusReportId = pendingMapFocus).also { pendingMapFocus = -1 }
                PAGE_PROFILE -> ProfileFragment()
                else -> AnalyticsFragment.newInstance()
            }
            fm.beginTransaction().replace(R.id.pageContainer, page, tag).commit()
        }
        binding.pageContainer.isVisible = tag != null
        closePageOnBack.isEnabled = tag != null
        if (tag != null && vm.searchOpen) vm.setSearchOpen(false)
    }

    /** Opens the Map tab zoomed on one report (call this from a report card). */
    fun focusOnMap(reportId: Int) {
        pendingMapFocus = reportId
        binding.bottomNav.selectedItemId = R.id.nav_map
    }

    // The nav bar restores its selected tab after onCreate; make the page match it.
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        showPage(
            when (binding.bottomNav.selectedItemId) {
                R.id.nav_reports -> PAGE_REPORTS
                R.id.nav_map -> PAGE_MAP
                R.id.nav_profile -> PAGE_PROFILE
                else -> null
            }
        )
    }

    // ── CardActions ──────────────────────────────────────────────────────────
    override fun onUpvote(cardId: Int) = vm.upvote(cardId)

    override fun onOpenComments(cardId: Int) =
        CommentsSheet.newInstance(cardId).show(supportFragmentManager, "comments")

    override fun onMove(cardId: Int, forward: Boolean) = vm.moveCard(cardId, forward)

    override fun onOpenImage(uri: String) = ImageViewer.show(this, uri)

    private companion object {
        const val PAGE_REPORTS = "analytics"
        const val PAGE_MAP = "map"
        const val PAGE_PROFILE = "profile"
    }
}
