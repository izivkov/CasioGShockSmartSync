package org.avmedia.gshockGoogleSync.ui.time

import AppTextLarge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard

@Composable
fun StepCounterView(
    modifier: Modifier = Modifier,
    timeViewModel: TimeViewModel = hiltViewModel()
) {
    val state by timeViewModel.state.collectAsState()

    AppCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(2f)
                    .padding(horizontal = 12.dp)
            ) {
                AppTextLarge(
                    text = stringResource(id = R.string.steps),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AppTextLarge(
                    text = state.stepCount.toString()
                )
            }
        }
    }
}
