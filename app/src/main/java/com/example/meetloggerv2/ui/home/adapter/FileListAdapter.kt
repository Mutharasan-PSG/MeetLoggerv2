package com.example.meetloggerv2.ui.home.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.meetloggerv2.R
import java.util.Locale
import com.google.firebase.Timestamp

class FileListAdapter(
    private val context: Context,
    private val fileList: ArrayList<Triple<String, String, Timestamp>>
) : ArrayAdapter<Triple<String, String, Timestamp>>(context, R.layout.list_item_2, fileList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item_2, parent, false)

        val fileNameTextView = view.findViewById<TextView>(R.id.textViewFileName)
        val statusImageView = view.findViewById<android.widget.ImageView>(R.id.imageViewStatus)

        val (fileName, status, _) = fileList[position] // Ignore timestamp in display

        fileNameTextView.text = fileName.substringBeforeLast(".")  // Show file name without extension

        // Set status image resource and color filter dynamically based on file state
        when (status.lowercase(Locale.ROOT)) {
            "processed" -> {
                statusImageView.setImageResource(R.drawable.ic_status_processed)
                statusImageView.setColorFilter(ContextCompat.getColor(context, R.color.green))
            }
            "processing" -> {
                statusImageView.setImageResource(R.drawable.ic_status_processing)
                statusImageView.setColorFilter(ContextCompat.getColor(context, R.color.BLUE))
            }
            else -> {
                statusImageView.setImageResource(R.drawable.ic_status_saved)
                statusImageView.setColorFilter(ContextCompat.getColor(context, R.color.Grey))
            }
        }

        return view
    }
}