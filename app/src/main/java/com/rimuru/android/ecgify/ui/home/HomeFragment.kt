package com.rimuru.android.ecgify.ui.home

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.rimuru.android.ecgify.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fabMain.setOnClickListener {
            val bottomSheet = SourceBottomSheet().apply {
                setOnImageSelectedListener { uri ->
                    navigateToManualSelection(uri)
                }
            }
            bottomSheet.show(parentFragmentManager, "SourceBottomSheet")
        }
    }

    private fun navigateToManualSelection(uri: Uri) {
        val action = HomeFragmentDirections
            .actionHomeFragmentToManualSelectionFragment(uri.toString())
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}