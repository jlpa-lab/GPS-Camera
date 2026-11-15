package com.gps.map.camera.and.time.stamp.ui.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gps.map.camera.and.time.stamp.databinding.ItemGalleryCaptureBinding

class GalleryCaptureAdapter(
    private val onClick: (GalleryCapture) -> Unit
) : ListAdapter<GalleryCapture, GalleryCaptureAdapter.CaptureViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CaptureViewHolder =
        CaptureViewHolder(ItemGalleryCaptureBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CaptureViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CaptureViewHolder(
        private val binding: ItemGalleryCaptureBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(capture: GalleryCapture) {
            Glide.with(binding.captureImage)
                .load(capture.uri)
                .centerCrop()
                .into(binding.captureImage)
            val isVideo = capture.type == GalleryCaptureType.VIDEO
            binding.capturePlay.visibility = if (isVideo) android.view.View.VISIBLE else android.view.View.GONE
            binding.captureDuration.visibility = if (isVideo) android.view.View.VISIBLE else android.view.View.GONE
            if (isVideo) binding.captureDuration.text = formatDuration(capture.durationMs)
            binding.root.setOnClickListener { onClick(capture) }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<GalleryCapture>() {
            override fun areItemsTheSame(oldItem: GalleryCapture, newItem: GalleryCapture) = oldItem.uri == newItem.uri
            override fun areContentsTheSame(oldItem: GalleryCapture, newItem: GalleryCapture) = oldItem == newItem
        }
    }
}
