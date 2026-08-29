package com.fotoyu.compressor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fotoyu.compressor.databinding.FragmentHistoryBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private var adapter: HistoryAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        setupRecyclerView()
        setupTabs()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.rvHistory.layoutManager = LinearLayoutManager(context)
        adapter = HistoryAdapter(emptyList())
        binding.rvHistory.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabHistory.removeAllTabs()
        binding.tabHistory.addTab(binding.tabHistory.newTab().setText("Semua"))
        binding.tabHistory.addTab(binding.tabHistory.newTab().setText("Berhasil"))
        binding.tabHistory.addTab(binding.tabHistory.newTab().setText("Gagal"))

        binding.tabHistory.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { filterHistory() }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.history.collectLatest { filterHistory() }
        }
    }

    fun loadHistory() {
        viewModel.loadHistory()
    }

    private fun filterHistory() {
        if (_binding == null || !isAdded) return

        val allItems = viewModel.history.value
        val tabIndex = binding.tabHistory.selectedTabPosition
        
        val filtered = allItems.filter { item ->
            when (tabIndex) {
                1 -> item.isSuccess
                2 -> !item.isSuccess
                else -> true
            }
        }
        adapter?.updateItems(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
