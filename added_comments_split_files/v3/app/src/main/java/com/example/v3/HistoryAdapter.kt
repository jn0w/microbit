package com.example.v3

// This file handles displaying the list of past reaction times on the History screen.
// An Adapter connects your data to a scrollable list so users can see their history.

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


// This adapter takes a list of ReactionTimeEntry objects and displays them in a scrollable list.
// Each item in the list shows the reaction time and when it was recorded.
class HistoryAdapter(private val items: List<ReactionTimeEntry>) : 
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    // This formats timestamps into readable dates like "Dec 13, 2025 2:30 PM"
    private val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    // ViewHolder holds references to the text views in each list item.
    // This makes scrolling faster because we dont have to find the views every time.
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val reactionTimeText: TextView = view.findViewById(R.id.reaction_time_text)
        val timestampText: TextView = view.findViewById(R.id.timestamp_text)
    }

    // Called when we need to create a new list item view.
    // This inflates the XML layout and wraps it in a ViewHolder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    // Called when we need to fill a list item with data.
    // We get the data for this position and put it in the text views.
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.reactionTimeText.text = "${item.reactionTimeMs} ms"
        holder.timestampText.text = dateFormat.format(Date(item.timestamp))
    }

    // Returns how many items are in the list.
    override fun getItemCount() = items.size
}
