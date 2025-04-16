package org.avmedia.gshockGoogleSync.ui.health

import AppTextLarge
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                PermissionsRationaleContent()
            }
        }
    }
}

@Composable
private fun PermissionsRationaleContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        AppTextLarge(
            text = stringResource(R.string.app_name),
        )
        Spacer(modifier = Modifier.height(16.dp))
        AppTextLarge(
            text = stringResource(R.string.health_connect_permissions_required),
        )
    }
}
