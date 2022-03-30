/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:18 p.m.
 */

package org.avmedia.gShockPhoneSync

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.android.synthetic.main.activity_ble_operations.characteristics_recycler_view
import kotlinx.android.synthetic.main.activity_ble_operations.log_scroll_view
import kotlinx.android.synthetic.main.activity_ble_operations.log_text_view
import kotlinx.android.synthetic.main.activity_ble_operations.mtu_field
import kotlinx.android.synthetic.main.activity_ble_operations.request_mtu_button
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.ble.DeviceCharacteristics
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.jetbrains.anko.alert
import org.jetbrains.anko.noButton
import org.jetbrains.anko.selector
import org.jetbrains.anko.yesButton
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BleTestActivity : AppCompatActivity() {

    private lateinit var device: BluetoothDevice
    private val dateFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)

    private val characteristicAdapter: CharacteristicAdapter by lazy {
        CharacteristicAdapter(DeviceCharacteristics.characteristics) { characteristic ->
            showCharacteristicOptions(characteristic)
        }
    }
    private var notifyingCharacteristics = mutableListOf<UUID>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")

        setContentView(R.layout.activity_ble_operations)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            title = getString(R.string.app_name)
        }
        setupRecyclerView()
        request_mtu_button.setOnClickListener {
            if (mtu_field.text.isNotEmpty() && mtu_field.text.isNotBlank()) {
                mtu_field.text.toString().toIntOrNull()?.let { mtu ->
                    log("Requesting for MTU value of $mtu")
                    Connection.requestMtu(device, mtu)
                } ?: log("Invalid MTU value: ${mtu_field.text}")
            } else {
                log("Please specify a numeric value for desired ATT MTU (23-517)")
            }
            hideKeyboard()
        }
        createAppEventsSubscription()
    }

    override fun onResume() {
        super.onResume()
        Connection.connect(device, this)
    }

    override fun onPause() {
        super.onPause()
        Connection.teardownConnection(device)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        characteristics_recycler_view.apply {
            adapter = characteristicAdapter
            layoutManager = LinearLayoutManager(
                this@BleTestActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        val animator = characteristics_recycler_view.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    @SuppressLint("SetTextI18n")
    private fun log(message: String) {
        val formattedMessage = String.format("%s: %s", dateFormatter.format(Date()), message)
        runOnUiThread {
            val currentLogText = log_text_view.text.ifEmpty {
                "Beginning of log."
            }
            log_text_view.text = "$currentLogText\n$formattedMessage"
            log_scroll_view.post { log_scroll_view.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun showCharacteristicOptions(characteristic: BluetoothGattCharacteristic) {
        DeviceCharacteristics.characteristicProperties[characteristic]?.let { properties ->
            selector("Select an action to perform", properties.map { it.action }) { _, i ->
                when (properties[i]) {
                    CharacteristicProperty.Readable -> {
                        log("Reading from ${characteristic.uuid}")
                        Connection.readCharacteristic(device, characteristic)
                    }
                    CharacteristicProperty.Writable, CharacteristicProperty.WritableWithoutResponse -> {
                        showWritePayloadDialog(characteristic)
                    }
                    CharacteristicProperty.Notifiable, CharacteristicProperty.Indicatable -> {
                        if (notifyingCharacteristics.contains(characteristic.uuid)) {
                            log("Disabling notifications on ${characteristic.uuid}")
                            Connection.disableNotifications(device, characteristic)
                        } else {
                            log("Enabling notifications on ${characteristic.uuid}")
                            Connection.enableNotifications(device, characteristic)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showWritePayloadDialog(characteristic: BluetoothGattCharacteristic) {
        val hexField = layoutInflater.inflate(R.layout.edittext_hex_payload, null) as EditText
        alert {
            customView = hexField
            isCancelable = false
            yesButton {
                with(hexField.text.toString()) {
                    if (isNotBlank() && isNotEmpty()) {
                        val bytes = hexToBytes()
                        Connection.writeCharacteristic(
                            device,
                            characteristic,
                            bytes
                        )
                    } else {
                        log("Please enter a hex payload to write to ${characteristic.uuid}")
                    }
                }
            }
            noButton {}
        }.show()
        hexField.showKeyboard()
    }

    private fun createAppEventsSubscription() {
        ProgressEvents.subscriber.start(
            this.javaClass.simpleName,

            {
                when (it) {
                    ProgressEvents.Events.Disconnect -> {
                        runOnUiThread {
                            alert {
                                title = "Disconnected"
                                message = "Disconnected from device."
                                positiveButton("OK") { finish() }
                            }.show()
                        }
                    }
                    ProgressEvents.Events.CharacteristicRead -> {
                        Timber.i("onCharacteristicRead")
                    }
                    ProgressEvents.Events.CharacteristicWrite -> {
                        Timber.i("onCharacteristicWrite")
                    }
                    ProgressEvents.Events.MtuChanged -> {
                        Timber.i("onMtuChanged")
                    }
                    ProgressEvents.Events.CharacteristicChanged -> {
                        Timber.i("onCharacteristicChanged")
                    }
                    ProgressEvents.Events.NotificationsEnabled -> {
                        val characteristic: BluetoothGattCharacteristic =
                            ProgressEvents.Events.NotificationsEnabled.payload as BluetoothGattCharacteristic
                        Timber.i("Enabling ${characteristic.uuid}")
                        notifyingCharacteristics.add(characteristic.uuid)
                    }

                    ProgressEvents.Events.NotificationsDisabled -> {
                        val characteristic: BluetoothGattCharacteristic =
                            ProgressEvents.Events.NotificationsEnabled.payload as BluetoothGattCharacteristic
                        Timber.i("Disabling ${characteristic.uuid}")
                        notifyingCharacteristics.remove(characteristic.uuid)
                    }
                }
            },
            { throwable -> Timber.d("Got error on subscribe: $throwable") })
    }

    enum class CharacteristicProperty {
        Readable,
        Writable,
        WritableWithoutResponse,
        Notifiable,
        Indicatable;

        val action
            get() = when (this) {
                Readable -> "Read"
                Writable -> "Write"
                WritableWithoutResponse -> "Write Without Response"
                Notifiable -> "Toggle Notifications"
                Indicatable -> "Toggle Indications"
            }
    }

    private fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun EditText.showKeyboard() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        requestFocus()
        inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun String.hexToBytes() =
        this.chunked(2).map { it.uppercase(Locale.US).toInt(16).toByte() }.toByteArray()
}
