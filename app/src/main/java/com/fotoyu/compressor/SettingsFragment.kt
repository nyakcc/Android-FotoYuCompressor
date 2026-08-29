package com.fotoyu.compressor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.fotoyu.compressor.databinding.FragmentSettingsBinding
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        setupInputs()
        observeViewModel()
    }

    private fun setupInputs() {
        binding.editMaxWidth.doAfterTextChanged { s ->
            if (binding.editMaxWidth.hasFocus()) {
                val num = s.toString().toIntOrNull()
                if (num != null && num >= 480) {
                    viewModel.updateMaxWidth(num)
                }
            }
        }

        binding.editSplitCount.doAfterTextChanged { s ->
            if (binding.editSplitCount.hasFocus()) {
                val num = s.toString().toIntOrNull()
                if (num != null && num >= 1) {
                    viewModel.updateSplitCount(num)
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.maxWidth.collectLatest { valInt ->
                if (!binding.editMaxWidth.hasFocus()) {
                    val s = valInt.toString()
                    if (binding.editMaxWidth.text.toString() != s) {
                        binding.editMaxWidth.setText(s)
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.splitCount.collectLatest { valInt ->
                if (!binding.editSplitCount.hasFocus()) {
                    val s = valInt.toString()
                    if (binding.editSplitCount.text.toString() != s) {
                        binding.editSplitCount.setText(s)
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
