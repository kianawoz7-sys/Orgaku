package com.tokoku.orgaku

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tokoku.orgaku.data.model.TaskItem
import com.tokoku.orgaku.databinding.ItemTaskBinding

class TaskAdapter(
    private val items: List<TaskItem>,
    private val onItemClick: (TaskItem) -> Unit
) : RecyclerView.Adapter<TaskAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvTaskTitle.text = item.title
            tvDeadline.text = "Deadline: ${item.deadline}"
            tvPriority.text = item.priority

            // Frontend Mapping for 3-State UI (using String from Firebase)
            when (item.status.lowercase()) {
                "in_progress" -> {
                    ivStatusIndicator.setImageResource(R.drawable.ic_status_progress)
                    tvTaskTitle.paintFlags =
                        tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    tvTaskTitle.alpha = 1.0f
                    metaLayout.alpha = 1.0f
                }

                "done", "completed" -> {
                    ivStatusIndicator.setImageResource(R.drawable.ic_status_done)
                    tvTaskTitle.paintFlags = tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    tvTaskTitle.alpha = 0.7f
                    metaLayout.alpha = 0.7f
                }

                else -> { // "todo"
                    ivStatusIndicator.setImageResource(R.drawable.ic_status_todo)
                    tvTaskTitle.paintFlags =
                        tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    tvTaskTitle.alpha = 1.0f
                    metaLayout.alpha = 1.0f
                }
            }

            // Logic warna priority
            when (item.priority.uppercase()) {
                "TINGGI", "HIGH" -> {
                    tvPriority.setBackgroundResource(R.drawable.bg_badge_priority_high)
                    tvPriority.setTextColor(root.context.getColor(R.color.priority_high_text))
                }

                "SEDANG", "MEDIUM" -> {
                    tvPriority.setBackgroundResource(R.drawable.bg_badge_priority_medium)
                    tvPriority.setTextColor(root.context.getColor(R.color.priority_medium_text))
                }

                else -> {
                    tvPriority.setBackgroundResource(R.drawable.bg_badge_priority_low)
                    tvPriority.setTextColor(root.context.getColor(R.color.priority_low_text))
                }
            }

            root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun getItemCount() = items.size
}
