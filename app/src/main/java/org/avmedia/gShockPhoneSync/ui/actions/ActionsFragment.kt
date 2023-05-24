/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-14, 1:48 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.actions

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import org.avmedia.gShockPhoneSync.databinding.FragmentActionsBinding
import org.avmedia.gshockapi.ProgressEvents


class ActionsFragment : Fragment() {

    private var _binding: FragmentActionsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    init {
    }

    @SuppressLint("UseRequireInsteadOfGet")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val requestMultiplePermissions = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                ProgressEvents.onNext("ActionsPermissionsGranted")
            } else {
                ProgressEvents.onNext("ActionsPermissionsNotGranted")
            }
        }

        var requiredPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE,
        )

        // WRITE_EXTERNAL_STORAGE doesn't provide any additional access since Android 11
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requiredPermissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

        requestMultiplePermissions.launch(requiredPermissions)

        _binding = FragmentActionsBinding.inflate(inflater, container, false)
        _binding?.actionList?.init()
        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.actionList?.shutdown()
        _binding = null
    }
}