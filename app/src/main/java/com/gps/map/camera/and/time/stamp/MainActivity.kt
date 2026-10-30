package com.gps.map.camera.and.time.stamp

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.gps.map.camera.and.time.stamp.ui.home.HomeFragment
import com.gps.map.camera.and.time.stamp.ui.settings.SettingsFragment
import com.gps.map.camera.and.time.stamp.ui.tools.ToolsFragment
import com.gps.map.camera.and.time.stamp.ui.widget.WidgetFragment

class MainActivity : AppCompatActivity() {

    private lateinit var navItems: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        configureSystemBars()
        bindBottomNavigation()

        if (savedInstanceState == null) {
            selectTab(0, HomeFragment())
        }
    }

    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = ContextCompat.getColor(this, R.color.home_navigation)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val bottomNavigation = findViewById<View>(R.id.bottom_navigation_bar)
        val originalPaddingBottom = bottomNavigation.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation) { view, insets ->
            val navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                originalPaddingBottom + navigationBarInset
            )
            insets
        }
        ViewCompat.requestApplyInsets(bottomNavigation)
    }

    private fun bindBottomNavigation() {
        navItems = listOf(
            findViewById(R.id.nav_home),
            findViewById(R.id.nav_tools),
            findViewById(R.id.nav_widget),
            findViewById(R.id.nav_settings)
        )

        navItems.forEachIndexed { index, item ->
            item.setOnClickListener {
                when (index) {
                    0 -> selectTab(index, HomeFragment())
                    1 -> selectTab(index, ToolsFragment())
                    2 -> selectTab(index, WidgetFragment())
                    3 -> selectTab(index, SettingsFragment())
                }
            }
        }
    }

    private fun selectTab(index: Int, fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, fragment)
            .commit()

        navItems.forEachIndexed { itemIndex, item ->
            item.isSelected = itemIndex == index
        }
    }
}
