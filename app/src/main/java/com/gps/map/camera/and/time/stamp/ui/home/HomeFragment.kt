package com.gps.map.camera.and.time.stamp.ui.home

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.utils.Routes

class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configureFeatureCard(
            view.findViewById(R.id.card_earth_view),
            R.string.home_earth_view,
            R.drawable.ic_globe,
            R.drawable.bg_home_icon_tile_blue,
            R.drawable.bg_home_earth_gradient,
            "#9DBBFF"
        )
        configureFeatureCard(
            view.findViewById(R.id.card_street_view),
            R.string.home_street_view,
            R.drawable.ic_street,
            R.drawable.bg_home_icon_tile_purple,
            R.drawable.bg_home_street_gradient,
            "#F3A6D8"
        )
        configureFeatureCard(
            view.findViewById(R.id.card_live_video),
            R.string.home_live_video,
            R.drawable.ic_video,
            R.drawable.bg_home_icon_tile_red,
            R.drawable.bg_home_live_gradient,
            "#F4A7C8"
        )
        configureFeatureCard(
            view.findViewById(R.id.card_map_view),
            R.string.home_map_view,
            R.drawable.ic_map,
            R.drawable.bg_home_icon_tile_teal,
            R.drawable.bg_home_map_gradient,
            "#78D8CB"
        )

        view.findViewById<View>(R.id.card_earth_view).setOnClickListener {
            Routes.startEarthViewActivity(requireActivity())
        }

        view.findViewById<View>(R.id.card_street_view).setOnClickListener {
            Routes.startStreetViewActivity(requireActivity())
        }

        view.findViewById<View>(R.id.card_map_view).setOnClickListener {
            Routes.startMapViewActivity(requireActivity())
        }
        view.findViewById<View>(R.id.card_gps_camera).setOnClickListener {
            Routes.startCameraActivity(requireActivity())
        }
        view.findViewById<View>(R.id.card_live_video).setOnClickListener {
            Routes.startLiveVideoActivity(requireActivity())
        }
    }

    private fun configureFeatureCard(
        root: View,
        titleRes: Int,
        iconRes: Int,
        tileRes: Int,
        backgroundRes: Int,
        arrowColor: String
    ) {
        val card = root.findViewById<View>(R.id.home_feature_card) ?: root
        card.setBackgroundResource(backgroundRes)
        card.findViewById<ImageView>(R.id.home_feature_icon).apply {
            setImageResource(iconRes)
            setBackgroundResource(tileRes)
        }
        card.findViewById<ImageView>(R.id.home_feature_arrow).imageTintList =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(arrowColor))
        card.findViewById<android.widget.TextView>(R.id.home_feature_title).setText(titleRes)
    }

    private fun setComingSoon(view: View, cardId: Int, labelId: Int) {
        view.findViewById<View>(cardId).setOnClickListener {
            Toast.makeText(
                requireContext(),
                "${getString(labelId)} is coming soon",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
