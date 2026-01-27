package com.example.testd.tasks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.testd.R

class PlaceAdapter(
    private val onItemClick: (PlaceItem) -> Unit
) : RecyclerView.Adapter<PlaceAdapter.PlaceViewHolder>() {

    private val items = mutableListOf<PlaceItem>()

    fun submitList(newItems: List<PlaceItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place, parent, false)
        return PlaceViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class PlaceViewHolder(
        itemView: View,
        private val onItemClick: (PlaceItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvPlaceName)
        private val tvAddress: TextView = itemView.findViewById(R.id.tvPlaceAddress)
        private var currentItem: PlaceItem? = null

        init {
            itemView.setOnClickListener {
                currentItem?.let(onItemClick)
            }
        }

        fun bind(item: PlaceItem) {
            currentItem = item
            tvName.text = item.name
            tvAddress.text = item.address
        }
    }
}
