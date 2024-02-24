package com.inngest

import java.util.Properties

// TODO should this be a singleton?
class Version() {
    private val versionProperties = Properties()

    init {
        versionProperties.load(this.javaClass.getResourceAsStream("/version.properties"))
    }

    fun getVersion(): String = versionProperties.getProperty("version") ?: "missing-version"
}
