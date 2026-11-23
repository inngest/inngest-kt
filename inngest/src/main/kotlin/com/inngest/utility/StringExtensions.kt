package com.inngest.utility

internal fun String?.nonBlankOrNull(): String? = if (this.isNullOrBlank()) null else this
