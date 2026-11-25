package com.gps.map.camera.and.time.stamp.ui.tools

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.gps.map.camera.and.time.stamp.R
import com.gps.map.camera.and.time.stamp.ads.AdsManager
import com.gps.map.camera.and.time.stamp.utils.Routes

class ToolsFragment : Fragment(R.layout.fragment_tools) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AdsManager.loadInterHome(requireActivity(), ignoreLimit = true)
        AdsManager.loadInterBack(requireActivity(), ignoreLimit = true)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AdsManager.showInterHomeBack(requireActivity(), onAction = { _ ->
                    requireActivity().finish()
                })
            }
        })
        view.findViewById<View>(R.id.tool_area_measurement).setOnClickListener {
            Routes.startAreaMeasurementActivity(requireActivity())
        }
        view.findViewById<View>(R.id.tool_route_plan).setOnClickListener {
            Routes.startRoutePlanActivity(requireActivity())
        }
        view.findViewById<View>(R.id.tool_location).setOnClickListener {
            Routes.startLocationActivity(requireActivity())
        }
        view.findViewById<View>(R.id.tool_camera).setOnClickListener {
            Routes.startToolsCameraActivity(requireActivity())
        }
        view.findViewById<View>(R.id.tool_video).setOnClickListener {
            Routes.startGpsVideoActivity(requireActivity())
        }
        view.findViewById<View>(R.id.tool_photo_grid).setOnClickListener {
            Routes.startPhotoGridActivity(requireActivity())
        }
        view.findViewById<View>(R.id.tool_gallery).setOnClickListener {
            Routes.startPhotoGalleryActivity(requireActivity())
        }
        view.findViewById<View>(R.id.tool_template).setOnClickListener {
            Routes.startStampTemplateActivity(requireActivity())
        }
    }
}
