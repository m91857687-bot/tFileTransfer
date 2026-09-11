package com.tans.tfiletransporter.ui.instructions

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.tans.tfiletransporter.R

class InstructionsDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.instructions_title)
            .setMessage(R.string.instructions_desc)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }
}
