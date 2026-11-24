package org.avmedia.gshockGoogleSync.homeassistant

import android.content.Context
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage

/**
 * The stores of Home Assistant and the app switches, on the app's [LocalDataStorage]. Plain object so background
 * workers can use it without dependency injection.
 */
object AppStores {
    /** The time of each watch's last connection, the `app.last_sync` variable (per watch). */
    const val KEY_LAST_SYNC = "app.lastSync"

    internal class LocalKeyValueStore(private val context: Context) : KeyValueStore {
        override fun get(key: String): String? = LocalDataStorage.get(context, key)
        // Wait for each write: the fire-and-forget put/delete can land out of order (see StorageOrderTest).
        override fun put(key: String, value: String) = kotlinx.coroutines.runBlocking { LocalDataStorage.putAndWait(context, key, value) }
        override fun delete(key: String) = kotlinx.coroutines.runBlocking { LocalDataStorage.deleteAsync(context, key) }
        override fun keys(): Set<String> = LocalDataStorage.toJsonObject(context).keys().asSequence().toSet()
    }

    /** Every per-watch value; the values of older versions belong to the registered H5600 watch ([HaStore.legacyOwnerCandidate]). */
    fun watchStore(context: Context): org.avmedia.gshockGoogleSync.watch.WatchStore {
        val kv = LocalKeyValueStore(context.applicationContext)
        return org.avmedia.gshockGoogleSync.watch.WatchStore(kv, pickLegacyOwner = { HaStore(kv).legacyOwnerCandidate() })
    }

    /** The Home Assistant link. */
    fun haStore(context: Context): HaStore = HaStore(LocalKeyValueStore(context.applicationContext))

    fun rawSwitch(context: Context): RawReadSwitch = RawReadSwitch(LocalKeyValueStore(context.applicationContext))

    fun phoneNotifications(context: Context): org.avmedia.gshockGoogleSync.notifications.PhoneNotificationSettings =
        org.avmedia.gshockGoogleSync.notifications.PhoneNotificationSettings(LocalKeyValueStore(context.applicationContext), watchStore(context))

    fun toggles(context: Context): ServiceToggles = ServiceToggles(LocalKeyValueStore(context.applicationContext))
}
