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
import com.example.patchboard.databinding.ActivityMainBinding
import com.example.patchboard.model.Priority
import com.example.patchboard.model.SortMode
import com.example.patchboard.ui.AddReportSheet
import com.example.patchboard.ui.AdminLoginSheet
import com.example.patchboard.ui.BoardViewModel
import com.example.patchboard.ui.CardActions
import com.example.patchboard.ui.ColumnAdapter
import com.example.patchboard.ui.CommentsSheet
import com.example.patchboard.ui.ImageViewer
import com.example.patchboard.ui.color

class MainActivity : AppCompatActivity(), CardActions {

    private lateinit var binding: ActivityMainBinding
    private lateinit var vm: BoardViewModel
    private val columnAdapter = ColumnAdapter(this)
    private var lastSearchOpen = false

    private val isNight: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private val closeSearchOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = vm.setSearchOpen(false)
    }

    private val closeReportsOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showReportsPage(false)
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
        onBackPressedDispatcher.addCallback(this, closeReportsOnBack) // added last = checked first

        setupBoard()
        setupTopBar()
        setupFilters()
        setupBottomNav()
        binding.fab.setOnClickListener { AddReportSheet().show(supportFragmentManager, "add") }

        vm.observe(this) { render() }
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
        binding.btnAdminSignIn.setOnClickListener {
            AdminLoginSheet().show(supportFragmentManager, "admin")
        }
        binding.btnAdminActive.setOnClickListener { vm.signOut() } // tap "Admin" to sign out
    }

    private fun setupFilters() {
        mapOf(
            binding.chipAll to null,
            binding.chipHigh to Priority.HIGH,
            binding.chipMedium to Priority.MEDIUM,
            binding.chipLow to Priority.LOW,
        ).forEach { (chip, priority) -> chip.setOnClickListener { vm.setPriorityFilter(priority) } }
        binding.btnSort.setOnClickListener { vm.cycleSort() }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_board -> { showReportsPage(false); true }
                R.id.nav_reports -> { showReportsPage(true); true }
                else -> {
                    // Map / Profile are placeholders in the prototype too.
                    Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }
    }

    // ── Render (called on every state change) ────────────────────────────────
    private fun render() {
        columnAdapter.submit(vm.visibleColumns(), vm.isAdmin)

        // Theme icon
        binding.btnTheme.setImageResource(if (isNight) R.drawable.ic_light_mode else R.drawable.ic_dark_mode)

        // Admin button
        binding.btnAdminSignIn.visibility = if (vm.isAdmin) View.GONE else View.VISIBLE
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
    }

    private fun showReportsPage(show: Boolean) {
        if (show && supportFragmentManager.findFragmentByTag("analytics") == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.pageContainer, AnalyticsFragment.newInstance(), "analytics")
                .commit()
        }
        binding.pageContainer.isVisible = show
        closeReportsOnBack.isEnabled = show
        if (show && vm.searchOpen) vm.setSearchOpen(false)
    }

    // The nav bar restores its selected tab after onCreate; make the page match it.
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        showReportsPage(binding.bottomNav.selectedItemId == R.id.nav_reports)
    }

    // ── CardActions ──────────────────────────────────────────────────────────
    override fun onUpvote(cardId: Int) = vm.upvote(cardId)

    override fun onOpenComments(cardId: Int) =
        CommentsSheet.newInstance(cardId).show(supportFragmentManager, "comments")

    override fun onMove(cardId: Int, forward: Boolean) = vm.moveCard(cardId, forward)

    override fun onOpenImage(uri: String) = ImageViewer.show(this, uri)
}