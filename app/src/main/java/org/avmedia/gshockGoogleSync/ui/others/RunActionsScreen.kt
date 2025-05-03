package org.avmedia.gshockGoogleSync.ui.others

import AppTextExtraLarge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

object ActionNameHandler {
    private val actionNames = mutableStateListOf<String>()
    var actionNamesText = mutableStateOf("")

    init {
        val format: (String) -> String = { input ->
            input.replace("\\s+".toRegex(), " ").trim()
        }

        val eventActions = arrayOf(
            EventAction("ActionNames") {
                val actionNamesResult = ProgressEvents.getPayload("ActionNames") as ArrayList<*>
                val cleanedNames = actionNamesResult.map { "\u2192 " + format(it as String) }
                actionNames.clear()
                actionNames.addAll(cleanedNames)
                actionNamesText.value = actionNames.joinToString(separator = "\n")
            }
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }
}

@Composable
fun RunActionsScreen(
    translateApi: TranslateRepository
) {
    val scrollState = rememberScrollState()
    var actionNamesText by remember { mutableStateOf("") }

    val derivedActionNamesText by remember {
        derivedStateOf { ActionNameHandler.actionNamesText.value }
    }

    LaunchedEffect(derivedActionNamesText) {
        actionNamesText = derivedActionNamesText
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppTextExtraLarge(
                text = translateApi.stringResource(
                    context = LocalContext.current,
                    id = R.string.running_actions
                ),
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = actionNamesText,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
fun RunFindPhoneScreen(
    translateApi: TranslateRepository
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        val text = remember(context) {
            buildString {
                append(translateApi.stringResource(context, R.string.find_phone))
                append("\n\n\n")
                append(
                    translateApi.stringResource(
                        context,
                        R.string.when_found_lift_phone_to_stop_ringing
                    )
                )
            }
        }

        AppTextExtraLarge(
            text = text,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )
    }
}