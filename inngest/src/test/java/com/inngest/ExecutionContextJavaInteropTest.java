package com.inngest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExecutionContextJavaInteropTest {
    @Test
    void preservesFourArgumentConstructor() {
        ExecutionContext ctx = new ExecutionContext(2, "fn-id", "run-id", "test");

        assertEquals(2, ctx.getAttempt());
        assertEquals("fn-id", ctx.getFnId());
        assertEquals("run-id", ctx.getRunId());
        assertEquals("test", ctx.getEnv());
        assertFalse(ctx.getDisableImmediateExecution());
        assertFalse(ctx.getUseApi());
        assertEquals(new ExecutionStack(), ctx.getStack());
        assertNull(ctx.getQueueItemId());
    }
}
