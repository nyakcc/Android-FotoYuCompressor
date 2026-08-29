package com.fotoyu.compressor

import android.content.res.ColorStateList
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
import java.util.Locale

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
        val black = ColorStateList.valueOf(requireContext().getColor(R.color.black))
        val gray = ColorStateList.valueOf(requireContext().getColor(R.color.gray_300))

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
            viewModel.photos.collectLatest { binding.txtTotal.text = it.size.toString() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentProcessedCount.collectLatest { binding.txtDiproses.text = it.toString() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentStep.collectLatest { step ->
                updateChecklist(step, black, gray)
            }
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

    private fun updateChecklist(step: Int, black: ColorStateList, gray: ColorStateList) {
        // Step 1: Scanning
        if (step >= 1) {
            binding.step1.setImageResource(R.drawable.ic_check_circle)
            binding.step1.imageTintList = black
        } else {
            binding.step1.setImageResource(R.drawable.ic_step_pending)
            binding.step1.imageTintList = gray
        }

        // Step 2: Compressing
        if (step == 2) {
            binding.step2Loading.visibility = View.VISIBLE
            binding.step2Done.visibility = View.GONE
        } else if (step > 2) {
            binding.step2Loading.visibility = View.GONE
            binding.step2Done.visibility = View.VISIBLE
            binding.step2Done.setImageResource(R.drawable.ic_check_circle)
            binding.step2Done.imageTintList = black
        } else {
            binding.step2Loading.visibility = View.GONE
            binding.step2Done.visibility = View.VISIBLE
            binding.step2Done.setImageResource(R.drawable.ic_step_pending)
            binding.step2Done.imageTintList = gray
        }

        // Step 3: Saving (merged with Step 2 usually, but let's handle if step 4 is reached)
        if (step >= 4) {
            binding.step3.setImageResource(R.drawable.ic_check_circle)
            binding.step3.imageTintList = black
            binding.step4.setImageResource(R.drawable.ic_check_circle)
            binding.step4.imageTintList = black
        } else {
            binding.step3.setImageResource(R.drawable.ic_step_pending)
            binding.step3.imageTintList = gray
            binding.step4.setImageResource(R.drawable.ic_step_pending)
            binding.step4.imageTintList = gray
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
