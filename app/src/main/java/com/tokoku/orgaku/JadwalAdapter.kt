package com.tokoku.orgaku

import android.content.Intent
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tokoku.orgaku.databinding.ItemJadwalBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class JadwalAdapter(
    private var listJadwal: List<MeetingSchedule>,
    private var userRole: String,
    private val onDeleteClick: (MeetingSchedule) -> Unit
) : RecyclerView.Adapter<JadwalAdapter.ViewHolder>() {

    fun updateData(newList: List<MeetingSchedule>, role: String? = null) {
        this.listJadwal = newList
        if (role != null) this.userRole = role
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemJadwalBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemJadwalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = listJadwal[position]
        with(holder.binding) {
            tvMeetingTitle.text = item.title
            tvTime.text = item.time
            tvLocation.text = item.location

            // 1. Defensively Parse Date for Premium Badge
            if (item.date.isNotEmpty()) {
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val date = inputFormat.parse(item.date)
                    if (date != null) {
                        val cal = Calendar.getInstance()
                        cal.time = date

                        val dayFormat = SimpleDateFormat("EEE,", Locale.forLanguageTag("id"))
                        val monthYearFormat =
                            SimpleDateFormat("MMM yyyy", Locale.forLanguageTag("id"))

                        tvMeetingDayName.text = dayFormat.format(date)
                        tvMeetingDateNumber.text = String.format(
                            Locale.getDefault(),
                            "%02d",
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                        tvMeetingMonthYear.text = monthYearFormat.format(date)
                    }
                } catch (e: Exception) {
                    tvMeetingDayName.text = "---"
                    tvMeetingDateNumber.text = "00"
                    tvMeetingMonthYear.text = "N/A"
                }
            }

            // 2. Role-based Delete Visibility
            val normalizedRole = userRole.lowercase().trim()
            if (normalizedRole == "ketua" || normalizedRole == "chairman" || normalizedRole == "admin") {
                btnDeleteJadwal.visibility = View.VISIBLE
            } else {
                btnDeleteJadwal.visibility = View.GONE
            }

            btnDeleteJadwal.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onDeleteClick(item)
            }

            root.setOnClickListener {
                val intent = Intent(it.context, DetailMeetingActivity::class.java).apply {
                    putExtra("EXTRA_MEETING_ID", item.id)
                    putExtra("EXTRA_TITLE", item.title)
                    putExtra("EXTRA_DATE", item.date)
                    putExtra("EXTRA_DESC", item.description)
                    putExtra("EXTRA_LOCATION", item.location)
                    putExtra("EXTRA_TIME", item.time)
                    putExtra("EXTRA_START_TIME", item.startTime)
                    putExtra("EXTRA_END_TIME", item.endTime)
                }
                it.context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = listJadwal.size
}
