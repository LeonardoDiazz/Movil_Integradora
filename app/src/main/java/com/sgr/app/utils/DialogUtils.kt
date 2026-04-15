package com.sgr.app.utils

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.sgr.app.R

fun showErrorDialog(
    context: Context,
    title: String = "No disponible",
    message: String
) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_error, null)
    view.findViewById<TextView>(R.id.tvErrorTitle).text = title
    view.findViewById<TextView>(R.id.tvErrorMessage).text = message

    val dialog = AlertDialog.Builder(context)
        .setView(view)
        .create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    view.findViewById<MaterialButton>(R.id.btnErrorClose).setOnClickListener { dialog.dismiss() }

    dialog.show()
}

fun showConfirmDialog(
    context: Context,
    title: String,
    message: String,
    positiveLabel: String = "Sí, guardar",
    onConfirm: () -> Unit
) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_save, null)
    view.findViewById<TextView>(R.id.tvConfirmTitle).text = title
    view.findViewById<TextView>(R.id.tvConfirmMessage).text = message
    view.findViewById<MaterialButton>(R.id.btnConfirmPositive).text = positiveLabel

    val dialog = AlertDialog.Builder(context)
        .setView(view)
        .create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    view.findViewById<MaterialButton>(R.id.btnConfirmNegative).setOnClickListener { dialog.dismiss() }
    view.findViewById<MaterialButton>(R.id.btnConfirmPositive).setOnClickListener {
        dialog.dismiss()
        onConfirm()
    }

    dialog.show()
}
