package org.avmedia.translateapi

import java.util.Locale

data class ResourceLocaleKey(val resourceId: Int, val locale: Locale) {
    override fun hashCode(): Int {
        val asString = "$resourceId.${locale.language.lowercase()}"
        val asHash = asString.hashCode()
        return asHash
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResourceLocaleKey) return false
        return resourceId == other.resourceId &&
                locale.language.equals(other.locale.language, ignoreCase = true)
    }
}

