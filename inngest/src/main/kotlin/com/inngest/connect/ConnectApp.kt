package com.inngest.connect

import com.inngest.Inngest
import com.inngest.InngestFunction

/**
 * One app served over a connect worker: an Inngest client plus the functions
 * it exposes. A single worker connection can serve multiple apps, but all of
 * their clients must be configured for the same environment.
 */
class ConnectApp(
    val client: Inngest,
    val functions: List<InngestFunction>,
)
