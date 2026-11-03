package com.gps.map.camera.and.time.stamp.ui.earthview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gps.map.camera.and.time.stamp.R

class EarthViewPlaceAdapter(
    private val onPlaceClick: (EarthPlace) -> Unit
) : RecyclerView.Adapter<EarthViewPlaceAdapter.PlaceViewHolder>() {

    private var places: List<EarthPlace> = emptyList()

    fun submitList(newPlaces: List<EarthPlace>) {
        places = newPlaces
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder =
        PlaceViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_earth_view_place, parent, false)
        )

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(places[position])
    }

    override fun getItemCount(): Int = places.size

    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.earth_view_place_name)
        private val address: TextView = itemView.findViewById(R.id.earth_view_place_address)

        fun bind(place: EarthPlace) {
            name.text = place.name
            address.text = place.displayAddress()
            itemView.setOnClickListener { onPlaceClick(place) }
        }
    }

    private fun EarthPlace.displayAddress(): String {
        val parts = address.split(',').map(String::trim).filter(String::isNotEmpty)
        return when {
            parts.size >= 2 -> parts.takeLast(2).joinToString(", ")
            parts.firstOrNull().equals(name, ignoreCase = true) -> "Country"
            else -> address
        }
    }
}
