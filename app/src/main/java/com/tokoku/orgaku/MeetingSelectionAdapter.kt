package com.tokoku.orgaku

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tokoku.orgaku.databinding.ItemMeetingSelectionBinding

class MeetingSelectionAdapter(
    private var meetings: List<MeetingSchedule>,
    private val onItemClick: (MeetingSchedule) -> Unit
) : RecyclerView.Adapter<MeetingSelectionAdapter.ViewHolder>() {

    fun updateData(newMeetings: List<MeetingSchedule>) {
        this.meetings = newMeetings
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemMeetingSelectionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMeetingSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val meeting = meetings[position]
        holder.binding.apply {
            tvMeetingTitle.text = meeting.title
            tvMeetingDate.text = meeting.date
            root.setOnClickListener { onItemClick(meeting) }
        }
    }

    override fun getItemCount(): Int = meetings.size
}
