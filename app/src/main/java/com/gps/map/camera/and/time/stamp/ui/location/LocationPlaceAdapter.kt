package com.gps.map.camera.and.time.stamp.ui.location

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlace

class LocationPlaceAdapter(
    private val onPlaceClick: (EarthPlace) -> Unit
) : RecyclerView.Adapter<LocationPlaceAdapter.PlaceViewHolder>() {

    private var places: List<EarthPlace> = emptyList()

    fun submitList(newPlaces: List<EarthPlace>) {
        places = newPlaces
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location_search_result, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(places[position])
    }

    override fun getItemCount(): Int = places.size

    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name = itemView.findViewById<TextView>(R.id.location_search_result_name)

        fun bind(place: EarthPlace) {
            name.text = place.name
            itemView.setOnClickListener { onPlaceClick(place) }
        }
    }
}
