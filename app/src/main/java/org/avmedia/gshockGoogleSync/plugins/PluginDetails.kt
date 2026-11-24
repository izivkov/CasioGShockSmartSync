package org.avmedia.gshockGoogleSync.plugins

import AppText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.avmedia.smartsync.plugin.api.Control
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.PluginAccess
import org.avmedia.smartsync.plugin.api.WatchPlugin

/** One folded section of a plugin's details: a title, one short line, and what a tap on the row shows. */
data class DetailSection(val title: String, val line: String, val more: List<String> = emptyList())

/** What a plugin's details page says, one short line per section (the rest behind a tap). Android-free. */
object PluginDetails {
    const val EVERYTHING = "everything your watch provides"
    const val ALL_CONTROLS = "all watch settings and notifications"

    /** The first lines of [items], or "N more" beyond [shown]; the whole list goes behind the tap when cut. */
    private fun short(items: List<String>, none: String, shown: Int = 2): Pair<String, List<String>> = when {
        items.isEmpty() -> none to emptyList()
        items.size <= shown -> items.joinToString() to emptyList()
        else -> (items.take(shown).joinToString() + " and ${items.size - shown} more") to items
    }

    fun reads(access: PluginAccess): DetailSection {
        val all = access.reads.sortedBy { it.ordinal }.map { AccessGuard.words(it) }
        // Everything the watch gives: one line; each kind (also the phone's) behind the tap.
        if (false) return DetailSection("Reads", EVERYTHING, all)
        val (line, more) = short(all, "nothing")
        return DetailSection("Reads", line, more)
    }

    fun controls(access: PluginAccess): DetailSection {
        val all = access.controls.sortedBy { it.ordinal }.map { AccessGuard.words(it) }
        if (access.controls.containsAll(Control.ALL)) return DetailSection("Controls", ALL_CONTROLS, all)
        val (line, more) = short(all, "nothing")
        return DetailSection("Controls", line, more)
    }

    fun sections(plugin: WatchPlugin, worksWith: List<String>): List<DetailSection> {
        val access = runCatching { plugin.access }.getOrNull() ?: PluginAccess()
        val permissions = PluginPermissions.required(access)
        return listOfNotNull(
            reads(access),
            controls(access),
            short(access.sendsTo, "nowhere", shown = 1).let { (l, m) -> DetailSection("Sends to", l, m) },
            short(access.stores, "nothing", shown = 1).let { (l, m) -> DetailSection("Stores", l, m) },
            worksWith.takeIf { it.isNotEmpty() }?.let { w -> short(w, "", shown = 1).let { (l, m) -> DetailSection("Works with", l, m) } },
            DetailSection(
                "Permissions",
                if (permissions.isEmpty()) "none" else short(permissions.map { it.reason }, "none").first,
                permissions.map { "${it.reason} (${it.name.substringAfterLast('.')})" },
            ),
            about(plugin),
        )
    }

    /** Author, licence, source and issues; null when the plugin says none of them. */
    fun about(plugin: WatchPlugin): DetailSection? {
        val author = runCatching { plugin.author }.getOrDefault("")
        val license = runCatching { plugin.license }.getOrDefault("")
        val links = listOfNotNull(
            runCatching { plugin.sourceUrl }.getOrNull()?.takeIf { it.isNotBlank() }?.let { "Source: $it" },
            runCatching { plugin.issuesUrl }.getOrNull()?.takeIf { it.isNotBlank() }?.let { "Issues: $it" },
        )
        val line = listOf(author.takeIf { it.isNotBlank() }?.let { "by $it" }, license.takeIf { it.isNotBlank() }).filterNotNull().joinToString("  ·  ")
        if (line.isBlank() && links.isEmpty()) return null
        return DetailSection("About this plugin", line.ifBlank { "links" }, links)
    }

    /** The text first on the page: the description, else the summary. */
    fun description(plugin: WatchPlugin): String =
        runCatching { plugin.description }.getOrDefault("").ifBlank { runCatching { plugin.summary }.getOrDefault("") }

    /** "Needs: your Home Assistant URL and a token"; null for none. */
    fun needs(plugin: WatchPlugin): String? = runCatching { plugin.setupNeeds }.getOrDefault("").takeIf { it.isNotBlank() }?.let { "Needs: $it" }

    /** The guide link, only an http(s) one. */
    fun guide(plugin: WatchPlugin): String? = runCatching { plugin.sourceUrl }.getOrNull()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}

/**
 * A row that folds: [title] and [line]; a tap anywhere on the row shows [more] below it and a second tap folds it again.
 * Without [more] the row does not fold. A line of [more] that names a link ("Source: https://...") opens it on a tap.
 */
@Composable
internal fun FoldRow(title: String, line: String, more: List<String>, modifier: Modifier = Modifier, titleSize: Int = 12) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().let { m -> if (more.isEmpty()) m else m.clickable { open = !open } }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(text = title, fontSize = titleSize.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (line.isNotBlank()) AppText(text = line, fontSize = 14.sp)
            }
            if (more.isNotEmpty()) {
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Fold $title" else "Open $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (open) more.forEach { item ->
            val link = item.substringAfter(": ", "").takeIf { it.startsWith("https://") || it.startsWith("http://") }
            AppText(
                text = item, fontSize = 14.sp,
                textDecoration = if (link != null) TextDecoration.Underline else null,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp).let { m -> link?.let { l -> m.clickable { runCatching { uri.openUri(l) } } } ?: m },
            )
        }
    }
}

/** "Details": everything about data and permissions, folded; a tap opens the sections, each folded again. */
@Composable
internal fun PluginDetailSections(sections: List<DetailSection>) {
    var open by rememberSaveable { mutableStateOf(true) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(text = "Details", modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Fold details" else "Open details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) sections.forEach { FoldRow(it.title, it.line, it.more) }
    }
}
