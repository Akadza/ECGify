package com.rimuru.android.ecgify.ui.home

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.rimuru.android.ecgify.databinding.FragmentResultsBinding
import com.rimuru.android.ecgify.ui.home.adapters.EcgGraphAdapter
import com.rimuru.android.ecgify.ui.home.viewmodels.ResultsViewModel

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    // Просто используем ViewModel без Hilt
    private val viewModel: ResultsViewModel by lazy {
        ViewModelProvider(this).get(ResultsViewModel::class.java)
    }

    private val args by navArgs<ResultsFragmentArgs>()
    private lateinit var graphAdapter: EcgGraphAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupObservers()

        // Запускаем оцифровку
        args.imageUri?.let { uriString ->
            val uri = Uri.parse(uriString)
            viewModel.digitizeImage(requireContext(), uri)
        }
    }

    private fun setupUI() {
        graphAdapter = EcgGraphAdapter { lead ->
            // Обработка клика (пока пусто)
        }

        binding.recyclerGraphs.apply {
            layoutManager = LinearLayoutManager(requireContext())  // Последовательный список
            adapter = graphAdapter
        }

        binding.btnSaveData.setOnClickListener {
            viewModel.saveEcgData(requireContext())
        }

        binding.btnExportImage.setOnClickListener {
            viewModel.saveTraceImage(requireContext())
        }

        binding.btnShare.setOnClickListener {
            viewModel.shareResults(requireContext())
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupObservers() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ResultsViewModel.ResultsUiState.Loading -> {
                    showLoading(true)
                    binding.tvStatus.text = "Обработка изображения..."
                }
                is ResultsViewModel.ResultsUiState.Success -> {
                    showLoading(false)
                    binding.tvStatus.text = "Оцифровка завершена"

                    graphAdapter.submitList(state.result.ecgData.leads.entries.map { (lead, data) ->
                        EcgGraphAdapter.EcgGraphItem(lead, data)
                    })

                    // Удалена строка с ivTrace — изображения трассировки больше нет в layout

                    binding.tvStats.text = """
                        Отведений: ${state.result.ecgData.leads.size}
                        Длительность: ${state.result.ecgData.duration} сек
                        Частота: ${state.result.ecgData.samplingRate} Гц
                    """.trimIndent()
                }
                is ResultsViewModel.ResultsUiState.Error -> {
                    showLoading(false)
                    binding.tvStatus.text = "Ошибка: ${state.message}"
                }
            }
        }

        viewModel.saveProgress.observe(viewLifecycleOwner) { progress ->
            binding.progressSave.progress = progress
            binding.progressSave.visibility = if (progress in 1..99) View.VISIBLE else View.GONE
            if (progress == 100) {
                binding.tvStatus.text = "Данные сохранены"
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.contentLayout.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}