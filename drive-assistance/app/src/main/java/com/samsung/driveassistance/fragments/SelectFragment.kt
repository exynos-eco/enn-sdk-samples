package com.samsung.driveassistance.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.samsung.driveassistance.R
import com.samsung.driveassistance.databinding.FragmentSelectBinding

class SelectFragment : Fragment() {
    private lateinit var binding: FragmentSelectBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imageButton.visibility = View.VISIBLE
        binding.cameraButton.visibility = View.VISIBLE

        binding.imageButton.setOnClickListener {
            findNavController().navigate(R.id.action_selectFragment_to_imageFragment)
        }
        binding.cameraButton.setOnClickListener {
            findNavController().navigate(R.id.action_selectFragment_to_cameraFragment)
        }
    }
}