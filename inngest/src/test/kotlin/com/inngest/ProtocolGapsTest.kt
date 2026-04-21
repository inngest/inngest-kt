package com.inngest

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class ProtocolGapsTest {
    @Test
    @Disabled("In-band sync is not implemented yet; sync is still performed as a separate POST /fn/register handshake.")
    fun `in-band sync remains intentionally unsupported`() {
    }

    @Test
    @Disabled("waitForEvent supports the if-expression form only; the match convenience API is still pending.")
    fun `waitForEvent match convenience remains intentionally unsupported`() {
    }

    @Test
    @Disabled("The SDK does not yet implement StepPlanned and StepNotFound opcodes for planned-step execution.")
    fun `planned step opcodes remain intentionally unsupported`() {
    }
}
