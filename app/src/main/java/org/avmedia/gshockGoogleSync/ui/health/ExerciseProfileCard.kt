package org.avmedia.gshockGoogleSync.ui.health

import AppTextLarge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockapi.model.ExerciseProfile
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ExerciseProfileCard(
    modifier: Modifier = Modifier,
    healthViewModel: HealthViewModel = hiltViewModel()
) {
    val state by healthViewModel.state.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var profileStepGoalText by remember { mutableStateOf("") }
    var profileHeightText by remember { mutableStateOf("") }
    var profileWeightText by remember { mutableStateOf("") }
    var profileError by remember { mutableStateOf<String?>(null) }
    // The field values as last read from the watch; Save is enabled only once they differ.
    var loadedProfileTexts by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val isModified = loadedProfileTexts != null &&
        Triple(profileStepGoalText, profileHeightText, profileWeightText) != loadedProfileTexts
    val scope = rememberCoroutineScope()
    val imperialLocale = remember { isImperialLocale() }

    LaunchedEffect(state.stepGoal, state.height, state.weight) {
        isLoading = true
        val profile = healthViewModel.readExerciseProfile()
        if (profile != null) {
            profileStepGoalText = profile.stepGoal.toString()
            profileHeightText = if (imperialLocale) {
                (profile.height / 2.54f).roundToInt().toString()
            } else {
                profile.height.toString()
            }
            profileWeightText = if (imperialLocale) {
                (profile.weight * 2.20462f).roundToInt().toString()
            } else {
                profile.weight.toString()
            }
        } else {
            profileStepGoalText = state.stepGoal.toString()
            profileHeightText = if (imperialLocale) {
                (state.height / 2.54f).roundToInt().toString()
            } else {
                state.height.toString()
            }
            profileWeightText = if (imperialLocale) {
                (state.weight * 2.20462f).roundToInt().toString()
            } else {
                state.weight.toString()
            }
        }
        loadedProfileTexts = Triple(profileStepGoalText, profileHeightText, profileWeightText)
        isLoading = false
    }

    AppCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextLarge(
                    text = stringResource(id = R.string.exercise_profile),
                    modifier = Modifier.weight(1f)
                )

                if (isLoading || isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Set step goal, height, and weight for your watch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            val labelHeight = if (imperialLocale) "Height (in)" else "Height (cm)"
            val labelWeight = if (imperialLocale) "Weight (lb)" else "Weight (kg)"

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = profileStepGoalText,
                    onValueChange = { profileStepGoalText = it; profileError = null },
                    label = { Text(stringResource(id = R.string.step_goal)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = profileError != null && profileStepGoalText.toIntOrNull() == null,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = profileHeightText,
                        onValueChange = { profileHeightText = it; profileError = null },
                        label = { Text(labelHeight) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = profileError != null && profileHeightText.toIntOrNull() == null,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = profileWeightText,
                        onValueChange = { profileWeightText = it; profileError = null },
                        label = { Text(labelWeight) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = profileError != null && profileWeightText.toIntOrNull() == null,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (profileError != null) {
                    Text(
                        text = profileError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                AppButton(
                    text = stringResource(id = R.string.save),
                    enabled = isModified && !isSaving,
                    onClick = {
                        val parsedStepGoal = profileStepGoalText.toIntOrNull()
                        val parsedHeight = profileHeightText.toIntOrNull()
                        val parsedWeight = profileWeightText.toIntOrNull()

                        when {
                            parsedStepGoal == null ->
                                profileError = "Step goal must be a number."
                            parsedHeight == null ->
                                profileError = "Height must be a number."
                            parsedWeight == null ->
                                profileError = "Weight must be a number."
                            parsedStepGoal !in 1000..30000 ->
                                profileError = "Step goal must be between 1000 and 30000."
                            parsedHeight !in 20..120 ->
                                profileError = if (imperialLocale) "Height must be between 20 and 120 in." else "Height must be between 50 and 250 cm."
                            parsedWeight !in 20..500 ->
                                profileError = if (imperialLocale) "Weight must be between 20 and 500 lb." else "Weight must be between 20 and 300 kg."
                            else -> {
                                val metricHeight = if (imperialLocale) (parsedHeight * 2.54f).roundToInt() else parsedHeight
                                val metricWeight = if (imperialLocale) (parsedWeight / 2.20462f).roundToInt() else parsedWeight

                                isSaving = true
                                scope.launch {
                                    val saved = healthViewModel.saveExerciseProfile(
                                        ExerciseProfile(
                                            stepGoal = parsedStepGoal,
                                            height = metricHeight,
                                            weight = metricWeight
                                        )
                                    )
                                    isSaving = false
                                    if (saved) {
                                        profileError = null
                                        loadedProfileTexts = Triple(profileStepGoalText, profileHeightText, profileWeightText)
                                    } else {
                                        profileError = "Unable to save exercise profile to watch."
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun isImperialLocale(): Boolean {
    val country = Locale.getDefault().country.uppercase(Locale.US)
    return country in setOf("US", "LR", "MM", "CA")
}
