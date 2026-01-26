package com.example.testd.tasks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.testd.R
import java.util.Locale

class TaskAdapter(
    private val items: MutableList<TaskItem>
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    fun updateData(newItems: List<TaskItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvStatus.text = "状态：${item.status}"
        holder.tvReward.text = String.format(Locale.getDefault(), "酬劳：%.2f", item.reward)
        holder.tvDescription.text = item.description
        holder.tvLocations.text = "起点：${item.startLocation}  终点：${item.targetLocation}"
    }

    override fun getItemCount(): Int = items.size

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTaskTitle)
        val tvStatus: TextView = view.findViewById(R.id.tvTaskStatus)
        val tvReward: TextView = view.findViewById(R.id.tvTaskReward)
        val tvDescription: TextView = view.findViewById(R.id.tvTaskDescription)
        val tvLocations: TextView = view.findViewById(R.id.tvTaskLocations)
    }
}
