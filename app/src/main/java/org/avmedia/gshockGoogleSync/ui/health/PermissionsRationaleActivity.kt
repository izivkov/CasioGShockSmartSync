package org.avmedia.gshockGoogleSync.ui.health

import AppTextLarge
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository

class PermissionsRationaleActivity : ComponentActivity() {

    private val viewModel: HealthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                PermissionsRationaleContent(translateApi = viewModel.translateApi)
            }
        }
    }
}

@Composable
private fun PermissionsRationaleContent(translateApi: TranslateRepository) {
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
            text = translateApi.stringResource(
                context = LocalContext.current,
                id = R.string.health_connect_permissions_required
            ),
        )
    }
}
