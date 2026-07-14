package org.avmedia.gshockGoogleSync.ui.actions

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.others.RunFindPhoneScreen

class FindPhoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show activity on top of lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        setContent {
            GShockSmartSyncTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RunFindPhoneScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // If the activity is destroyed (e.g. by system or user), stop the ringing if it's still going
        PhoneFinder.stopRing(this, "FindPhoneActivity destroyed")
    }
}
