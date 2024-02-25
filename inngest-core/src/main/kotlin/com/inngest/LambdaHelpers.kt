package com.inngest

import java.util.function.BiFunction

internal fun <A, B, C> BiFunction<A, B, C>.toKotlin(): (A, B) -> C = { a, b -> this.apply(a, b) }
