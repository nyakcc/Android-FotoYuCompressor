package com.fotoyu.compressor

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.fotoyu.compressor.databinding.ActivityMainBinding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    
    lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupViewPager()
        setupBottomNav()
        observeProcessing()
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> binding.viewPager.setCurrentItem(0, false)
                R.id.navigation_history -> binding.viewPager.setCurrentItem(1, false)
                R.id.navigation_settings -> binding.viewPager.setCurrentItem(2, false)
            }
            true
        }
    }

    private fun observeProcessing() {
        lifecycleScope.launch {
            viewModel.isProcessing.collectLatest { processing ->
                if (processing) {
                    showProcessOverlay()
                } else {
                    // We keep it visible if status is "Finished" or "Cancelled" until manual exit
                    // Actually, the ProcessFragment handles its own exit button
                }
            }
        }
    }

    private fun showProcessOverlay() {
        binding.processContainer.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.process_container, ProcessFragment())
            .commit()
    }

    fun hideProcessOverlay() {
        binding.processContainer.visibility = View.GONE
        // Also ensure ViewModel state is reset if needed
        if (viewModel.isProcessing.value) {
            viewModel.stopProcessing()
        }
    }

    override fun onBackPressed() {
        if (binding.processContainer.visibility == View.VISIBLE) {
            // Check if processing is active before allowing exit
            if (!viewModel.isProcessing.value) {
                hideProcessOverlay()
            }
            return
        }
        super.onBackPressed()
    }

    class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> HomeFragment()
                1 -> HistoryFragment()
                else -> SettingsFragment()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
