package com.permieware.osmapdigger.external

/** Opens an explicit user-selected URL in the platform browser. */
fun interface ExternalLinkOpener {
    fun open(url: String)
}
