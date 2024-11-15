package org.avmedia.gShockSmartSyncCompose.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Utils {
    companion object {
        fun AppHashCode(): String {
            val callingFunctionName = Thread.currentThread().stackTrace[3].methodName
            return callingFunctionName.hashCode().toString()
        }

        fun <T> runApi(apiCall: suspend (Array<out T>) -> Unit, vararg args: T) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    apiCall(args)  // Pass the arguments as an array
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}