/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-23, 9:51 a.m.
 */

package org.avmedia.gShockPhoneSync.ui.time

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import org.avmedia.gShockPhoneSync.databinding.FragmentTimeBinding
import org.avmedia.gshockapi.ProgressEvents

class TimeFragment : Fragment() {

    private var _binding: FragmentTimeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    init {
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentTimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
