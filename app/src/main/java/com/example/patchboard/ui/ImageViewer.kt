package com.example.patchboard.ui

import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import com.bumptech.glide.Glide
import com.example.patchboard.databinding.DialogImageBinding

/** Full-screen photo viewer (the web version's "lightbox"). Tap anywhere to close. */
object ImageViewer {
    fun show(context: Context, uri: String) {
        val b = DialogImageBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(b.root)
        Glide.with(context).load(Uri.parse(uri)).into(b.imgFull)
        b.root.setOnClickListener { dialog.dismiss() }
        b.btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
