package com.fotoyu.compressor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.fotoyu.compressor.databinding.FragmentHomeBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    private val sourceLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { 
            (activity as? MainActivity)?.onFolderSelected(it)
            viewModel.setSourceFolder(it) 
        }
    }

    private val outputLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { 
            (activity as? MainActivity)?.onFolderSelected(it)
            viewModel.setOutputFolder(it) 
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        setupListeners()
        observeViewModel()
        refreshUI()
    }

    private fun setupListeners() {
        binding.btnPickSource.setOnClickListener { sourceLauncher.launch(null) }
        binding.btnPickOutput.setOnClickListener { outputLauncher.launch(null) }
        binding.btnStart.setOnClickListener { viewModel.startProcessing() }
        
        val toSettings = View.OnClickListener {
            (activity as? MainActivity)?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.navigation_settings
        }
        binding.cardSettings.setOnClickListener(toSettings)
        binding.btnChangeSettings.setOnClickListener(toSettings)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.photos.collectLatest { refreshUI() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sourceUri.collectLatest { refreshUI() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.outputUri.collectLatest { refreshUI() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.maxWidth.collectLatest { refreshUI() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.splitCount.collectLatest { refreshUI() }
        }
    }

    fun refreshUI() {
        val b = _binding ?: return
        if (!isAdded) return

        val count = viewModel.photos.value.size
        val sizeBytes = viewModel.totalSize.value
        
        b.txtCount.text = String.format(Locale.US, "%,d", count).replace(',', '.')
        
        if (count == 0) {
            b.txtSize.text = "--"
            b.txtResultSize.text = "--"
            b.txtSaving.text = "--"
        } else {
            b.txtSize.text = formatFileSize(sizeBytes)
            val ratio = viewModel.maxWidth.value.toDouble() / 4000.0 
            val estFactor = (ratio * ratio).coerceIn(0.05, 0.5)
            val estResultSize = (sizeBytes * estFactor).toLong()
            b.txtResultSize.text = formatFileSize(estResultSize)
            val saving = ((1.0 - (estResultSize.toDouble() / sizeBytes)) * 100).toInt().coerceIn(10, 95)
            b.txtSaving.text = "$saving%"
        }

        b.sourcePathText.text = viewModel.sourceUri.value?.path?.substringAfterLast(':') ?: "Not selected"
        b.outputPathText.text = viewModel.outputUri.value?.path?.substringAfterLast(':') ?: "Not selected"
        
        b.txtValWidth.text = "${viewModel.maxWidth.value} px"
        b.txtValSplit.text = "Setiap ${String.format(Locale.US, "%,d", viewModel.splitCount.value).replace(',', '.')} foto"

        b.btnStart.isEnabled = viewModel.sourceUri.value != null && viewModel.outputUri.value != null && count > 0
    }

    private fun formatFileSize(bytes: Long): String {
        val mb = bytes / (1024 * 1024.0)
        return if (mb > 1024) String.format(Locale.US, "%.1f GB", mb/1024.0) else String.format(Locale.US, "%.1f MB", mb)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
