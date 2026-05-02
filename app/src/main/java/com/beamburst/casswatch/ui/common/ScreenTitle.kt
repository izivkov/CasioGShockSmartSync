package com.beamburst.casswatch.ui.common

import com.beamburst.casswatch.ui.common.AppText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.beamburst.casswatch.theme.Spacing

@Composable
fun ScreenTitle(
    text: String,
    modifier: Modifier
) {
    val defaultModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.xl, vertical = Spacing.md)

    AppText(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = defaultModifier.then(modifier),
        textAlign = TextAlign.Start
    )
}
