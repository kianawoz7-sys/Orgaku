package com.tokoku.orgaku

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tokoku.orgaku.data.model.Notification
import com.tokoku.orgaku.databinding.ItemNotificationBinding

class NotificationAdapter(
    private val notifications: List<Notification>,
    private val onItemClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = notifications[position]
        holder.binding.apply {

            // ── Judul notifikasi ──
            tvTitle.text = item.title

            // ── Body: gabungkan senderName + senderOrg jika tersedia,
            //    fallback ke message asli dari Firestore ──
            tvMessage.text = when {
                item.senderName.isNotEmpty() && item.senderOrg.isNotEmpty() ->
                    "${item.message}\n\uD83D\uDC64 ${item.senderName} · ${item.senderOrg}"
                item.senderName.isNotEmpty() ->
                    "${item.message}\n\uD83D\uDC64 ${item.senderName}"
                else ->
                    item.message
            }

            // ── Timestamp relatif ("3 menit lalu", dll) ──
            tvTime.text = DateUtils.getRelativeTimeSpanString(
                item.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )

            // ── Icon berdasarkan tipe notifikasi ──
            val iconRes = when (item.type) {
                "task"    -> R.drawable.ic_layout
                "meeting" -> R.drawable.ic_calendar
                "absensi" -> R.drawable.ic_shield
                "comment" -> R.drawable.ic_message
                else      -> R.drawable.ic_notification
            }
            ivIcon.setImageResource(iconRes)

            // ── Click listener ──
            root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun getItemCount(): Int = notifications.size
}
