package com.tokoku.orgaku

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tokoku.orgaku.databinding.ItemOnboardingSlideBinding

data class OnboardingSlide(
    val title: String,
    val subtitle: String,
    val imageRes: Int
)

class OnboardingAdapter(
    private val slides: List<OnboardingSlide>
) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemOnboardingSlideBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOnboardingSlideBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val slide = slides[position]
        holder.binding.txtTitle.text = slide.title
        holder.binding.txtSubtitle.text = slide.subtitle
        holder.binding.imgOnboarding.setImageResource(slide.imageRes)
    }

    override fun getItemCount() = slides.size
}