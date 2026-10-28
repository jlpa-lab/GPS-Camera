package com.gps.map.camera.and.time.stamp.ui.onboarding.adapter

import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.gps.map.camera.and.time.stamp.ui.onboarding.model.OnboardingItem

class OnboardingAdapter(
    manager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(manager, lifecycle) {

    private val items = mutableListOf<OnboardingItem>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitData(items: List<OnboardingItem>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun createFragment(position: Int): Fragment {
        return OnboardingPageFragment.newInstance(items[position])
    }
}







