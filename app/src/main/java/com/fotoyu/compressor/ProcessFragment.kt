package com.fotoyu.compressor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.fotoyu.compressor.databinding.FragmentProcessBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProcessFragment : Fragment() {
    private var _binding: FragmentProcessBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProcessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        binding.btnCancel.setOnClickListener { viewModel.stopProcessing() }
        binding.btnBack.setOnClickListener { (activity as? MainActivity)?.hideProcessOverlay() }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.progress.collectLatest { 
                binding.progressIndicator.progress = it
                binding.txtPercent.text = "$it%"
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statusText.collectLatest { binding.txtStatus.text = it }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.timeRemaining.collectLatest { binding.txtRemaining.text = it }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isProcessing.collectLatest { processing ->
                binding.btnCancel.text = if (processing) "BATALKAN" else "SELESAI"
                binding.btnCancel.setOnClickListener { 
                    if (processing) {
                        viewModel.stopProcessing()
                    } else {
                        (activity as? MainActivity)?.hideProcessOverlay()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
