package com.gps.map.camera.and.time.stamp.ui.tools

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.utils.Routes

class ToolsFragment : Fragment(R.layout.fragment_tools) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.tool_area_measurement).setOnClickListener {
            Routes.startAreaMeasurementActivity(requireActivity())
        }
        view.findViewById<View>(R.id.tool_route_plan).setOnClickListener {
            Routes.startRoutePlanActivity(requireActivity())
        }
    }
}
