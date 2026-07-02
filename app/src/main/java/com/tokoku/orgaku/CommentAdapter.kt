package com.tokoku.orgaku

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tokoku.orgaku.data.model.Comment
import com.tokoku.orgaku.databinding.ItemCommentBinding

class CommentAdapter(
    private val items: List<Comment>,
    private val currentUserId: String,
    private val onItemLongClick: (Comment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvUserNameComment.text = item.userName
            tvCommentBody.text = item.body
            
            if (item.userAvatar.startsWith("http")) {
                Glide.with(root.context)
                    .load(item.userAvatar)
                    .placeholder(R.drawable.avatar_1)
                    .circleCrop()
                    .into(ivUserComment)
            } else {
                val resId = root.context.resources.getIdentifier(
                    item.userAvatar, 
                    "drawable", 
                    root.context.packageName
                )
                if (resId != 0) {
                    ivUserComment.setImageResource(resId)
                } else {
                    ivUserComment.setImageResource(R.drawable.avatar_1)
                }
            }
        }

        holder.itemView.setOnLongClickListener {
            if (item.userId == currentUserId) {
                onItemLongClick(item)
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount() = items.size
}
