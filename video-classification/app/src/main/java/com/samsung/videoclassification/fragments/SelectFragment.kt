// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.videoclassification.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.samsung.videoclassification.R
import com.samsung.videoclassification.databinding.FragmentSelectBinding


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
        // Disable Camera and Image features (TSN_ResNet50 is Video-only)
        binding.cameraButton.visibility = View.GONE
        binding.cameraButton.alpha = 0.3f

        binding.imageButton.visibility = View.GONE
        binding.imageButton.alpha = 0.3f

        binding.videoButton.setOnClickListener {
            view.findNavController().navigate(R.id.action_selectFragment_to_videoFragment)
        }

    }

    private fun cameraPermissionGranted() = ContextCompat.checkSelfPermission(
        requireContext(), android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
}