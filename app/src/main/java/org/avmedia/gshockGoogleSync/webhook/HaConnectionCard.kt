package org.avmedia.gshockGoogleSync.webhook

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppButton

/** Card 1: where Home Assistant is and how to get in. The typed [url] and [token] live in the screen. */
@Composable
internal fun HaConnectionCard(
    saved: Boolean,
    url: String,
    token: String,
    onUrl: (String) -> Unit,
    onToken: (String) -> Unit,
    check: HaCheckUi,
    find: HaFindUi,
    onFind: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val base = HaClient.normalizeBase(url)
    // One home found: fill it in. Several: the owner picks below.
    LaunchedEffect(find) {
        val done = (find as? HaFindUi.Done)?.found ?: (find as? HaFindUi.Searching)?.found
        done?.singleOrNull()?.let { onUrl(it.url) }
    }
    HaCard("Connection", R.drawable.ic_home_assistant) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrl,
            label = { Text("Address, for example https://ha.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = token,
                onValueChange = onToken,
                label = { Text("Access token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pasteFromClipboard(context)?.let(onToken) }) { Text("Paste") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("Find on my network", onFind, enabled = find !is HaFindUi.Searching, modifier = Modifier.weight(1f))
            AppButton(
                "Create a token",
                { openInBrowser(context, "$base/profile/security") },
                enabled = base != null,
                modifier = Modifier.weight(1f),
            )
        }
        FindResult(find, onUrl)
        AppButton("Save", onSave, enabled = url.isNotBlank() && token.isNotBlank() && check != HaCheckUi.Checking, modifier = Modifier.fillMaxWidth())
        CheckResult(saved, check)
    }
}

@Composable
private fun FindResult(find: HaFindUi, onUrl: (String) -> Unit) {
    when (find) {
        HaFindUi.Idle -> {}
        HaFindUi.Unavailable -> HaNote("This phone cannot search the network. Type the address.", error = true)
        is HaFindUi.Searching -> {
            HaLoading("Looking for Home Assistant...")
            FoundList(find.found, onUrl)
        }
        is HaFindUi.Done ->
            if (find.found.isEmpty()) HaNote("No Home Assistant found. Is this phone on the same Wi-Fi? You can type the address.", error = true)
            else FoundList(find.found, onUrl)
    }
}

@Composable
private fun FoundList(found: List<HaFound>, onUrl: (String) -> Unit) {
    if (found.size < 2) return
    found.forEach { TextButton(onClick = { onUrl(it.url) }) { Text("${it.name}  ${it.url}") } }
}

@Composable
private fun CheckResult(saved: Boolean, check: HaCheckUi) {
    when (check) {
        HaCheckUi.Checking -> HaLoading("Checking...")
        is HaCheckUi.Ok -> HaNote(check.message)
        is HaCheckUi.Error -> {
            HaNote(check.message, error = true)
            HaDetails(check.detail)
        }
        HaCheckUi.Idle ->
            HaNote(if (saved) "Saved. Each watch registers itself when it connects." else "Not set up yet. Enter the address and a token, then Save.")
    }
}

private fun pasteFromClipboard(context: Context): String? =
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
        ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
