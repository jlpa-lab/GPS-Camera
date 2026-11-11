package com.gps.map.camera.and.time.stamp.ui.home

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.utils.Routes

class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
