package com.example.patchboard.ui

import android.R.attr.inputType
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.patchboard.R
import com.example.patchboard.databinding.SheetAddReportBinding
import com.example.patchboard.map.MapPickerDialog
import com.example.patchboard.model.LocationTag
import com.example.patchboard.model.Priority
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import kotlin.math.max

/** "New Report" form: location, title, description, priority and an optional photo. */
class AddReportSheet : BottomSheetDialogFragment() {

    private var _b: SheetAddReportBinding? = null
    private val b get() = _b!!
    private lateinit var vm: BoardViewModel

    private var location: LocationTag = LocationTag.IT_LAB
    private var priority = Priority.MEDIUM
    private var imageFile: File? = null      // photo attached to this draft
    private var pendingCameraFile: File? = null
    private var pinLat: Double? = null           // optional map pin
    private var pinLng: Double? = null

    private val locationChips = mutableMapOf<LocationTag, Chip>()
    private val priorityButtons = mutableMapOf<Priority, MaterialButton>()

    // No CAMERA permission needed: the system camera app takes the picture for us.
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCameraFile
        if (ok && file != null) setImage(file) else file?.delete()
        pendingCameraFile = null
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) copyPickedImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(requireActivity())[BoardViewModel::class.java]
        savedInstanceState?.let { s ->
            s.getString(KEY_LOCATION)?.let { saved ->
                vm.locationTags.firstOrNull { it.tag == saved }?.let { location = it }
            }
            priority = Priority.values().getOrElse(s.getInt(KEY_PRIORITY)) { priority }
            s.getString(KEY_IMAGE)?.let { imageFile = File(it) }
            s.getString(KEY_PENDING)?.let { pendingCameraFile = File(it) }
            if (s.containsKey(KEY_PIN_LAT)) { pinLat = s.getDouble(KEY_PIN_LAT); pinLng = s.getDouble(KEY_PIN_LNG) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LOCATION, location.tag)
        outState.putInt(KEY_PRIORITY, priority.ordinal)
        imageFile?.let { outState.putString(KEY_IMAGE, it.absolutePath) }
        pendingCameraFile?.let { outState.putString(KEY_PENDING, it.absolutePath) }
        val la = pinLat; val ln = pinLng
        if (la != null && ln != null) { outState.putDouble(KEY_PIN_LAT, la); outState.putDouble(KEY_PIN_LNG, ln) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = SheetAddReportBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        BottomSheetBehavior.from(sheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val basePad = requireContext().dp(16)
        ViewCompat.setOnApplyWindowInsetsListener(b.sheetRoot) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            b.formBody.updatePadding(bottom = basePad + max(ime, nav))
            insets
        }

        buildLocationChips()
        buildPriorityButtons()

        b.btnClose.setOnClickListener { dismiss() }
        b.etTitle.doAfterTextChanged { updateSubmit() }
        b.btnCamera.setOnClickListener { launchCamera() }
        b.btnGallery.setOnClickListener { pickImage.launch("image/*") }
        b.btnRemovePhoto.setOnClickListener { removeImage() }
        b.btnSubmit.setOnClickListener { submit() }

        // Map pin (optional): the picker sends its result through the activity's FragmentManager.
        b.btnPin.setOnClickListener {
            MapPickerDialog.newInstance(pinLat, pinLng, priority.ordinal).show(parentFragmentManager, "pin")
        }
        b.btnRemovePin.setOnClickListener { pinLat = null; pinLng = null; renderPin() }
        parentFragmentManager.setFragmentResultListener(MapPickerDialog.RESULT_KEY, viewLifecycleOwner) { _, r ->
            pinLat = r.getDouble(MapPickerDialog.KEY_LAT)
            pinLng = r.getDouble(MapPickerDialog.KEY_LNG)
            renderPin()
        }

        renderLocation()
        renderPriority()
        renderPhoto()
        renderPin()
        updateSubmit()
    }

    // ── Location chips ───────────────────────────────────────────────────────
    private fun buildLocationChips() {
        val ctx = requireContext()
        b.locationGroup.removeAllViews()
        locationChips.clear()
        if (location !in vm.locationTags) location = vm.locationTags.first()

        vm.locationTags.forEach { loc ->
            val chip = Chip(ctx).apply {
                text = loc.tag
                isCheckable = false
                isCheckedIconVisible = false
                setEnsureMinTouchTargetSize(false)
                textSize = 11f
                setTextColor(ctx.color(loc.textRes))
                chipBackgroundColor = ColorStateList.valueOf(ctx.color(loc.bgRes))
                chipStrokeWidth = ctx.dp(2).toFloat()
                setOnClickListener { location = loc; renderLocation() }
                if (vm.isAdmin) setOnLongClickListener { confirmDeleteLocation(loc); true }   // admin: hold to delete
            }
            locationChips[loc] = chip
            b.locationGroup.addView(chip)
        }

        if (vm.isAdmin) {                                      // admin only: "+ Add location" chip
            b.locationGroup.addView(Chip(ctx).apply {
                text = getString(R.string.location_add_chip)
                isCheckable = false
                isCheckedIconVisible = false
                setEnsureMinTouchTargetSize(false)
                textSize = 11f
                setTextColor(ctx.color(R.color.pb_primary))
                chipBackgroundColor = ColorStateList.valueOf(Color.TRANSPARENT)
                chipStrokeColor = ColorStateList.valueOf(ctx.color(R.color.pb_primary))
                chipStrokeWidth = ctx.dp(1).toFloat()
                setOnClickListener { showAddLocationDialog() }
            })
        }
    }

    private fun showAddLocationDialog() {
        val ctx = requireContext()
        val pad = ctx.dp(20)
        val input = EditText(ctx).apply {
            hint = getString(R.string.location_add_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
        }
        val holder = FrameLayout(ctx).apply { setPadding(pad, pad / 2, pad, 0); addView(input) }
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.location_add_title)
            .setView(holder)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.location_add_confirm, null)   // real handler below keeps the dialog open on errors
            .create()
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            when (vm.addLocationTag(input.text.toString())) {
                AddTagResult.ADDED -> {
                    location = vm.locationTags.last()          // select the new tag right away
                    buildLocationChips(); renderLocation()
                    dialog.dismiss()
                }
                AddTagResult.DUPLICATE -> input.error = getString(R.string.location_err_duplicate)
                AddTagResult.INVALID -> input.error = getString(R.string.location_err_invalid)
            }
        }
    }

    private fun confirmDeleteLocation(loc: LocationTag) {
        val ctx = requireContext()
        val used = vm.locationUsage(loc)
        when {
            used > 0 -> Toast.makeText(ctx, getString(R.string.location_in_use, loc.tag, used), Toast.LENGTH_LONG).show()
            vm.locationTags.size <= 1 -> Toast.makeText(ctx, R.string.location_last, Toast.LENGTH_LONG).show()
            else -> MaterialAlertDialogBuilder(ctx)
                .setTitle(getString(R.string.location_delete_title, loc.tag))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    vm.removeLocationTag(loc); buildLocationChips(); renderLocation()
                }
                .show()
        }
    }

    private fun renderLocation() {
        val ctx = requireContext()
        locationChips.forEach { (loc, chip) ->
            chip.chipStrokeColor = ColorStateList.valueOf(
                if (loc == location) ctx.color(R.color.pb_primary) else Color.TRANSPARENT
            )
        }
    }

    // ── Priority ─────────────────────────────────────────────────────────────
    private fun buildPriorityButtons() {
        val ctx = requireContext()
        Priority.values().forEachIndexed { i, p ->
            val btn = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = p.label
                textSize = 12f
                isAllCaps = false
                cornerRadius = ctx.dp(8)
                strokeWidth = ctx.dp(2)
                insetTop = 0; insetBottom = 0
                setOnClickListener { priority = p; renderPriority() }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = ctx.dp(8)
            }
            priorityButtons[p] = btn
            b.priorityRow.addView(btn, lp)
        }
    }

    private fun renderPriority() {
        val ctx = requireContext()
        priorityButtons.forEach { (p, btn) ->
            val selected = p == priority
            btn.backgroundTintList = ColorStateList.valueOf(if (selected) ctx.color(p.bgRes) else Color.TRANSPARENT)
            btn.strokeColor = ColorStateList.valueOf(ctx.color(if (selected) p.dotRes else R.color.pb_outline))
            btn.setTextColor(ctx.color(if (selected) p.textRes else R.color.pb_on_surface_variant))
        }
    }

    // ── Photo ────────────────────────────────────────────────────────────────
    private fun reportsDir(): File = File(requireContext().filesDir, "reports").apply { mkdirs() }

    private fun newPhotoFile() = File(reportsDir(), "IMG_${System.currentTimeMillis()}.jpg")

    private fun launchCamera() {
        val file = newPhotoFile()
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        takePicture.launch(uri)
    }

    /** Copies a gallery pick into app storage so access doesn't depend on a temporary permission grant. */
    private fun copyPickedImage(uri: Uri) {
        val ctx = requireContext().applicationContext
        val target = newPhotoFile()
        Thread {
            val ok = try {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                    true
                } ?: false
            } catch (e: Exception) { false }
            view?.post { if (_b != null && ok) setImage(target) else target.delete() }
        }.start()
    }

    private fun setImage(file: File) {
        imageFile?.takeIf { it != file }?.delete()
        imageFile = file
        renderPhoto()
    }

    private fun removeImage() {
        imageFile?.delete()
        imageFile = null
        renderPhoto()
    }

    private fun renderPhoto() {
        val file = imageFile
        if (file != null && file.exists()) {
            b.photoPreviewBox.visibility = View.VISIBLE
            b.photoButtons.visibility = View.GONE
            Glide.with(this).load(Uri.fromFile(file)).centerCrop().into(b.imgPreview)
        } else {
            imageFile = null
            b.photoPreviewBox.visibility = View.GONE
            b.photoButtons.visibility = View.VISIBLE
        }
    }

    // ── Map pin ──────────────────────────────────────────────────────────────
    private fun renderPin() {
        val la = pinLat; val ln = pinLng
        b.btnPin.text = if (la != null && ln != null) getString(R.string.map_pin_set, la, ln)
        else getString(R.string.map_add_pin)
        b.btnRemovePin.visibility = if (la != null) View.VISIBLE else View.GONE
    }

    // ── Submit ───────────────────────────────────────────────────────────────
    private fun updateSubmit() {
        b.btnSubmit.isEnabled = b.etTitle.text.toString().isNotBlank()
    }

    private fun submit() {
        val title = b.etTitle.text.toString().trim()
        if (title.isEmpty()) return
        vm.addReport(
            location = location,
            title = title,
            description = b.etDescription.text.toString(),
            priority = priority,
            imageUri = imageFile?.let { Uri.fromFile(it).toString() },
            lat = pinLat,
            lng = pinLng,
        )
        imageFile = null // ownership moved to the new report; don't delete on dismiss
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Discard an unsent photo when the sheet is really closing (not on rotation).
        if (isRemoving || requireActivity().isFinishing) imageFile?.delete()
    }

    private companion object {
        const val KEY_LOCATION = "location"
        const val KEY_PRIORITY = "priority"
        const val KEY_IMAGE = "image"
        const val KEY_PENDING = "pending"
        const val KEY_PIN_LAT = "pin_lat"
        const val KEY_PIN_LNG = "pin_lng"
    }
}
