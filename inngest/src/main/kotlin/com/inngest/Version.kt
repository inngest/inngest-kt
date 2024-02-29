package com.inngest

class Version() {
    companion object {
        private val version: String? = Version::class.java.getPackage().implementationVersion

        fun getVersion(): String = version ?: "version-not-found"
    }
}
