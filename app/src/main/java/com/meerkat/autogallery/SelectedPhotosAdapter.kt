// SelectedPhotosAdapter.kt
package com.meerkat.autogallery

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide  // <- This import was missing!

class SelectedPhotosAdapter(
    private var photoUris: MutableList<String>,
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<SelectedPhotosAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.photoImageView)
        val removeButton: ImageView = itemView.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val uri = Uri.parse(photoUris[position])

        Glide.with(holder.itemView.context)
            .load(uri)
            .centerCrop()
            .placeholder(R.drawable.ic_photo_library) // Add placeholder
            .error(R.drawable.ic_photo_library) // Add error handling
            .into(holder.imageView)

        holder.removeButton.setOnClickListener {
            onRemoveClick(position)
        }
    }

    override fun getItemCount(): Int = photoUris.size

    fun updatePhotos(newPhotos: MutableList<String>) {
        photoUris = newPhotos
        notifyDataSetChanged()
    }
}