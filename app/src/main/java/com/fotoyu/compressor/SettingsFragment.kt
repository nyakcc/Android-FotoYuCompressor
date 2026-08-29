package com.fotoyu.compressor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.fotoyu.compressor.databinding.FragmentSettingsBinding
import androidx.core.widget.doAfterTextChanged

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.getRoot()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        refreshUI()
        setupInputs()
    }

    private fun setupInputs() {
        binding.editMaxWidth.doAfterTextChanged { s ->
            val num = s.toString().toIntOrNull()
            if (num != null && num >= 480) {
                viewModel.updateMaxWidth(num)
            }
        }

        binding.editSplitCount.doAfterTextChanged { s ->
            val num = s.toString().toIntOrNull()
            if (num != null && num >= 1) {
                viewModel.updateSplitCount(num)
            }
        }
    }

    fun refreshUI() {
        val b = _binding ?: return
        if (!isAdded) return
        if (!b.editMaxWidth.hasFocus()) {
            b.editMaxWidth.setText(viewModel.maxWidth.value.toString())
        }
        if (!b.editSplitCount.hasFocus()) {
            b.editSplitCount.setText(viewModel.splitCount.value.toString())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
