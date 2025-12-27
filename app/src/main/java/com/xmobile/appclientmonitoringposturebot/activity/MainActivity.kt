package com.xmobile.appclientmonitoringposturebot.activity

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.xmobile.appclientmonitoringposturebot.adapter.Viewpager2Adapter
import com.xmobile.appclientmonitoringposturebot.databinding.ActivityMainBinding
import com.xmobile.appclientmonitoringposturebot.model.UserDevice

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var device: UserDevice? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initControl()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun initControl() {
        device = intent.getSerializableExtra("device", UserDevice::class.java)

        setUpViewPager()
        setUpBottomNavigation()

        if (intent.hasExtra("navigate_to")) {
            selectPage(3)
        }
    }

    private fun setUpBottomNavigation() {
        binding.imgHome.setOnClickListener { selectPage(0) }
        binding.imgDashboard.setOnClickListener { selectPage(1) }
        binding.imgHistory.setOnClickListener { selectPage(2) }
        binding.imgNotification.setOnClickListener { selectPage(3) }
        selectPage(0)
    }

    private fun selectPage(position: Int) {
        binding.viewpager2.currentItem = position
    }

    private fun setUpViewPager() {
        val adapter = Viewpager2Adapter(this, device)
        binding.viewpager2.adapter = adapter
        binding.viewpager2.offscreenPageLimit = adapter.itemCount
        binding.viewpager2.isUserInputEnabled = false

        binding.viewpager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateBottomNav(position)
            }
        })
    }

    private fun updateBottomNav(selectedIndex: Int) {
        val items = listOf(
            binding.imgHome,
            binding.imgDashboard,
            binding.imgHistory,
            binding.imgNotification
        )

        items.forEachIndexed { index, imageView ->
            val isSelected = index == selectedIndex
            imageView.isSelected = isSelected

            imageView.animate()
                .scaleX(if (isSelected) 1.1f else 1f)
                .scaleY(if (isSelected) 1.1f else 1f)
                .setDuration(150)
                .start()
        }
    }
}