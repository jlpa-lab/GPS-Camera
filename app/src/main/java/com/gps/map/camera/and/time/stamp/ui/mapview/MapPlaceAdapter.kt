package com.gps.map.camera.and.time.stamp.ui.mapview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ui.earthview.EarthPlace

class MapPlaceAdapter(
    private val onPlaceClick: (EarthPlace) -> Unit
) : RecyclerView.Adapter<MapPlaceAdapter.PlaceViewHolder>() {

    private var places: List<EarthPlace> = emptyList()

    fun submitList(newPlaces: List<EarthPlace>) {
        places = newPlaces
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder =
        PlaceViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_map_place, parent, false)
        )

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.bind(places[position])
    }

    override fun getItemCount(): Int = places.size

    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.map_place_name)

        fun bind(place: EarthPlace) {
            name.text = place.name
            itemView.setOnClickListener {
                onPlaceClick(place)
            }
        }
    }
}
