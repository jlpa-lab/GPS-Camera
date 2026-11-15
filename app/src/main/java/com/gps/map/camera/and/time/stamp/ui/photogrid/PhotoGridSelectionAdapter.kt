package com.gps.map.camera.and.time.stamp.ui.photogrid

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.databinding.ItemPhotoGridPhotoBinding
import com.gps.map.camera.and.time.stamp.ui.gallery.PhotoGalleryPhoto

class PhotoGridSelectionAdapter(
    private val onPhotoClick: (PhotoGalleryPhoto) -> Unit
) : ListAdapter<PhotoGalleryPhoto, PhotoGridSelectionAdapter.PhotoViewHolder>(DIFF_CALLBACK) {
    private var selectedUris = emptySet<android.net.Uri>()

    fun setSelectedUris(uris: List<android.net.Uri>) {
        selectedUris = uris.toSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder =
        PhotoViewHolder(
            ItemPhotoGridPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PhotoViewHolder(
        private val binding: ItemPhotoGridPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: PhotoGalleryPhoto) {
            Glide.with(binding.photoImage)
                .load(photo.uri)
                .centerCrop()
                .into(binding.photoImage)
            val selected = photo.uri in selectedUris
            binding.root.background = GradientDrawable().apply {
                cornerRadius = 16f * binding.root.resources.displayMetrics.density
                setColor(Color.TRANSPARENT)
            }
            binding.photoSelected.visibility = if (selected) android.view.View.VISIBLE else android.view.View.GONE
            binding.root.setOnClickListener { onPhotoClick(photo) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PhotoGalleryPhoto>() {
            override fun areItemsTheSame(oldItem: PhotoGalleryPhoto, newItem: PhotoGalleryPhoto) =
                oldItem.uri == newItem.uri

            override fun areContentsTheSame(oldItem: PhotoGalleryPhoto, newItem: PhotoGalleryPhoto) =
                oldItem == newItem
        }
    }
}
