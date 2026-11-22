package org.avmedia.gshockGoogleSync.webhook

import AppText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Searchable list of the values the message can use, grouped Watch, Health, Phone, App. A tap inserts at the cursor. */
@Composable
internal fun VariablePicker(variables: List<Variable>, onInsert: (String) -> Unit, startOpen: Boolean = false) {
    var open by remember { mutableStateOf(startOpen) }
    var query by remember { mutableStateOf("") }
    TextButton(onClick = { open = !open }) { Text(if (open) "Hide values" else "Insert a value") }
    if (!open) return
    OutlinedTextField(
        value = query, onValueChange = { query = it }, singleLine = true,
        label = { Text("Search values") }, modifier = Modifier.fillMaxWidth(),
    )
    val groups = VariableGroups.grouped(variables, query)
    if (query.isBlank()) {
        PickerRow("{{all}}", "Everything below, in one go", onInsert)
    }
    if (groups.isEmpty()) HaNote("No value matches.")
    groups.forEach { (group, list) ->
        AppText(text = group.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Column {
            list.forEach { PickerRow("{{${it.name}}}", it.description, onInsert) }
        }
    }
}

@Composable
private fun PickerRow(token: String, description: String, onInsert: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onInsert(token) }.padding(vertical = 4.dp)) {
        AppText(text = token, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        if (description.isNotBlank()) HaNote(description)
    }
}
