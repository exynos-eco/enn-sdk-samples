// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.segmentation.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.samsung.segmentation.R
import com.samsung.segmentation.databinding.FragmentSelectBinding


class SelectFragment : Fragment() {
    private lateinit var binding: FragmentSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments.let {}
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentSelectBinding.inflate(layoutInflater)

        return binding.root
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        binding.cameraButton.setOnClickListener {
            if (cameraPermissionGranted()) {
                view.findNavController().navigate(R.id.action_selectFragment_to_cameraFragment)
            } else {
                Toast.makeText(
                    requireContext(), "Camera permission denied.", Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.imageButton.setOnClickListener {
            view.findNavController().navigate(R.id.action_selectFragment_to_imageFragment)
        }
    }

    private fun cameraPermissionGranted() = ContextCompat.checkSelfPermission(
        requireContext(), android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
}