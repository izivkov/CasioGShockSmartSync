/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-14, 1:48 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.actions

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import org.avmedia.gShockPhoneSync.databinding.FragmentActionsBinding
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber


class ActionsFragment : Fragment() {

    private var _binding: FragmentActionsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    init {
        ProgressEvents.addEvent("ActionsPermissionsGranted")
        ProgressEvents.addEvent("ActionsPermissionsNotGranted")
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

        requestMultiplePermissions.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        )

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

    @SuppressLint("RestrictedApi")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        Timber.i("Requesting permissions")
    }
}