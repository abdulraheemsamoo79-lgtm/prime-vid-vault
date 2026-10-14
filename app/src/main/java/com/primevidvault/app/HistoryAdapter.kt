package com.primevidvault.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var items: List<HistoryItem>,
    private val onRedownload: (HistoryItem) -> Unit,
    private val onDelete: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvItemTitle)
        val tvMeta: TextView = v.findViewById(R.id.tvItemMeta)
        val btnRe: Button = v.findViewById(R.id.btnItemRedownload)
        val btnDel: Button = v.findViewById(R.id.btnItemDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.tvTitle.text =
            if (item.title.length > 60) item.title.substring(0, 60) + "..." else item.title
        val date = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            .format(Date(item.date))
        holder.tvMeta.text = item.type + " • " + date
        holder.btnRe.setOnClickListener { onRedownload(item) }
        holder.btnDel.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
