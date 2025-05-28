import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import javax.inject.Inject

class BluetoothHelper @Inject constructor(
    private val context: Context,
    private val activity: Activity,
    private val requestBluetooth: ActivityResultLauncher<Intent>,
    private val onBluetoothEnabled: () -> Unit,
    private val onBluetoothNotEnabled: () -> Unit
) {
    private var onResultCallback: (() -> Unit)? = null

    fun turnOnBLE(onResult: (() -> Unit)? = null) {
        onResultCallback = onResult

        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null) {
            AppSnackbar(
                context.getString(
                    R.string.sorry_your_device_does_not_support_bluetooth_exiting
                )
            )
            activity.finish()
            return
        }

        if (bluetoothAdapter.isEnabled.not()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                AppSnackbar(
                    context.getString(
                        R.string.permission_required_to_turn_on_bluetooth
                    )
                )
                onBluetoothNotEnabled()
                return
            }

            runCatching {
                requestBluetooth.launch(enableBtIntent)
            }.onFailure { e ->
                if (e is SecurityException) {
                    AppSnackbar(
                        context.getString(
                            R.string.you_have_no_permissions_to_turn_on_bluetooth_please_turn_it_on_manually
                        )
                    )
                    onBluetoothNotEnabled()
                }
            }
        } else {
            onBluetoothEnabled()
            onResult?.invoke()
        }
    }

    fun handleBluetoothResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            // Bluetooth successfully enabled
            onBluetoothEnabled()
            onResultCallback?.invoke()
        } else {
            // User did not enable Bluetooth
            onBluetoothNotEnabled()
        }
    }
}
