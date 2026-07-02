package com.tokoku.orgaku

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tokoku.orgaku.data.model.SubTask
import com.tokoku.orgaku.databinding.ItemSubTaskBinding

class SubTaskAdapter(
    private val items: List<SubTask>,
    private val onSubTaskChecked: (SubTask, Boolean) -> Unit
) : RecyclerView.Adapter<SubTaskAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemSubTaskBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.cbSubTask.apply {
            text = item.title
            setOnCheckedChangeListener(null) // Prevent recursive call
            isChecked = item.isCompleted
            
            setOnCheckedChangeListener { _, isChecked ->
                onSubTaskChecked(item, isChecked)
            }
        }
    }

    override fun getItemCount() = items.size
}
