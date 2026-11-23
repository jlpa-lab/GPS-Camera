package com.gps.map.camera.and.time.stamp.ui.widget

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.utils.Routes

class WidgetFragment : Fragment(R.layout.fragment_widget) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.widget_live_gps_card).setOnClickListener {
            Routes.startMapViewActivity(requireActivity())
        }
        view.findViewById<View>(R.id.widget_world_card).setOnClickListener {
            Routes.startEarthViewActivity(requireActivity())
        }
        view.findViewById<View>(R.id.widget_trending_card).setOnClickListener {
            Routes.startEarthViewActivity(requireActivity())
        }
    }

}
