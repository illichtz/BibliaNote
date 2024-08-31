package com.a101apps.biblianote

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Handle night mode for navigation and status bar colors
        handleNightMode()

        viewPager = findViewById(R.id.viewPager)
        val pagerAdapter = ScreenSlidePagerAdapter(this)
        viewPager.adapter = pagerAdapter

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.viewPager)
                    ?.childFragmentManager?.fragments?.firstOrNull()

                val navHostFragment = (currentFragment as? Fragment)
                    ?.childFragmentManager
                    ?.primaryNavigationFragment as? NavHostFragment

                if (navHostFragment?.navController?.navigateUp() != true) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)
    }

    private inner class ScreenSlidePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> BookFragment() // This fragment contains NavHostFragment for book-related navigation
                1 -> NoteFragment() // This fragment contains NavHostFragment for note-related navigation
                else -> throw IllegalStateException("Unexpected position $position")
            }
        }

        override fun getItemCount(): Int = 2
    }

    // Function to handle night mode
    private fun handleNightMode() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val darkColor = ContextCompat.getColor(this, R.color.dark)
        val lightColor = ContextCompat.getColor(this, R.color.light)
        window.navigationBarColor = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) darkColor else lightColor
        window.statusBarColor = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) darkColor else lightColor

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val visibilityFlags = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else {
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            window.decorView.systemUiVisibility = visibilityFlags
        }
    }

}
