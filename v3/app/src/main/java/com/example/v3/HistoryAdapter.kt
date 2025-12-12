package com.example.v3

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReactionTimeEntry(
    val reactionTimeMs: Long,
    val timestamp: Long
)

class HistoryAdapter(private val items: List<ReactionTimeEntry>) : 
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val reactionTimeText: TextView = view.findViewById(R.id.reaction_time_text)
        val timestampText: TextView = view.findViewById(R.id.timestamp_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.reactionTimeText.text = "${item.reactionTimeMs} ms"
        holder.timestampText.text = dateFormat.format(Date(item.timestamp))
    }

    override fun getItemCount() = items.size
}

