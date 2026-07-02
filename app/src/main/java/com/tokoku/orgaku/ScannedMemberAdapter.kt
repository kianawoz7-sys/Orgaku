package com.tokoku.orgaku

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tokoku.orgaku.databinding.ItemAnggotaScanBinding

class ScannedMemberAdapter(
    private var items: List<ScannedMember>,
    private var isReadOnly: Boolean = false,
    private val onManualHadirClick: (ScannedMember) -> Unit = {},
    private val onBatalkanClick: (ScannedMember) -> Unit = {}
) : RecyclerView.Adapter<ScannedMemberAdapter.ViewHolder>() {

    fun updateData(newItems: List<ScannedMember>, readOnly: Boolean? = null) {
        this.items = newItems
        if (readOnly != null) this.isReadOnly = readOnly
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemAnggotaScanBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAnggotaScanBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvName.text = item.name
            
            // Task 1: Bind real NIM and Status
            if (item.isPresent) {
                tvNim.text = "${item.nim} • Sudah Absen"
                badgeStatus.text = item.status
                badgeStatus.setBackgroundResource(R.drawable.bg_badge_green_alpha)
                badgeStatus.setTextColor(root.context.getColor(R.color.status_green_text))
                badgeStatus.visibility = View.VISIBLE
                
                btnManualHadir.visibility = View.GONE
                // Hide Batalkan if isReadOnly
                btnBatalkan.visibility = if (isReadOnly) View.GONE else View.VISIBLE
                btnBatalkan.setOnClickListener { onBatalkanClick(item) }
            } else {
                tvNim.text = "${item.nim} • Belum Absen"
                badgeStatus.text = "BELUM"
                badgeStatus.setBackgroundResource(R.drawable.bg_badge_priority_medium)
                badgeStatus.setTextColor(root.context.getColor(R.color.priority_medium_text))
                badgeStatus.visibility = View.VISIBLE
                
                // Hide Manual Hadir if isReadOnly
                btnManualHadir.visibility = if (isReadOnly) View.GONE else View.VISIBLE
                btnBatalkan.visibility = View.GONE
                btnManualHadir.setOnClickListener { onManualHadirClick(item) }
            }

            // Task 1: Hybrid Avatar Loading
            if (item.avatar.startsWith("http")) {
                Glide.with(root.context).load(item.avatar).circleCrop().into(ivAvatar)
            } else {
                val resId = root.context.resources.getIdentifier(item.avatar, "drawable", root.context.packageName)
                if (resId != 0) {
                    ivAvatar.setImageResource(resId)
                } else {
                    ivAvatar.setImageResource(R.drawable.avatar_1)
                }
            }
        }
    }

    override fun getItemCount() = items.size
}
