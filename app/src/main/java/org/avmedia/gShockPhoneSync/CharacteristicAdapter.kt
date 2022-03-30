/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-14, 1:48 p.m.
 */

package org.avmedia.gShockPhoneSync

import android.bluetooth.BluetoothGattCharacteristic
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.row_characteristic.view.characteristic_properties
import kotlinx.android.synthetic.main.row_characteristic.view.characteristic_uuid
import org.avmedia.gShockPhoneSync.ble.printProperties
import org.jetbrains.anko.layoutInflater

class CharacteristicAdapter(
    private val items: List<BluetoothGattCharacteristic>,
    private val onClickListener: ((characteristic: BluetoothGattCharacteristic) -> Unit)
) : RecyclerView.Adapter<CharacteristicAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = parent.context.layoutInflater.inflate(
            R.layout.row_characteristic,
            parent,
            false
        )
        return ViewHolder(view, onClickListener)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    class ViewHolder(
        private val view: View,
        private val onClickListener: ((characteristic: BluetoothGattCharacteristic) -> Unit)
    ) : RecyclerView.ViewHolder(view) {

        fun bind(characteristic: BluetoothGattCharacteristic) {
            view.characteristic_uuid.text = characteristic.uuid.toString()
            view.characteristic_properties.text = characteristic.printProperties()
            view.setOnClickListener { onClickListener.invoke(characteristic) }
        }
    }
}
