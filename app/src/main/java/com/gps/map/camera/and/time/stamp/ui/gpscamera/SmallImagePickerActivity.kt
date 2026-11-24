package com.gps.map.camera.and.time.stamp.ui.gpscamera

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ui.photogrid.PhotoGridMediaAccess
import java.util.concurrent.Executors

class SmallImagePickerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PHOTO_URI = "small_image_picker_photo_uri"
    }

    private val queryExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_small_image_picker)

        findViewById<View>(R.id.small_image_picker_back).setOnClickListener { finish() }
        val grid = findViewById<RecyclerView>(R.id.small_image_picker_grid)
        val adapter = SmallImagePickerAdapter { uri ->
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_PHOTO_URI, uri.toString()))
            finish()
        }
        grid.layoutManager = GridLayoutManager(this, 2)
        grid.adapter = adapter

        queryExecutor.execute {
            val photos = PhotoGridMediaAccess.queryPhotos(this, 60, 0).map { it.uri }
            runOnUiThread { if (!isFinishing && !isDestroyed) adapter.submit(photos) }
        }
    }

    override fun onDestroy() {
        queryExecutor.shutdown()
        super.onDestroy()
    }

    private class SmallImagePickerAdapter(
        private val onPhotoClick: (Uri) -> Unit
    ) : RecyclerView.Adapter<SmallImagePickerAdapter.PhotoViewHolder>() {
        private var photos = emptyList<Uri>()

        fun submit(newPhotos: List<Uri>) {
            photos = newPhotos
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_small_image_picker, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            holder.bind(photos[position])
        }

        override fun getItemCount(): Int = photos.size

        inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val image = itemView.findViewById<ImageView>(R.id.small_image_picker_image)

            fun bind(uri: Uri) {
                Glide.with(image)
                    .load(uri)
                    .centerCrop()
                    .into(image)
                itemView.setOnClickListener { onPhotoClick(uri) }
            }
        }
    }
}
