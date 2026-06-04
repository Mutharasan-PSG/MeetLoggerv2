package com.example.meetloggerv2.ui.audio.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.example.meetloggerv2.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AudioProcessingDialogHelper(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val userFilesLiveData: LiveData<List<String>>,
    private val fetchUserFiles: () -> Unit,
    private val onProcessingConfirmed: (speakers: List<String>, followUp: String) -> Unit
) {
    private var temporarySpeakerList: List<String> = emptyList()

    fun show() {
        showSpeakerSelectionDialog()
    }

    private fun showSpeakerSelectionDialog() {
        val v = LayoutInflater.from(context).inflate(R.layout.dialog_speaker_selection, null)
        val d = MaterialAlertDialogBuilder(context).setView(v).setCancelable(false).create()
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setDimAmount(0.8f)
        
        v.findViewById<Button>(R.id.proceedButton).setOnClickListener {
            val checkedId = v.findViewById<RadioGroup>(R.id.radioGroup).checkedRadioButtonId
            if (checkedId == -1) {
                Toast.makeText(context, R.string.error_selection_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (checkedId == R.id.radioYes) {
                d.dismiss()
                showSpeakerInputDialog()
            } else {
                d.dismiss()
                showFollowUpSelectionDialog(emptyList())
            }
        }
        v.findViewById<Button>(R.id.cancelButton).setOnClickListener { d.dismiss() }
        d.show()
    }

    private fun showSpeakerInputDialog() {
        val v = LayoutInflater.from(context).inflate(R.layout.dialog_speaker_input, null)
        val container = v.findViewById<LinearLayout>(R.id.speakerContainer)
        val addSpeakerBtn = v.findViewById<Button>(R.id.addSpeakerButton)
        val proceedBtn = v.findViewById<Button>(R.id.proceedButton)
        val speakerList = mutableListOf<String>()

        val updateButtons = {
            val allFilled = speakerList.all { it.isNotBlank() } && speakerList.isNotEmpty()
            proceedBtn.isEnabled = allFilled
            addSpeakerBtn.isEnabled = allFilled && speakerList.size < 10
        }

        val addInput = {
            val item = LayoutInflater.from(context).inflate(R.layout.item_speaker_input, container, false)
            val idx = speakerList.size
            speakerList.add("")
            val input = item.findViewById<EditText>(R.id.speakerNameInput)
            item.findViewById<TextView>(R.id.speakerLabel).text = "Speaker ${('A' + idx)}"
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    speakerList[idx] = s.toString().trim()
                    updateButtons()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            container.addView(item)
            input.post {
                input.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
            updateButtons()
        }

        addInput()
        val d = MaterialAlertDialogBuilder(context).setView(v).setCancelable(false).create()
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setDimAmount(0.8f)
        addSpeakerBtn.setOnClickListener { addInput() }
        proceedBtn.setOnClickListener {
            val filtered = speakerList.filter { it.isNotBlank() }
            temporarySpeakerList = filtered
            d.dismiss()
            showFollowUpSelectionDialog(filtered)
        }
        v.findViewById<ImageView>(R.id.backButton).setOnClickListener {
            d.dismiss()
            showSpeakerSelectionDialog()
        }
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
    }

    private fun showFollowUpSelectionDialog(speakers: List<String>) {
        val v = LayoutInflater.from(context).inflate(R.layout.dialog_follow_up_selection, null)
        val spinner = v.findViewById<Spinner>(R.id.spinnerFiles)
        val proceed = v.findViewById<Button>(R.id.proceedButton)
        v.findViewById<RadioGroup>(R.id.radioGroup).setOnCheckedChangeListener { _, id ->
            spinner.visibility = if (id == R.id.radioYes) View.VISIBLE else View.GONE
            if (id == R.id.radioYes) {
                proceed.isEnabled = false
                fetchUserFiles()
            } else proceed.isEnabled = true
        }
        userFilesLiveData.observe(lifecycleOwner) { files ->
            val adapter = ArrayAdapter(
                context,
                R.layout.spinner_selected_item,
                files.map { it.substringBeforeLast(".") }
            )
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinner.adapter = adapter
            spinner.setTag(files)
            proceed.isEnabled = true
        }
        val d = MaterialAlertDialogBuilder(context).setView(v).setCancelable(false).create()
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setDimAmount(0.8f)
        proceed.setOnClickListener {
            val checkedId = v.findViewById<RadioGroup>(R.id.radioGroup).checkedRadioButtonId
            if (checkedId == -1) {
                Toast.makeText(context, R.string.error_selection_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val followUp = if (checkedId == R.id.radioYes) {
                @Suppress("UNCHECKED_CAST")
                (spinner.tag as? List<String>)?.getOrNull(spinner.selectedItemPosition) ?: ""
            } else ""
            d.dismiss()
            onProcessingConfirmed(speakers, followUp)
        }
        v.findViewById<Button>(R.id.cancelButton).setOnClickListener { d.dismiss() }
        v.findViewById<ImageView>(R.id.backButton).setOnClickListener {
            d.dismiss()
            showSpeakerSelectionDialog()
        }
        d.show()
    }
}
