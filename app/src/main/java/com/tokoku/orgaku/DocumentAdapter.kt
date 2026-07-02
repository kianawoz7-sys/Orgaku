package com.tokoku.orgaku

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.recyclerview.widget.RecyclerView
import com.tokoku.orgaku.databinding.ItemDocumentBinding

class DocumentAdapter(private var items: List<Document>) :
    RecyclerView.Adapter<DocumentAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemDocumentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvDocTitle.text = item.title
            tvDocCategory.text = item.category

            // Set icon based on URL (File vs Folder)
            val isFolder = item.driveUrl.contains("drive.google.com/drive/folders")
            ivDocIcon.setImageResource(if (isFolder) R.drawable.ic_folder else R.drawable.ic_document)

            // Modern vibrant icon color based on category (React Style)
            val context = root.context
            when (item.category.lowercase()) {
                "proposal" -> {
                    cardIcon.setCardBackgroundColor(context.getColor(R.color.blue_proposal))
                    ivDocIcon.setColorFilter(context.getColor(R.color.white))
                }
                "lpj" -> {
                    cardIcon.setCardBackgroundColor(context.getColor(R.color.orange_lpj))
                    ivDocIcon.setColorFilter(context.getColor(R.color.white))
                }
                "surat" -> {
                    cardIcon.setCardBackgroundColor(context.getColor(R.color.purple_surat))
                    ivDocIcon.setColorFilter(context.getColor(R.color.white))
                }
                else -> {
                    // Organization/University Logo Style for other categories
                    cardIcon.setCardBackgroundColor(context.getColor(R.color.badge_bg_blue))
                    ivDocIcon.setImageResource(R.drawable.ic_globe)
                    ivDocIcon.setColorFilter(context.getColor(R.color.primary_dark))
                }
            }

            btnOpen.setOnClickListener {
                val url = item.driveUrl
                if (url.isNotEmpty()) {
                    val builder = CustomTabsIntent.Builder()

                    // Set toolbar color to our app's primary color
                    builder.setToolbarColor(context.getColor(R.color.primary_dark))

                    // Show title of the document in the toolbar
                    builder.setShowTitle(true)

                    val customTabsIntent = builder.build()
                    customTabsIntent.launchUrl(context, Uri.parse(url))
                }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<Document>) {
        items = newList
        notifyDataSetChanged()
    }
}