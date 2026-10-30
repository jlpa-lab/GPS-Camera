package com.gps.map.camera.and.time.stamp.ui.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gps.map.camera.and.time.stamp.databinding.ItemGalleryPhotoBinding

class PhotoGalleryAdapter(
    private val onPhotoClick: (PhotoGalleryPhoto) -> Unit
) : ListAdapter<PhotoGalleryPhoto, PhotoGalleryAdapter.PhotoViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder =
        PhotoViewHolder(
            ItemGalleryPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PhotoViewHolder(
        private val binding: ItemGalleryPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: PhotoGalleryPhoto) {
            Glide.with(binding.photoImage)
                .load(photo.uri)
                .centerCrop()
                .into(binding.photoImage)
            binding.root.setOnClickListener { onPhotoClick(photo) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PhotoGalleryPhoto>() {
            override fun areItemsTheSame(
                oldItem: PhotoGalleryPhoto,
                newItem: PhotoGalleryPhoto
            ): Boolean = oldItem.uri == newItem.uri

            override fun areContentsTheSame(
                oldItem: PhotoGalleryPhoto,
                newItem: PhotoGalleryPhoto
            ): Boolean = oldItem == newItem
        }
    }
}
