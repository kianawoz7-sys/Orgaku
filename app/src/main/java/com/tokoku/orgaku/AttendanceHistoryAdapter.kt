package com.tokoku.orgaku

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tokoku.orgaku.databinding.ItemRiwayatAbsensiBinding

class AttendanceHistoryAdapter(private val items: List<AttendanceHistory>) :
    RecyclerView.Adapter<AttendanceHistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRiwayatAbsensiBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRiwayatAbsensiBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvTitle.text = item.title
            tvDate.text = item.date
            badgeStatus.text = item.status

            if (item.status == "Hadir") {
                badgeStatus.setBackgroundResource(R.drawable.bg_badge_green_alpha)
                badgeStatus.setTextColor(ContextCompat.getColor(root.context, R.color.status_green_text))
                cardIcon.setCardBackgroundColor(ContextCompat.getColor(root.context, R.color.status_green_bg))
                ivIcon.setColorFilter(ContextCompat.getColor(root.context, R.color.status_green_text))
                
                tvScanTime.visibility = android.view.View.VISIBLE
                tvScanTime.text = "Hadir pada ${item.scanTime ?: "-"}"
            } else {
                badgeStatus.setBackgroundResource(R.drawable.bg_badge_priority_high)
                badgeStatus.setTextColor(ContextCompat.getColor(root.context, R.color.priority_high_text))
                cardIcon.setCardBackgroundColor(ContextCompat.getColor(root.context, R.color.priority_high_bg))
                ivIcon.setColorFilter(ContextCompat.getColor(root.context, R.color.priority_high_text))
                
                tvScanTime.visibility = android.view.View.GONE
            }
        }
    }

    override fun getItemCount() = items.size
}
