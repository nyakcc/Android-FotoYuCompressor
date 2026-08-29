package com.fotoyu.compressor

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
        binding.viewPager.offscreenPageLimit = 2 // Keep all 3 tabs in memory
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> binding.viewPager.setCurrentItem(0, false)
                R.id.navigation_history -> {
                    viewModel.loadHistory() // Refresh history data
                    binding.viewPager.setCurrentItem(1, false)
                }
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
        // Remove the fragment to stop observation
        val fragment = supportFragmentManager.findFragmentById(R.id.process_container)
        if (fragment != null) {
            supportFragmentManager.beginTransaction().remove(fragment).commit()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.processContainer.visibility == View.VISIBLE) {
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
