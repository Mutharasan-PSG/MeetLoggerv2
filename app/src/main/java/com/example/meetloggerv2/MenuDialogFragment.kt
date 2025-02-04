package com.example.meetloggerv2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment


class MenuDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_menu_dialog, container, false)

        // Set onClickListeners for the buttons
        val joinMeetButton = view.findViewById<Button>(R.id.btn_join_meet)
        val createMeetButton = view.findViewById<Button>(R.id.btn_create_meet)
        val scheduleMeetButton = view.findViewById<Button>(R.id.btn_schedule_meet)

        joinMeetButton.setOnClickListener {
            // Handle Join Meet button click
            dismiss() // Dismiss the BottomSheet after action
        }

        createMeetButton.setOnClickListener {
            // Handle Create New Meet button click
            dismiss() // Dismiss the BottomSheet after action
        }

        scheduleMeetButton.setOnClickListener {
            // Handle Schedule Meet button click
            dismiss() // Dismiss the BottomSheet after action
        }

        return view
    }
}
