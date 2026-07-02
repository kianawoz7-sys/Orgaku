package com.tokoku.orgaku

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tokoku.orgaku.data.model.Task
import com.tokoku.orgaku.databinding.ItemTaskCardBinding

class TaskCardAdapter(
    private var items: List<Task>,
    private var userRole: String,
    private val onItemClick: (Task) -> Unit,
    private val onMaju: (Task) -> Unit,
    private val onMundur: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : RecyclerView.Adapter<TaskCardAdapter.ViewHolder>() {

    fun updateData(newItems: List<Task>, role: String? = null) {
        this.items = newItems
        if (role != null) this.userRole = role
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemTaskCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemTaskCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvTaskTitle.text = item.title
            tvDeadline.text = item.deadline
            tvPriority.text = item.priority.uppercase()
            tvTaskSubtitle.text = item.description

            // Logic warna priority
            when (item.priority.uppercase()) {
                "TINGGI", "HIGH" -> {
                    cardPriority.setCardBackgroundColor(root.context.getColor(R.color.priority_high_bg))
                    tvPriority.setTextColor(root.context.getColor(R.color.priority_high_text))
                    viewIndicator.setBackgroundColor(root.context.getColor(R.color.priority_high_text))
                }

                "SEDANG", "MEDIUM" -> {
                    cardPriority.setCardBackgroundColor(root.context.getColor(R.color.priority_medium_bg))
                    tvPriority.setTextColor(root.context.getColor(R.color.priority_medium_text))
                    viewIndicator.setBackgroundColor(root.context.getColor(R.color.priority_medium_text))
                }

                else -> {
                    cardPriority.setCardBackgroundColor(root.context.getColor(R.color.priority_low_bg))
                    tvPriority.setTextColor(root.context.getColor(R.color.priority_low_text))
                    viewIndicator.setBackgroundColor(root.context.getColor(R.color.priority_low_text))
                }
            }

            // Kanban Visibility Logic
            when (item.status.lowercase()) {
                "todo" -> {
                    btnMundur.visibility = View.GONE
                    btnMaju.visibility = View.VISIBLE
                }

                "in_progress" -> {
                    btnMundur.visibility = View.VISIBLE
                    btnMaju.visibility = View.VISIBLE
                }

                "done" -> {
                    btnMundur.visibility = View.VISIBLE
                    btnMaju.visibility = View.GONE
                }
            }

            // Delete Visibility Logic (Ketua only)
            if (userRole.equals("ketua", ignoreCase = true) || userRole.equals(
                    "admin",
                    ignoreCase = true
                )
            ) {
                btnDelete.visibility = View.VISIBLE
            } else {
                btnDelete.visibility = View.GONE
            }

            // Click Listeners
            root.setOnClickListener { onItemClick(item) }
            btnMaju.setOnClickListener { onMaju(item) }
            btnMundur.setOnClickListener { onMundur(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun getItemCount() = items.size
}
