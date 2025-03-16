package com.example.meetloggerv2

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale
import com.google.firebase.Timestamp

class FileListAdapter(
    private val context: Context,
    private val fileList: ArrayList<Triple<String, String, Timestamp>>
) : ArrayAdapter<Triple<String, String, Timestamp>>(context, R.layout.list_item_2, fileList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item_2, parent, false)

        val fileNameTextView = view.findViewById<TextView>(R.id.textViewFileName)
        val statusTextView = view.findViewById<TextView>(R.id.textViewStatus)

        val (fileName, status, _) = fileList[position] // Ignore timestamp in display

        fileNameTextView.text = fileName.substringBeforeLast(".")  // Show file name without extension
        statusTextView.text = status.capitalize(Locale.ROOT)  // Capitalize the status text

        // Change the color of the status text based on its value
        if (status.equals("processed", ignoreCase = true)) {
            statusTextView.setTextColor(ContextCompat.getColor(context, R.color.green)) // Green color
        } else {
            statusTextView.setTextColor(ContextCompat.getColor(context, R.color.red)) // Red color
        }

        return view
    }
}